package rs.raf.banka2_bek.investmentfund.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.investmentfund.model.ClientFundPosition;
import rs.raf.banka2_bek.investmentfund.model.InvestmentFund;
import rs.raf.banka2_bek.investmentfund.repository.ClientFundPositionRepository;
import rs.raf.banka2_bek.investmentfund.repository.InvestmentFundRepository;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.stock.util.ListingCurrencyResolver;
import rs.raf.banka2_bek.auth.util.UserRole;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/*
================================================================================
 TODO — KALKULATOR IZVEDENIH POLJA FONDA (vrednost, profit, procenat)
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 195-202 i 292-295
--------------------------------------------------------------------------------
 METODE:
  BigDecimal computeFundValue(InvestmentFund fund);
    = fund.account.balance
    + sum(Portfolio.quantity * Listing.price) za sve Portfolio sa
      userRole=FUND, userId=fund.id, konvertovano u RSD

  BigDecimal computeProfit(InvestmentFund fund);
    = computeFundValue(fund) - sum(ClientFundPosition.totalInvested for fund)

  BigDecimal computePositionValue(ClientFundPosition position);
    = fundValue * (position.totalInvested / totalInvested_across_all_positions)

  BigDecimal computePositionPercent(ClientFundPosition position);
    = position.totalInvested / sum(all positions) * 100
    * Napomena: ako je totalInvested_sum = 0, vrati 0 (edge case posle
                 brisanja svih pozicija).

 PERFORMANSE:
 - Ove metode se zovu cesto (svaki refresh Discovery stranice).
 - Optimalno: cache koji se invalidira pri svakoj ClientFundTransaction-i
   ili novoj order fill-u fund portfolija.
 - Za pocetak: racunaj uvek freshly, optimizuj kasnije ako bude sporo.

 KONVERZIJA VALUTA:
 - Fond je u RSD, ali hartije mogu biti u USD/EUR/JPY. Koristi
   CurrencyConversionService.convert(amount, fromCurrency, "RSD") bez komisije.
================================================================================
*/
@Service
@RequiredArgsConstructor
public class FundValueCalculator {

    private final InvestmentFundRepository investmentFundRepository;
    private final PortfolioRepository portfolioRepository;
    private final ListingRepository listingRepository;
    private final AccountRepository accountRepository;
    private final ClientFundPositionRepository clientFundPositionRepository;
    private final CurrencyConversionService currencyConversionService;

    public BigDecimal computeFundValue(InvestmentFund fund) {
        BigDecimal liquidAmount = accountRepository.findById(fund.getAccountId())
                .map(Account::getBalance)
                .orElse(BigDecimal.ZERO);

        List<Portfolio> holdings = portfolioRepository.findByUserIdAndUserRole(fund.getId(), UserRole.FUND);

        BigDecimal holdingsValue = BigDecimal.ZERO;
        for (Portfolio p : holdings) {
            if (p.getQuantity() == null || p.getQuantity() == 0) continue;
            Listing listing = listingRepository.findById(p.getListingId()).orElse(null);
            if (listing == null || listing.getPrice() == null) continue;
            String currency = ListingCurrencyResolver.resolveSafe(listing, "USD");
            BigDecimal priceRsd = currencyConversionService.convert(listing.getPrice(), currency, "RSD");
            holdingsValue = holdingsValue.add(priceRsd.multiply(BigDecimal.valueOf(p.getQuantity())));
        }

        return liquidAmount.add(holdingsValue).setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal computeProfit(InvestmentFund fund) {
        BigDecimal fundValue = computeFundValue(fund);
        BigDecimal totalInvested = sumTotalInvested(fund.getId());
        return fundValue.subtract(totalInvested).setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal computePositionValue(Long fundId, Long userId, String userRole) {
        ClientFundPosition position = clientFundPositionRepository
                .findByFundIdAndUserIdAndUserRole(fundId, userId, userRole)
                .orElse(null);
        if (position == null) return BigDecimal.ZERO;

        BigDecimal totalInvested = sumTotalInvested(fundId);
        if (totalInvested.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        InvestmentFund fund = investmentFundRepository.findById(fundId).orElse(null);
        if (fund == null) return BigDecimal.ZERO;

        BigDecimal fundValue = computeFundValue(fund);
        return fundValue.multiply(position.getTotalInvested())
                .divide(totalInvested, 4, RoundingMode.HALF_UP);
    }

    public BigDecimal computePositionPercent(Long fundId, Long userId, String userRole) {
        ClientFundPosition position = clientFundPositionRepository
                .findByFundIdAndUserIdAndUserRole(fundId, userId, userRole)
                .orElse(null);
        if (position == null) return BigDecimal.ZERO;

        BigDecimal totalInvested = sumTotalInvested(fundId);
        if (totalInvested.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        return position.getTotalInvested()
                .multiply(new BigDecimal("100"))
                .divide(totalInvested, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal sumTotalInvested(Long fundId) {
        return clientFundPositionRepository.findByFundId(fundId).stream()
                .map(ClientFundPosition::getTotalInvested)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
