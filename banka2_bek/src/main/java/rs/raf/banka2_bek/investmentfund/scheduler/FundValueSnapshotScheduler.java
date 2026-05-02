package rs.raf.banka2_bek.investmentfund.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.investmentfund.model.ClientFundPosition;
import rs.raf.banka2_bek.investmentfund.model.FundValueSnapshot;
import rs.raf.banka2_bek.investmentfund.model.InvestmentFund;
import rs.raf.banka2_bek.investmentfund.repository.ClientFundPositionRepository;
import rs.raf.banka2_bek.investmentfund.repository.FundValueSnapshotRepository;
import rs.raf.banka2_bek.investmentfund.repository.InvestmentFundRepository;
import rs.raf.banka2_bek.investmentfund.service.FundValueCalculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/*
================================================================================
 TODO — DNEVNI SNIMAK VREDNOSTI SVIH FONDOVA (23:45)
 Zaduzen: BE tim
 Spec referenca: Celina 4, linija 316 "belezite istorijske podatke"
--------------------------------------------------------------------------------
 FLOW:
  1. Svaki dan u 23:45 (cron "0 45 23 * * *"):
     a. Dohvati sve aktivne fondove.
     b. Za svaki fund, FundValueCalculator.computeFundValue + computeProfit.
     c. Upise FundValueSnapshot za snapshotDate=today.
     d. Ako vec postoji snapshot za taj dan (manual trigger ili retry),
        UPDATE umesto INSERT.

 OPCIJE:
  - Dodati manual trigger endpoint (admin only) za testiranje.
  - Alternativno: cesci snapshot-i (1x po satu) ako treba preciznije.
================================================================================
*/
@Slf4j
@Component
@RequiredArgsConstructor
public class FundValueSnapshotScheduler {

    private final InvestmentFundRepository investmentFundRepository;
    private final FundValueCalculator fundValueCalculator;
    private final FundValueSnapshotRepository fundValueSnapshotRepository;
    private final AccountRepository accountRepository;
    private final ClientFundPositionRepository clientFundPositionRepository;

    @Scheduled(cron = "0 45 23 * * *")
    public void snapshotAllFunds() {
        LocalDate today = LocalDate.now();
        List<InvestmentFund> funds = investmentFundRepository.findByActiveTrueOrderByNameAsc();
        log.info("Fund snapshot: {} active funds for {}", funds.size(), today);

        for (InvestmentFund fund : funds) {
            try {
                if (fundValueSnapshotRepository.existsByFundIdAndSnapshotDate(fund.getId(), today)) {
                    continue;
                }
                BigDecimal fundValue = fundValueCalculator.computeFundValue(fund);
                BigDecimal profit = fundValueCalculator.computeProfit(fund);
                BigDecimal liquidAmount = accountRepository.findById(fund.getAccountId())
                        .map(Account::getBalance)
                        .orElse(BigDecimal.ZERO);
                BigDecimal investedTotal = clientFundPositionRepository.findByFundId(fund.getId()).stream()
                        .map(ClientFundPosition::getTotalInvested)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                FundValueSnapshot snapshot = new FundValueSnapshot();
                snapshot.setFundId(fund.getId());
                snapshot.setSnapshotDate(today);
                snapshot.setFundValue(fundValue);
                snapshot.setLiquidAmount(liquidAmount);
                snapshot.setInvestedTotal(investedTotal);
                snapshot.setProfit(profit);
                fundValueSnapshotRepository.save(snapshot);
            } catch (Exception e) {
                log.error("Failed to snapshot fund #{}: {}", fund.getId(), e.getMessage());
            }
        }
    }
}
