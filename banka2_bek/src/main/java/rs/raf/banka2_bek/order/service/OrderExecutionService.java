package rs.raf.banka2_bek.order.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.transaction.model.Transaction;
import rs.raf.banka2_bek.transaction.repository.TransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Servis za izvrsavanje odobrenih naloga (APPROVED).
 *
 * Specifikacija: Celina 3 - Order Execution Engine
 *
 * Simulira izvrsavanje naloga na berzi koristeci parcijalno punjenje (partial fills).
 * Podrzava: MARKET, LIMIT, AON (all-or-none), after-hours naloge.
 * STOP i STOP_LIMIT nalozi se ovde NE izvrsavaju — oni se prvo aktiviraju
 * u StopOrderActivationService pa postaju MARKET/LIMIT.
 *
 * Provizije po specifikaciji:
 * - MARKET: max(14% * price, $7)
 * - LIMIT:  max(24% * price, $12)
 * Provizija se uplacuje na racun banke.
 */
@Service
@RequiredArgsConstructor
public class OrderExecutionService {

    private static final Logger log = LoggerFactory.getLogger(OrderExecutionService.class);

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;
    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final AonValidationService aonValidationService;

    /** Provizija za MARKET naloge: min(14% * price, $7) — spec: "koji iznos je manji" */
    private static final BigDecimal MARKET_COMMISSION_RATE = new BigDecimal("0.14");
    private static final BigDecimal MARKET_COMMISSION_MAX = new BigDecimal("7");

    /** Provizija za LIMIT naloge: min(24% * price, $12) — spec: "koji iznos je manji" */
    private static final BigDecimal LIMIT_COMMISSION_RATE = new BigDecimal("0.24");
    private static final BigDecimal LIMIT_COMMISSION_MAX = new BigDecimal("12");

    /** Dodatan delay po fill-u za after-hours naloge (u minutima) */
    private static final int AFTER_HOURS_DELAY_MINUTES = 30;

    @Transactional
    public void executeOrders() {
        // 1. Dohvatiti sve APPROVED naloge koji nisu zavrseni
        List<Order> activeOrders = orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED);

        // 2. Filtrirati samo MARKET i LIMIT naloge
        // STOP i STOP_LIMIT su vec pretvoreni u MARKET/LIMIT u proslom zadatku
        List<Order> executableOrders = activeOrders.stream()
                .filter(o -> o.getOrderType() == OrderType.MARKET || o.getOrderType() == OrderType.LIMIT)
                .toList();

        log.info("Starting execution cycle for {} orders.", executableOrders.size());

        for (Order order : executableOrders) {
            try {
                // 3a. Provera settlement date-a (samo za futures/opcije gde postoji)
                if (order.getListing().getSettlementDate() != null &&
                        order.getListing().getSettlementDate().isBefore(LocalDate.now())) {

                    order.setStatus(OrderStatus.DECLINED);
                    order.setDone(true);
                    order.setLastModification(LocalDateTime.now());
                    orderRepository.save(order);

                    log.warn("Order #{} auto-declined: settlement date {} has passed",
                            order.getId(), order.getListing().getSettlementDate());
                    continue;
                }

                // 3b. Provera after-hours delay
                // Ako je after-hours, mora proci AFTER_HOURS_DELAY_MINUTES od poslednje promene
                if (order.isAfterHours()) {
                    LocalDateTime lastMod = order.getLastModification();
                    if (lastMod != null && lastMod.plusMinutes(AFTER_HOURS_DELAY_MINUTES).isAfter(LocalDateTime.now())) {
                        log.debug("Order #{} is after-hours, skipping until delay passes.", order.getId());
                        continue;
                    }
                }

                // 3c. Izvrsavanje pojedinacnog naloga
                executeSingleOrder(order);

            } catch (Exception e) {
                // 4. Wrap u try-catch da greska na jednom nalogu ne srusi celu petlju
                log.error("Critical error executing order #{}: {}", order.getId(), e.getMessage());
            }
        }
    }
    private void executeSingleOrder(Order order) {
        // 1. Dohvatiti ažuriranu cenu listinga
        Listing listing = listingRepository.findById(order.getListing().getId())
                .orElseThrow(() -> new RuntimeException("Listing not found for order #" + order.getId()));

        // 2. Odrediti execution price
        BigDecimal executionPrice;
        if (order.getOrderType() == OrderType.MARKET) {
            executionPrice = (order.getDirection() == OrderDirection.BUY) ? listing.getAsk() : listing.getBid();
        } else { // LIMIT
            if (order.getDirection() == OrderDirection.BUY) {
                if (listing.getAsk().compareTo(order.getLimitValue()) > 0) return; // Cena previsoka
                executionPrice = listing.getAsk();
            } else {
                if (listing.getBid().compareTo(order.getLimitValue()) < 0) return; // Cena preniska
                executionPrice = listing.getBid();
            }
        }

        // 3. Odrediti količinu za fill (Vremenska formula)
        long volume = (listing.getVolume() == null || listing.getVolume() == 0) ? 1 : listing.getVolume();
        int remaining = order.getRemainingPortions();

        // Formula: maxInterval u sekundama, pa randomizacija
        long maxInterval = (24L * 60L) / (volume / (long) remaining);
        if (maxInterval <= 0) maxInterval = 1;
        long randomInterval = ThreadLocalRandom.current().nextLong(0, maxInterval + 1);

        int fillQuantity = Math.max(1, (int) (remaining / Math.max(1, randomInterval)));
        fillQuantity = Math.min(fillQuantity, remaining);

        // b. AON (All-or-None) provera
        if (!aonValidationService.checkCanExecuteAon(order, fillQuantity)) {
            return;
        }
        if (order.isAllOrNone()) {
            fillQuantity = order.getQuantity(); // AON mora sve
        }

        // 4. Izračun ukupne cene i provizije
        BigDecimal contractSize = BigDecimal.valueOf(order.getContractSize());
        BigDecimal totalPrice = executionPrice.multiply(BigDecimal.valueOf(fillQuantity)).multiply(contractSize)
                .setScale(4, RoundingMode.HALF_UP);

        // Provizija se ne naplaćuje ako zaposleni trguje u ime banke
        BigDecimal commission = "EMPLOYEE".equals(order.getUserRole()) ? BigDecimal.ZERO : calculateCommission(totalPrice, order.getOrderType());

        // 5. Finansijske operacije
        try {
            if (order.getDirection() == OrderDirection.BUY) {
                updateAccountBalance(order, fillQuantity, executionPrice, commission);
                updatePortfolio(order, fillQuantity, executionPrice);
            } else {
                updatePortfolio(order, -fillQuantity, executionPrice); // negativan qty za SELL
                updateAccountBalance(order, fillQuantity, executionPrice, commission);
            }
            createFillTransaction(order, fillQuantity, executionPrice);

            // 6. Ažurirati nalog
            order.setRemainingPortions(order.getRemainingPortions() - fillQuantity);
            order.setLastModification(LocalDateTime.now());
            if (order.getRemainingPortions() <= 0) {
                order.setDone(true);
                order.setStatus(OrderStatus.DONE);
            }
            orderRepository.save(order);

            log.info("Order #{} filled {} of {} @ {} (remaining: {}, commission: {})",
                    order.getId(), fillQuantity, order.getQuantity(),
                    executionPrice, order.getRemainingPortions(), commission);

        } catch (Exception e) {
            log.error("Failed to process financial settlement for order #{}: {}", order.getId(), e.getMessage());
        }
    }
    private void createFillTransaction(Order order, int quantity, BigDecimal price) {
        Account account = accountRepository.findById(order.getAccountId())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(quantity))
                .multiply(BigDecimal.valueOf(order.getContractSize()))
                .setScale(4, RoundingMode.HALF_UP);

        Transaction transaction = Transaction.builder()
                .account(account)
                .currency(account.getCurrency())
                .description("Order #" + order.getId() + " fill: " + quantity + " x " +
                        order.getListing().getTicker() + " @ " + price)
                .debit(order.getDirection() == OrderDirection.BUY ? totalAmount : BigDecimal.ZERO)
                .credit(order.getDirection() == OrderDirection.SELL ? totalAmount : BigDecimal.ZERO)
                .balanceAfter(account.getBalance())
                .availableAfter(account.getAvailableBalance())
                .createdAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);
    }

    private void updatePortfolio(Order order, int quantity, BigDecimal price) {
        Optional<Portfolio> existing = portfolioRepository.findByUserId(order.getUserId())
                .stream()
                .filter(p -> p.getListingId().equals(order.getListing().getId()))
                .findFirst();

        if (existing.isPresent()) {
            Portfolio portfolio = existing.get();
            int oldQty = portfolio.getQuantity();

            if (quantity > 0) { // BUY
                BigDecimal oldTotal = portfolio.getAverageBuyPrice().multiply(BigDecimal.valueOf(oldQty));
                BigDecimal newFillTotal = price.multiply(BigDecimal.valueOf(quantity));
                int newQty = oldQty + quantity;

                BigDecimal newAvg = oldTotal.add(newFillTotal)
                        .divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP);

                portfolio.setQuantity(newQty);
                portfolio.setAverageBuyPrice(newAvg);
                portfolioRepository.save(portfolio);
            } else { // SELL (quantity je već negativan)
                int newQty = oldQty + quantity;
                if (newQty <= 0) {
                    portfolioRepository.delete(portfolio);
                } else {
                    portfolio.setQuantity(newQty);
                    portfolioRepository.save(portfolio);
                }
            }
        } else if (quantity > 0) { // Prvi BUY
            Portfolio portfolio = new Portfolio();
            portfolio.setUserId(order.getUserId());
            portfolio.setListingId(order.getListing().getId());
            portfolio.setListingTicker(order.getListing().getTicker());
            portfolio.setListingName(order.getListing().getName());
            portfolio.setListingType(order.getListing().getListingType().name());
            portfolio.setQuantity(quantity);
            portfolio.setAverageBuyPrice(price);
            portfolio.setPublicQuantity(0);

            portfolioRepository.save(portfolio);
        } else {
            throw new IllegalStateException("Attempted to sell assets not present in portfolio for user: " + order.getUserId());
        }
    }

    private void updateAccountBalance(Order order, int quantity, BigDecimal price, BigDecimal commission) {
        Account userAccount = accountRepository.findById(order.getAccountId())
                .orElseThrow(() -> new RuntimeException("User account not found"));

        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(quantity))
                .multiply(BigDecimal.valueOf(order.getContractSize()))
                .setScale(4, RoundingMode.HALF_UP);

        if (order.getDirection() == OrderDirection.BUY) {
            // BUY: skida balance + commission
            BigDecimal totalDebit = totalAmount.add(commission);
            userAccount.setBalance(userAccount.getBalance().subtract(totalDebit));
            userAccount.setAvailableBalance(userAccount.getAvailableBalance().subtract(totalDebit));
        } else {
            // SELL: dodaje balance, skida commission
            userAccount.setBalance(userAccount.getBalance().add(totalAmount).subtract(commission));
            userAccount.setAvailableBalance(userAccount.getAvailableBalance().add(totalAmount).subtract(commission));
        }
        accountRepository.save(userAccount);

        // 6. Uplata provizije na račun banke
        if (commission.compareTo(BigDecimal.ZERO) > 0) {
            Account bankAccount = getBankAccount(userAccount.getCurrency().getId());
            bankAccount.setBalance(bankAccount.getBalance().add(commission));
            bankAccount.setAvailableBalance(bankAccount.getAvailableBalance().add(commission));
            accountRepository.save(bankAccount);

            createCommissionTransaction(order, bankAccount, commission);
        }
    }

    /**
     * Racuna proviziju: MARKET min(14% * price, $7), LIMIT min(24% * price, $12)
     * Spec: "u zavisnosti od toga koji iznos je manji"
     */
    private BigDecimal calculateCommission(BigDecimal totalPrice, OrderType orderType) {
        if (orderType == OrderType.MARKET) {
            return totalPrice.multiply(MARKET_COMMISSION_RATE).min(MARKET_COMMISSION_MAX);
        } else {
            return totalPrice.multiply(LIMIT_COMMISSION_RATE).min(LIMIT_COMMISSION_MAX);
        }
    }

    /**
     * Pronalazi racun banke (Company ID = 3) u valuti naloga.
     */
    private Account getBankAccount(Long currencyId) {
        return accountRepository.findAll().stream()
                .filter(a -> a.getCompany() != null && a.getCompany().getId() == 3L)
                .filter(a -> a.getCurrency().getId().equals(currencyId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Bank account for currency ID " + currencyId + " not found!"));
    }
    private void createCommissionTransaction(Order order, Account bankAccount, BigDecimal commission) {
        Transaction bankTransaction = Transaction.builder()
                .account(bankAccount)
                .currency(bankAccount.getCurrency())
                .description("Commission for Order #" + order.getId())
                .credit(commission)
                .debit(BigDecimal.ZERO)
                .balanceAfter(bankAccount.getBalance())
                .availableAfter(bankAccount.getAvailableBalance())
                .createdAt(LocalDateTime.now())
                .build();

        transactionRepository.save(bankTransaction);
    }

}
