package rs.raf.banka2_bek.investmentfund.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.util.UserRole;
import rs.raf.banka2_bek.investmentfund.model.ClientFundPosition;
import rs.raf.banka2_bek.investmentfund.model.ClientFundTransaction;
import rs.raf.banka2_bek.investmentfund.model.ClientFundTransactionStatus;
import rs.raf.banka2_bek.investmentfund.model.InvestmentFund;
import rs.raf.banka2_bek.investmentfund.repository.ClientFundPositionRepository;
import rs.raf.banka2_bek.investmentfund.repository.ClientFundTransactionRepository;
import rs.raf.banka2_bek.investmentfund.repository.InvestmentFundRepository;
import rs.raf.banka2_bek.investmentfund.scheduler.FundValueSnapshotScheduler;
import rs.raf.banka2_bek.order.exception.InsufficientFundsException;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.order.service.FundReservationService;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.stock.util.ListingCurrencyResolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundDividendService {

    private static final String RSD = "RSD";
    private static final int MONEY_SCALE = 4;

    private final InvestmentFundRepository investmentFundRepository;
    private final ClientFundTransactionRepository clientFundTransactionRepository;
    private final ClientFundPositionRepository clientFundPositionRepository;
    private final AccountRepository accountRepository;
    private final ListingRepository listingRepository;
    private final OrderRepository orderRepository;
    private final FundReservationService fundReservationService;
    private final CurrencyConversionService currencyConversionService;
    private final FundValueSnapshotScheduler fundValueSnapshotScheduler;

    @Transactional
    public ClientFundTransaction creditDividendToFund(Long fundId, Long listingId, BigDecimal totalDividendAmount) {
        if (fundId == null) {
            throw new IllegalArgumentException("Fund id je obavezan za B11 dividendni priliv.");
        }
        if (listingId == null) {
            throw new IllegalArgumentException("Listing id je obavezan za B11 dividendni priliv.");
        }
        if (totalDividendAmount == null || totalDividendAmount.signum() <= 0) {
            throw new IllegalArgumentException("Iznos dividende mora biti pozitivan.");
        }

        InvestmentFund fund = getActiveFund(fundId);
        Account fundAccount = getFundAccountForUpdate(fund);
        BigDecimal amount = scale(totalDividendAmount);

        credit(fundAccount, amount);

        ClientFundTransaction tx = new ClientFundTransaction();
        tx.setFundId(fund.getId());
        tx.setUserId(fund.getId());
        tx.setUserRole(UserRole.FUND);
        tx.setAmountRsd(amount);
        tx.setSourceAccountId(fundAccount.getId());
        tx.setInflow(true);
        tx.setStatus(ClientFundTransactionStatus.DIVIDEND_INFLOW);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setFailureReason("DIVIDEND_INFLOW listingId=" + listingId);

        ClientFundTransaction saved = clientFundTransactionRepository.save(tx);

        fundValueSnapshotScheduler.snapshotFundIfMissing(fund);

        log.info(
                "B11 dividend inflow credited: fund={}, listing={}, amountRsd={}",
                fundId,
                listingId,
                amount
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ClientFundTransaction> listPendingDividends(Long fundId) {
        return clientFundTransactionRepository.findByFundIdAndStatusOrderByCreatedAtAsc(
                fundId,
                ClientFundTransactionStatus.DIVIDEND_INFLOW
        );
    }

    @Transactional
    public List<Order> reinvestDividends(Long fundId) {
        InvestmentFund fund = getActiveFund(fundId);
        Account fundAccount = getFundAccountForUpdate(fund);
        List<ClientFundTransaction> pendingDividends = listPendingDividends(fundId);

        if (pendingDividends.isEmpty()) {
            log.info("B11 reinvest skipped: fund={} nema pending dividendi.", fundId);
            return List.of();
        }

        List<Order> createdOrders = new java.util.ArrayList<>();

        for (ClientFundTransaction dividendTx : pendingDividends) {
            Long listingId = extractListingId(dividendTx);

            if (listingId == null) {
                markFailed(dividendTx, "Nije moguce reinvestirati dividendu bez listingId reference.");
                continue;
            }

            Listing listing = listingRepository.findById(listingId)
                    .orElseThrow(() -> new EntityNotFoundException("Listing nije pronadjen: " + listingId));

            BigDecimal dividendAmount = scale(dividendTx.getAmountRsd());
            BigDecimal availableCash = scale(fundAccount.getAvailableBalance());
            BigDecimal amountForReinvestment = dividendAmount.min(availableCash);

            if (amountForReinvestment.signum() <= 0) {
                markFailed(dividendTx, "Fond nema raspoloziv cash za reinvestiranje dividende.");
                continue;
            }

            BigDecimal priceInListingCurrency = resolveBuyPrice(listing);
            BigDecimal priceInRsd = convertToRsd(
                    priceInListingCurrency,
                    ListingCurrencyResolver.resolveSafe(listing, RSD)
            );

            if (priceInRsd.signum() <= 0) {
                markFailed(dividendTx, "Cena listinga nije validna za reinvestiranje.");
                continue;
            }

            int quantity = amountForReinvestment
                    .divide(priceInRsd, 0, RoundingMode.FLOOR)
                    .intValue();

            if (quantity <= 0) {
                log.warn(
                        "B11 reinvest skipped: fund={}, tx={}, amount={} nije dovoljan za jednu hartiju {} po ceni {} RSD.",
                        fundId,
                        dividendTx.getId(),
                        amountForReinvestment,
                        listing.getTicker(),
                        priceInRsd
                );
                continue;
            }

            BigDecimal reservedAmount = priceInRsd
                    .multiply(BigDecimal.valueOf(quantity))
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            if (scale(fundAccount.getAvailableBalance()).compareTo(reservedAmount) < 0) {
                throw new InsufficientFundsException(
                        "Fond nema dovoljno raspolozivih sredstava za reinvestiranje dividende."
                );
            }

            Order order = createInternalBuyOrder(
                    fund,
                    fundAccount,
                    listing,
                    quantity,
                    priceInListingCurrency,
                    reservedAmount
            );

            createdOrders.add(order);

            dividendTx.setStatus(ClientFundTransactionStatus.DIVIDEND_REINVESTED);
            dividendTx.setCompletedAt(LocalDateTime.now());
            dividendTx.setFailureReason(
                    "DIVIDEND_REINVESTED orderId=" + order.getId() + ", listingId=" + listing.getId()
            );
            clientFundTransactionRepository.save(dividendTx);
        }

        fundValueSnapshotScheduler.snapshotFundIfMissing(fund);

        return createdOrders;
    }

    @Transactional
    public List<ClientFundTransaction> distributeDividendsToClients(Long fundId) {
        InvestmentFund fund = getActiveFund(fundId);
        Account fundAccount = getFundAccountForUpdate(fund);
        List<ClientFundTransaction> pendingDividends = listPendingDividends(fundId);

        if (pendingDividends.isEmpty()) {
            log.info("B11 distribution skipped: fund={} nema pending dividendi.", fundId);
            return List.of();
        }

        BigDecimal totalDividend = pendingDividends.stream()
                .map(ClientFundTransaction::getAmountRsd)
                .filter(Objects::nonNull)
                .map(this::scale)
                .reduce(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP), BigDecimal::add);

        if (totalDividend.signum() <= 0) {
            return List.of();
        }

        if (scale(fundAccount.getAvailableBalance()).compareTo(totalDividend) < 0) {
            throw new InsufficientFundsException(
                    "Fond nema dovoljno likvidnih sredstava za raspodelu dividendi."
            );
        }

        List<ClientFundPosition> positions = clientFundPositionRepository.findByFundId(fundId)
                .stream()
                .filter(position -> position.getTotalInvested() != null)
                .filter(position -> position.getTotalInvested().signum() > 0)
                .sorted(Comparator.comparing(ClientFundPosition::getId))
                .toList();

        BigDecimal totalInvested = positions.stream()
                .map(ClientFundPosition::getTotalInvested)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (positions.isEmpty() || totalInvested.signum() <= 0) {
            log.warn(
                    "B11 distribution skipped: fund={} nema klijentske pozicije za proporcionalnu raspodelu.",
                    fundId
            );
            return List.of();
        }

        List<ClientFundTransaction> distributions = new java.util.ArrayList<>();
        BigDecimal distributedSoFar = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        for (int i = 0; i < positions.size(); i++) {
            ClientFundPosition position = positions.get(i);

            BigDecimal clientAmount;

            if (i == positions.size() - 1) {
                clientAmount = totalDividend
                        .subtract(distributedSoFar)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            } else {
                clientAmount = totalDividend
                        .multiply(position.getTotalInvested())
                        .divide(totalInvested, MONEY_SCALE, RoundingMode.HALF_UP);

                distributedSoFar = distributedSoFar
                        .add(clientAmount)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            }

            if (clientAmount.signum() <= 0) {
                continue;
            }

            Account destinationAccount = resolveClientRsdAccountForUpdate(position.getUserId());

            debit(fundAccount, clientAmount);
            credit(destinationAccount, clientAmount);

            ClientFundTransaction distribution = new ClientFundTransaction();
            distribution.setFundId(fundId);
            distribution.setUserId(position.getUserId());
            distribution.setUserRole(position.getUserRole());
            distribution.setAmountRsd(clientAmount);
            distribution.setSourceAccountId(destinationAccount.getId());
            distribution.setInflow(false);
            distribution.setStatus(ClientFundTransactionStatus.DIVIDEND_DISTRIBUTED);
            distribution.setCreatedAt(LocalDateTime.now());
            distribution.setCompletedAt(LocalDateTime.now());
            distribution.setFailureReason("DIVIDEND_DISTRIBUTION fromFundAccountId=" + fundAccount.getId());

            distributions.add(clientFundTransactionRepository.save(distribution));
        }

        for (ClientFundTransaction pending : pendingDividends) {
            pending.setStatus(ClientFundTransactionStatus.DIVIDEND_DISTRIBUTED);
            pending.setCompletedAt(LocalDateTime.now());
            pending.setFailureReason("DIVIDEND_DISTRIBUTED totalAmount=" + totalDividend);
            clientFundTransactionRepository.save(pending);
        }

        fundValueSnapshotScheduler.snapshotFundIfMissing(fund);

        log.info(
                "B11 dividends distributed: fund={}, total={}, clients={}",
                fundId,
                totalDividend,
                distributions.size()
        );

        return distributions;
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void scheduledDividendProcessing() {
        List<InvestmentFund> activeFunds = investmentFundRepository.findByActiveTrueOrderByNameAsc();

        for (InvestmentFund fund : activeFunds) {
            try {
                reinvestDividends(fund.getId());
            } catch (RuntimeException ex) {
                log.error(
                        "B11 scheduled dividend processing failed for fund #{}: {}",
                        fund.getId(),
                        ex.getMessage(),
                        ex
                );
            }
        }
    }

    private Order createInternalBuyOrder(
            InvestmentFund fund,
            Account fundAccount,
            Listing listing,
            int quantity,
            BigDecimal priceInListingCurrency,
            BigDecimal reservedAmount
    ) {
        Order order = new Order();

        order.setUserId(fund.getId());
        order.setUserRole(UserRole.FUND);
        order.setFundId(fund.getId());
        order.setListing(listing);
        order.setQuantity(quantity);
        order.setRemainingPortions(quantity);
        order.setContractSize(1);
        order.setPricePerUnit(priceInListingCurrency);
        order.setApproximatePrice(
                priceInListingCurrency
                        .multiply(BigDecimal.valueOf(quantity))
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP)
        );
        order.setDirection(OrderDirection.BUY);
        order.setOrderType(OrderType.MARKET);
        order.setStatus(OrderStatus.APPROVED);
        order.setApprovedBy("SYSTEM_FUND_DIVIDEND_REINVESTMENT");
        order.setApprovedAt(LocalDateTime.now());
        order.setCreatedAt(LocalDateTime.now());
        order.setLastModification(LocalDateTime.now());
        order.setDone(false);
        order.setAfterHours(false);
        order.setAllOrNone(false);
        order.setMargin(false);
        order.setReservedAccountId(fundAccount.getId());
        order.setAccountId(fundAccount.getId());
        order.setReservedAmount(reservedAmount);
        order.setExchangeRate(resolveListingToRsdRate(listing));
        order.setFxCommission(BigDecimal.ZERO);

        Order saved = orderRepository.save(order);

        fundReservationService.reserveForBuy(saved, fundAccount);

        log.info(
                "B11 reinvest order created: fund={}, order={}, listing={}, quantity={}, reservedRsd={}",
                fund.getId(),
                saved.getId(),
                listing.getTicker(),
                quantity,
                reservedAmount
        );

        return saved;
    }

    private InvestmentFund getActiveFund(Long fundId) {
        InvestmentFund fund = investmentFundRepository.findById(fundId)
                .orElseThrow(() -> new EntityNotFoundException("Fond ne postoji: " + fundId));

        if (!fund.isActive()) {
            throw new IllegalStateException("Fond " + fund.getName() + " nije aktivan.");
        }

        return fund;
    }

    private Account getFundAccountForUpdate(InvestmentFund fund) {
        Account account = accountRepository.findForUpdateById(fund.getAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Racun fonda ne postoji: " + fund.getAccountId()));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Racun fonda nije aktivan: " + account.getAccountNumber());
        }

        return account;
    }

    private Account resolveClientRsdAccountForUpdate(Long clientId) {
        Account selected = accountRepository
                .findByClientIdAndStatusOrderByAvailableBalanceDesc(clientId, AccountStatus.ACTIVE)
                .stream()
                .filter(account -> account.getCurrency() != null)
                .filter(account -> RSD.equalsIgnoreCase(account.getCurrency().getCode()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Klijent nema aktivan RSD racun: " + clientId));

        return accountRepository.findForUpdateById(selected.getId()).orElse(selected);
    }

    private Long extractListingId(ClientFundTransaction tx) {
        String reason = tx.getFailureReason();

        if (reason == null) {
            return null;
        }

        String marker = "listingId=";
        int start = reason.indexOf(marker);

        if (start < 0) {
            return null;
        }

        start += marker.length();

        int end = start;
        while (end < reason.length() && Character.isDigit(reason.charAt(end))) {
            end++;
        }

        if (end == start) {
            return null;
        }

        return Long.parseLong(reason.substring(start, end));
    }

    private BigDecimal resolveBuyPrice(Listing listing) {
        BigDecimal price = listing.getAsk() != null ? listing.getAsk() : listing.getPrice();

        if (price == null || price.signum() <= 0) {
            throw new IllegalStateException("Listing " + listing.getTicker() + " nema validnu cenu za BUY order.");
        }

        return price.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveListingToRsdRate(Listing listing) {
        String listingCurrency = ListingCurrencyResolver
                .resolveSafe(listing, RSD)
                .toUpperCase(Locale.ROOT);

        if (RSD.equals(listingCurrency)) {
            return BigDecimal.ONE;
        }

        return currencyConversionService.getRate(listingCurrency, RSD);
    }

    private BigDecimal convertToRsd(BigDecimal amount, String fromCurrency) {
        String normalized = fromCurrency == null ? RSD : fromCurrency.toUpperCase(Locale.ROOT);

        if (RSD.equals(normalized)) {
            return scale(amount);
        }

        return currencyConversionService.convert(amount, normalized, RSD);
    }

    private void markFailed(ClientFundTransaction tx, String reason) {
        tx.setStatus(ClientFundTransactionStatus.FAILED);
        tx.setCompletedAt(LocalDateTime.now());
        tx.setFailureReason(reason);

        clientFundTransactionRepository.save(tx);
    }

    private void debit(Account account, BigDecimal amount) {
        BigDecimal scaled = scale(amount);

        account.setBalance(scale(account.getBalance()).subtract(scaled));
        account.setAvailableBalance(scale(account.getAvailableBalance()).subtract(scaled));

        accountRepository.save(account);
    }

    private void credit(Account account, BigDecimal amount) {
        BigDecimal scaled = scale(amount);

        account.setBalance(scale(account.getBalance()).add(scaled));
        account.setAvailableBalance(scale(account.getAvailableBalance()).add(scaled));

        accountRepository.save(account);
    }

    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}