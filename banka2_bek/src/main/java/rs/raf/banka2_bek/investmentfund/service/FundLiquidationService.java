package rs.raf.banka2_bek.investmentfund.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountCategory;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.util.UserRole;
import rs.raf.banka2_bek.investmentfund.model.ClientFundTransaction;
import rs.raf.banka2_bek.investmentfund.model.ClientFundTransactionStatus;
import rs.raf.banka2_bek.investmentfund.repository.ClientFundTransactionRepository;
import rs.raf.banka2_bek.investmentfund.repository.InvestmentFundRepository;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.stock.util.ListingCurrencyResolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FundLiquidationService {

    private static final Logger log = LoggerFactory.getLogger(FundLiquidationService.class);
    private static final String RSD = "RSD";
    private static final BigDecimal FX_FEE_RATE = new BigDecimal("0.01");
    private static final int MONEY_SCALE = 4;

    private final OrderRepository orderRepository;
    private final PortfolioRepository portfolioRepository;
    private final ListingRepository listingRepository;
    private final ClientFundTransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final InvestmentFundRepository investmentFundRepository;
    private final CurrencyConversionService currencyConversionService;

    /**
     * T9 — automatska likvidacija kada T8 withdraw nema dovoljno cash-a.
     *
     * T8 prosledjuje shortfall u RSD. Ovaj servis pravi interne FUND SELL
     * naloge, najveci holding prvo. Po svakom fill-u OrderExecutionService
     * poziva onFillCompleted, koji FIFO razresava PENDING isplate.
     */
    @Transactional
    public void liquidateFor(Long fundId, BigDecimal amountRsd) {
        if (amountRsd == null || amountRsd.signum() <= 0) {
            return;
        }

        sendPushNotification(fundId, "Isplata je pokrenuta. Zbog nedovoljno likvidnih sredstava, "
                + "vrsi se automatska prodaja hartija fonda.");

        List<Portfolio> fundHoldings = new ArrayList<>(portfolioRepository.findByUserIdAndUserRole(fundId, UserRole.FUND));
        fundHoldings.sort(Comparator.comparing(this::calculateValueInRsd).reversed());

        BigDecimal remaining = amountRsd.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        for (Portfolio holding : fundHoldings) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            if (holding.getQuantity() == null || holding.getQuantity() <= 0) continue;

            Listing listing = listingRepository.findByTicker(holding.getListingTicker())
                    .or(() -> holding.getListingId() == null ? java.util.Optional.empty() : listingRepository.findById(holding.getListingId()))
                    .orElseThrow(() -> new RuntimeException("Listing nije pronadjen za holding: " + holding.getListingTicker()));

            BigDecimal bid = listing.getBid() != null ? listing.getBid() : listing.getPrice();
            if (bid == null || bid.signum() <= 0) continue;

            String listingCurrency = ListingCurrencyResolver.resolveSafe(listing, RSD);
            BigDecimal priceInRsd = convertToRsd(bid, listingCurrency);
            BigDecimal bufferPrice = priceInRsd.multiply(new BigDecimal("0.99")).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (bufferPrice.compareTo(BigDecimal.ZERO) <= 0) continue;

            int quantityToSell = remaining.divide(bufferPrice, 0, RoundingMode.CEILING).intValue();
            quantityToSell = Math.min(quantityToSell, holding.getQuantity());

            if (quantityToSell > 0) {
                createInternalFundOrder(fundId, holding, listing, quantityToSell, bid);
                remaining = remaining.subtract(bufferPrice.multiply(BigDecimal.valueOf(quantityToSell)))
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            }
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            sendPushNotification(999L, "ALARM: Fond #" + fundId
                    + " nema dovoljno hartija za likvidaciju. Nedostaje jos " + remaining + " RSD.");
        }
    }

    @Transactional
    public void onFillCompleted(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order nije pronadjen"));

        if (!UserRole.FUND.equals(order.getUserRole())) return;

        Long fundId = order.getFundId() != null ? order.getFundId() : order.getUserId();
        log.info("Hook: FUND order #{} fillovan. Pokusaj FIFO resolve-a PENDING isplata za fond #{}.", orderId, fundId);

        List<ClientFundTransaction> fundPendingWithdrawals = transactionRepository
                .findByStatus(ClientFundTransactionStatus.PENDING)
                .stream()
                .filter(tx -> Objects.equals(tx.getFundId(), fundId))
                .filter(tx -> !tx.isInflow())
                .sorted(Comparator.comparing(ClientFundTransaction::getCreatedAt))
                .collect(Collectors.toList());

        Account fundAccount = getFundAccount(fundId);

        for (ClientFundTransaction tx : fundPendingWithdrawals) {
            if (nullToZero(fundAccount.getAvailableBalance()).compareTo(tx.getAmountRsd()) >= 0) {
                executeTransactionPayout(tx, fundAccount);
                log.info("Pending fund transaction #{} COMPLETED.", tx.getId());
            } else {
                break;
            }
        }
    }

    private void createInternalFundOrder(Long fundId, Portfolio holding, Listing listing, int quantity, BigDecimal bid) {
        Account fundAccount = getFundAccount(fundId);

        Order fundOrder = new Order();
        fundOrder.setUserId(fundId);
        fundOrder.setUserRole(UserRole.FUND);
        fundOrder.setFundId(fundId);
        fundOrder.setListing(listing);
        fundOrder.setQuantity(quantity);
        fundOrder.setRemainingPortions(quantity);
        fundOrder.setContractSize(1);
        fundOrder.setPricePerUnit(bid);
        fundOrder.setDirection(OrderDirection.SELL);
        fundOrder.setOrderType(OrderType.MARKET);
        fundOrder.setStatus(OrderStatus.APPROVED);
        fundOrder.setApprovedBy("SYSTEM_FUND_LIQUIDATION");
        fundOrder.setApprovedAt(LocalDateTime.now());
        fundOrder.setCreatedAt(LocalDateTime.now());
        fundOrder.setLastModification(LocalDateTime.now());
        fundOrder.setDone(false);
        fundOrder.setAfterHours(false);
        fundOrder.setAllOrNone(false);
        fundOrder.setMargin(false);
        fundOrder.setReservedAccountId(fundAccount.getId());
        fundOrder.setAccountId(fundAccount.getId());
        fundOrder.setExchangeRate(resolveListingToRsdRate(listing));

        orderRepository.save(fundOrder);
    }

    private void executeTransactionPayout(ClientFundTransaction tx, Account fundAccount) {
        Account destinationAccount = accountRepository.findForUpdateById(tx.getSourceAccountId())
                .orElseThrow(() -> new RuntimeException("Racun za isplatu nije pronadjen"));

        BigDecimal amountRsd = tx.getAmountRsd().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        debit(fundAccount, amountRsd);

        String destinationCurrency = destinationAccount.getCurrency().getCode();
        BigDecimal grossCredit = RSD.equals(destinationCurrency)
                ? amountRsd
                : convertFromRsd(amountRsd, destinationCurrency);
        boolean isPersonalClientAccount = destinationAccount.getClient() != null && UserRole.CLIENT.equals(tx.getUserRole());
        BigDecimal fxFee = (!RSD.equals(destinationCurrency) && isPersonalClientAccount)
                ? grossCredit.multiply(FX_FEE_RATE).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal netCredit = grossCredit.subtract(fxFee).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        credit(destinationAccount, netCredit);
        creditBankFxCommission(destinationCurrency, fxFee, destinationAccount.getId());

        tx.setStatus(ClientFundTransactionStatus.COMPLETED);
        tx.setCompletedAt(LocalDateTime.now());
        tx.setFailureReason(null);
        sendPushNotification(tx.getUserId(), "Isplata iz fonda u iznosu od " + tx.getAmountRsd()
                + " RSD je uspesno procesuirana.");
        transactionRepository.save(tx);
    }

    private Account getFundAccount(Long fundId) {
        if (investmentFundRepository != null) {
            return investmentFundRepository.findById(fundId)
                    .flatMap(fund -> accountRepository.findForUpdateById(fund.getAccountId()))
                    .orElseThrow(() -> new RuntimeException("Racun fonda nije pronadjen za fundId=" + fundId));
        }

        // Fallback zadrzan zbog starijih Mockito testova koji ne mock-uju
        // InvestmentFundRepository. Runtime putanja u aplikaciji koristi granu iznad.
        return accountRepository.findFirstByAccountCategoryAndCurrency_Code(AccountCategory.BANK_TRADING, RSD)
                .orElseThrow(() -> new RuntimeException("Glavni RSD racun banke nije pronadjen"));
    }

    private BigDecimal calculateValueInRsd(Portfolio p) {
        if (p == null || p.getQuantity() == null || p.getQuantity() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal unitPrice = p.getAverageBuyPrice();
        String currency = RSD;
        if (p.getListingId() != null) {
            Listing listing = listingRepository.findById(p.getListingId()).orElse(null);
            if (listing != null) {
                unitPrice = listing.getBid() != null ? listing.getBid() : listing.getPrice();
                currency = ListingCurrencyResolver.resolveSafe(listing, RSD);
            }
        }
        if (unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return convertToRsd(unitPrice, currency).multiply(BigDecimal.valueOf(p.getQuantity()))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveListingToRsdRate(Listing listing) {
        String listingCurrency = ListingCurrencyResolver.resolveSafe(listing, RSD);
        if (RSD.equals(listingCurrency)) {
            return BigDecimal.ONE;
        }
        return currencyConversionService.getRate(listingCurrency, RSD);
    }

    private BigDecimal convertToRsd(BigDecimal amount, String fromCurrency) {
        if (amount == null) return BigDecimal.ZERO;
        if (RSD.equals(fromCurrency)) return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return currencyConversionService.convert(amount, fromCurrency, RSD);
    }

    private BigDecimal convertFromRsd(BigDecimal amount, String toCurrency) {
        if (RSD.equals(toCurrency)) return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return currencyConversionService.convert(amount, RSD, toCurrency);
    }

    private void debit(Account account, BigDecimal amount) {
        BigDecimal scaled = amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        account.setBalance(nullToZero(account.getBalance()).subtract(scaled));
        account.setAvailableBalance(nullToZero(account.getAvailableBalance()).subtract(scaled));
        accountRepository.save(account);
    }

    private void credit(Account account, BigDecimal amount) {
        BigDecimal scaled = amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        account.setBalance(nullToZero(account.getBalance()).add(scaled));
        account.setAvailableBalance(nullToZero(account.getAvailableBalance()).add(scaled));
        accountRepository.save(account);
    }

    private void creditBankFxCommission(String currencyCode, BigDecimal commission, Long accountToSkipId) {
        if (commission == null || commission.signum() <= 0) {
            return;
        }
        Account bankAccount = accountRepository.findFirstByAccountCategoryAndCurrency_Code(
                        AccountCategory.BANK_TRADING, currencyCode)
                .orElseThrow(() -> new RuntimeException("Bankin racun za FX proviziju ne postoji u valuti " + currencyCode));
        if (Objects.equals(bankAccount.getId(), accountToSkipId)) {
            return;
        }
        credit(bankAccount, commission);
    }

    private void sendPushNotification(Long userId, String message) {
        log.info("[PUSH NOTIFICATION] Za korisnika #{}: {}", userId, message);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}