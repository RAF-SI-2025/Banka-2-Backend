package rs.raf.banka2_bek.order.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.util.UserRole;
import rs.raf.banka2_bek.investmentfund.service.FundLiquidationService;
import rs.raf.banka2_bek.order.event.OrderCompletedEvent;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.transaction.model.Transaction;
import rs.raf.banka2_bek.transaction.repository.TransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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
 *
 * Fund-ownership flow (Celina 4 (Nova) §3883-3964): kad supervizor kupi hartiju u
 * ime fonda, fund-ovi commit ide kroz {@code Portfolio} sa {@code userRole="FUND"}
 * + {@code userId=fund.id}. Sredstva se skidaju sa {@code fund.account} (postavljeno
 * u OrderServiceImpl.createOrder kad je {@code fundId != null}). Tax obracun
 * preskace fund-ordere (vidi {@code TaxService.calculateTaxForAllUsers} fundId filter).
 *
 * STOP i STOP_LIMIT nalozi se ovde NE izvrsavaju — oni se prvo aktiviraju
 * u StopOrderActivationService pa postaju MARKET/LIMIT.
 *
 * Provizije po specifikaciji (manji iznos od dva):
 * - MARKET: min(14% * price, $7)
 * - LIMIT:  min(24% * price, $12)
 * Provizija se uplacuje na racun banke. Za EMPLOYEE ordere provizija je 0.
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
    private final FundReservationService fundReservationService;
    private final FundLiquidationService fundLiquidationService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${bank.registration-number}")
    private String bankRegistrationNumber;

    /** Minimalan broj sekundi izmedju approval-a i prvog fill pokusaja (Phase 6). */
    @Value("${orders.execution.initial-delay-seconds:60}")
    private long initialDelaySeconds;

    /**
     * Dodatan delay za after-hours naloge (u sekundama). Spec trazi 30 min
     * po svakom fill-u; za demo se moze skratiti (npr. 60s u dev-u).
     */
    @Value("${orders.afterhours.delay-seconds:1800}")
    private long afterHoursDelaySeconds;

    /**
     * Safety cap za spec-izracunati interval izmedju fill-ova kod niskog
     * volumena — da order sa volume=0 ili tankim volumenom ne zamrzne
     * izvrsavanje na nepredvidivo dugo.
     */
    @Value("${orders.execution.max-fill-interval-seconds:600}")
    private long maxFillIntervalSeconds;

    /** Provizija za MARKET naloge: min(14% * price, $7) — spec: "koji iznos je manji" */
    private static final BigDecimal MARKET_COMMISSION_RATE = new BigDecimal("0.14");
    private static final BigDecimal MARKET_COMMISSION_CAP = new BigDecimal("7");

    /** Provizija za LIMIT naloge: min(24% * price, $12) — spec: "koji iznos je manji" */
    private static final BigDecimal LIMIT_COMMISSION_RATE = new BigDecimal("0.24");
    private static final BigDecimal LIMIT_COMMISSION_CAP = new BigDecimal("12");

    /** Menjacnica marza koja se naplacuje klijentu na SELL kad konvertuje u drugu valutu. */
    private static final BigDecimal SELL_FX_MARGIN = new BigDecimal("0.01");

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

        LocalDateTime now = LocalDateTime.now();
        for (Order order : executableOrders) {
            try {
                // 3a. Provera settlement date-a (samo za futures/opcije gde postoji)
                if (order.getListing().getSettlementDate() != null &&
                        order.getListing().getSettlementDate().isBefore(LocalDate.now())) {

                    order.setStatus(OrderStatus.DECLINED);
                    order.setDone(true);
                    order.setLastModification(LocalDateTime.now());
                    // Oslobadjanje rezervacije za auto-declined order
                    // (releaseReservationSafe vec interno guta sve greske)
                    releaseReservationSafe(order);
                    orderRepository.save(order);

                    log.warn("Order #{} auto-declined: settlement date {} has passed",
                            order.getId(), order.getListing().getSettlementDate());
                    continue;
                }

                // 3b. Fill eligibility guard.
                // Ako nalog ima setovan nextFillAt (posle prvog uspesnog fill-a),
                // koristi ga direktno — spec zahteva random interval izmedju
                // fill-ova + 30 min bonus za after-hours naloge PO SVAKOM fill-u.
                // Ako jos nije bilo fill-a, primeni standardni initialDelay
                // guard koristeci approvedAt/createdAt.
                if (order.getNextFillAt() != null) {
                    if (now.isBefore(order.getNextFillAt())) {
                        log.debug("Order #{} not yet eligible — next fill at {}",
                                order.getId(), order.getNextFillAt());
                        continue;
                    }
                } else {
                    LocalDateTime referenceTime = order.getApprovedAt() != null
                            ? order.getApprovedAt()
                            : order.getCreatedAt();
                    if (referenceTime != null) {
                        long requiredDelay = order.isAfterHours()
                                ? initialDelaySeconds + afterHoursDelaySeconds
                                : initialDelaySeconds;
                        if (Duration.between(referenceTime, now).getSeconds() < requiredDelay) {
                            log.debug("Order #{} not yet eligible for execution (needs {}s delay)",
                                    order.getId(), requiredDelay);
                            continue;
                        }
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
    void executeSingleOrder(Order order) {
        // 0. Legacy guard: APPROVED orderi iz starog seed-a nemaju reservedAccountId
        // ni accountId — ne mogu se izvrsiti. Markiraj ih kao DECLINED da scheduler
        // prekine retry loop.
        if (order.getReservedAccountId() == null && order.getAccountId() == null) {
            log.warn("Order #{} nema ni reservedAccountId ni accountId — oznacavam kao DECLINED (legacy seed)", order.getId());
            order.setStatus(OrderStatus.DECLINED);
            order.setLastModification(LocalDateTime.now());
            orderRepository.save(order);
            return;
        }

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

        // 3. Odrediti količinu za fill
        int remaining = order.getRemainingPortions() != null ? order.getRemainingPortions() : order.getQuantity();
        if (remaining <= 0) {
            order.setDone(true);
            order.setStatus(OrderStatus.DONE);
            order.setLastModification(LocalDateTime.now());
            releaseReservationSafe(order);
            orderRepository.save(order);
            publishOrderCompleted(order);
            return;
        }

        // Spec: Random fill quantity between 1 and remaining
        int fillQuantity = ThreadLocalRandom.current().nextInt(1, remaining + 1);
        fillQuantity = Math.min(fillQuantity, remaining);

        // b. AON (All-or-None) provera
        if (!aonValidationService.checkCanExecuteAon(order, fillQuantity)) {
            return;
        }
        if (order.isAllOrNone()) {
            fillQuantity = order.getQuantity(); // AON mora sve
        }

        // 4. Izračun ukupne cene i provizije (sve u valuti listinga)
        BigDecimal contractSize = BigDecimal.valueOf(order.getContractSize());
        BigDecimal totalPriceInListing = executionPrice.multiply(BigDecimal.valueOf(fillQuantity))
                .multiply(contractSize)
                .setScale(4, RoundingMode.HALF_UP);

        boolean isEmployee = UserRole.isEmployee(order.getUserRole()) || UserRole.FUND.equals(order.getUserRole());
        BigDecimal commissionInListing = isEmployee
                ? BigDecimal.ZERO
                : calculateCommission(totalPriceInListing, order.getOrderType());

        // Konverzija u valutu racuna. Za single-currency orderi (exchangeRate=1 ili null)
        // se ponasa kao pre.
        BigDecimal midRate = order.getExchangeRate() != null ? order.getExchangeRate() : BigDecimal.ONE;
        BigDecimal totalPriceInAccount = totalPriceInListing.multiply(midRate)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal commissionInAccount = commissionInListing.multiply(midRate)
                .setScale(4, RoundingMode.HALF_UP);

        // 5. Finansijske operacije preko FundReservationService (Phase 6 rewire).
        //    Exception se propagira i @Transactional radi rollback.
        if (order.getDirection() == OrderDirection.BUY) {
            // Pro-rata FX komisija za ovaj fill (iz order.fxCommission koji je bio
            // rezervisan pri kreiranju/odobravanju). 0 za zaposlene ili single-currency.
            BigDecimal fxForFill = proRataFxCommission(order, fillQuantity);
            BigDecimal totalDebit = totalPriceInAccount.add(commissionInAccount).add(fxForFill)
                    .setScale(4, RoundingMode.HALF_UP);
            fundReservationService.consumeForBuyFill(order, fillQuantity, totalDebit);
            // Bank prihod = order commission + FX commission (oboje u valuti racuna/banke).
            creditBankCommission(order, commissionInAccount.add(fxForFill));
            updatePortfolio(order, fillQuantity, executionPrice);
        } else {
            // SELL: consumeForSellFill skida qty iz portfolia i reservedQuantity.
            // Prihod (totalPrice - commission) ide na racun naloga (reservedAccountId).
            // Za klijenta sa razlicitom valutom racuna, jos 1% bankovske menjacnice
            // se skida pre isplate (spec: "prilikom konverzije uzimamo proviziju").

            // Za fond-ordere: portfolio je u FUND porfoliju, ne u supervizora
            Long sellPortfolioUserId = order.getFundId() != null ? order.getFundId() : order.getUserId();
            String sellPortfolioUserRole = order.getFundId() != null ? UserRole.FUND : order.getUserRole();

            Portfolio portfolio = portfolioRepository
                    .findByUserIdAndUserRole(sellPortfolioUserId, sellPortfolioUserRole).stream()
                    .filter(p -> p.getListingId().equals(order.getListing().getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Portfolio nije pronadjen za SELL order #" + order.getId()));

            fundReservationService.consumeForSellFill(order, portfolio, fillQuantity);

            Long receivingAccountId = order.getReservedAccountId() != null
                    ? order.getReservedAccountId()
                    : order.getAccountId();
            Account receivingAccount = accountRepository.findForUpdateById(receivingAccountId)
                    .orElseThrow(() -> new RuntimeException(
                            "Receiving account not found for SELL order #" + order.getId()));

            BigDecimal netRevenueInAccount = totalPriceInAccount.subtract(commissionInAccount);

            boolean multiCurrency = midRate.compareTo(BigDecimal.ONE) != 0
                    && order.getListing().getListingType() != ListingType.FOREX;
            BigDecimal fxFee = BigDecimal.ZERO;
            if (!isEmployee && multiCurrency) {
                fxFee = netRevenueInAccount.multiply(SELL_FX_MARGIN).setScale(4, RoundingMode.HALF_UP);
                netRevenueInAccount = netRevenueInAccount.subtract(fxFee);
            }

            receivingAccount.setBalance(receivingAccount.getBalance().add(netRevenueInAccount));
            receivingAccount.setAvailableBalance(receivingAccount.getAvailableBalance().add(netRevenueInAccount));
            accountRepository.save(receivingAccount);

            creditBankCommission(order, commissionInAccount.add(fxFee));
        }
        createFillTransaction(order, fillQuantity, executionPrice);

        // 6. Ažurirati nalog
        order.setRemainingPortions(order.getRemainingPortions() - fillQuantity);
        order.setLastModification(LocalDateTime.now());
        boolean justCompleted = false;
        if (order.getRemainingPortions() <= 0) {
            order.setDone(true);
            order.setStatus(OrderStatus.DONE);
            order.setNextFillAt(null);
            // Ako je ostao visak rezervacije (npr. fill po nizoj ceni od approxPrice)
            // vrati ga na availableBalance / availableQuantity.
            releaseReservationSafe(order);
            justCompleted = true;
        } else {
            // Spec: vremenski interval izmedju fill-ova =
            //   Random(0, 24 * 60 / (volume / remaining)) sekundi
            // + 30 min bonus za after-hours naloge (po svakom fill-u).
            order.setNextFillAt(LocalDateTime.now().plusSeconds(computeNextFillDelay(order, listing)));
        }
        orderRepository.save(order);

        log.info("Order #{} filled {} of {} @ {} (remaining: {}, orderComm: {}, listingCcy)",
                order.getId(), fillQuantity, order.getQuantity(),
                executionPrice, order.getRemainingPortions(), commissionInListing);

        if ("FUND".equals(order.getUserRole())) {
            log.info("T9 Hook: Detektovan nalog fonda #{}. Pokrecem resolve pending transakcija.", order.getUserId());
            fundLiquidationService.onFillCompleted(order.getId());
        }

        if (justCompleted) {
            publishOrderCompleted(order);
        }
    }

    /**
     * Emit-uje {@link OrderCompletedEvent} kad order zavrsi (status DONE).
     * Konzumenti (npr. {@code ProfitBankCacheEvictionListener}) invalidiraju
     * cached izvedena polja.
     */
    private void publishOrderCompleted(Order order) {
        try {
            eventPublisher.publishEvent(new OrderCompletedEvent(
                    order.getId(),
                    order.getUserId(),
                    order.getUserRole(),
                    order.getFundId()));
        } catch (RuntimeException ex) {
            // Ne sme da pukne order fill flow zbog event-a — log i nastavi.
            log.warn("Order #{} completed event publish failed: {}", order.getId(), ex.getMessage());
        }
    }

    /**
     * Izracunava koliko sekundi ceka do sledeceg fill pokusaja po specifikaciji:
     *   Random(0, 24 * 60 / (volume / remaining)) sekundi + 30 min ako je after-hours.
     * Ako je volume nula ili nema remainingPortions, fallback je {@code maxFillIntervalSeconds}.
     * Kompletan rezultat je ogranicen na {@code maxFillIntervalSeconds} + after-hours bonus.
     */
    long computeNextFillDelay(Order order, Listing listing) {
        long afterHoursBonus = order.isAfterHours() ? afterHoursDelaySeconds : 0L;
        Long volume = listing != null ? listing.getVolume() : null;
        int remaining = order.getRemainingPortions() != null ? order.getRemainingPortions() : 0;
        if (volume == null || volume <= 0L || remaining <= 0) {
            return maxFillIntervalSeconds + afterHoursBonus;
        }
        // 24 * 60 minuta trgovackog dana = 1440. Kad je volume ogroman
        // (npr. MSFT 50M/day) a remaining 10, formula daje milisekundni delay;
        // zato cap na maxFillIntervalSeconds (default 10 min).
        double maxSeconds = 1440.0 / ((double) volume / remaining);
        long capped = Math.max(0L, Math.min((long) Math.ceil(maxSeconds), maxFillIntervalSeconds));
        long randomDelay = capped > 0 ? ThreadLocalRandom.current().nextLong(0L, capped + 1L) : 0L;
        return randomDelay + afterHoursBonus;
    }

    /**
     * Idempotentno oslobadja rezervaciju za order (BUY: funds, SELL: portfolio qty).
     * Loguje i proguta greske da jedan fail ne sruši execution petlju.
     */
    private void releaseReservationSafe(Order order) {
        if (order.isReservationReleased()) {
            return;
        }
        try {
            if (order.getDirection() == OrderDirection.BUY) {
                fundReservationService.releaseForBuy(order);
            } else {
                // Za fond-ordere: traži portfolio u FUND portfoliju, ne u supervizora
                Long sellPortfolioUserId = order.getFundId() != null ? order.getFundId() : order.getUserId();
                String sellPortfolioUserRole = order.getFundId() != null ? UserRole.FUND : order.getUserRole();

                Portfolio portfolio = portfolioRepository
                        .findByUserIdAndUserRole(sellPortfolioUserId, sellPortfolioUserRole).stream()
                        .filter(p -> p.getListingId().equals(order.getListing().getId()))
                        .findFirst()
                        .orElse(null);
                if (portfolio != null) {
                    fundReservationService.releaseForSell(order, portfolio);
                } else {
                    order.setReservationReleased(true);
                }
            }
        } catch (Exception e) {
            log.warn("Release reservation failed for order #{}: {}", order.getId(), e.getMessage());
        }
    }

    /**
     * Uplacuje proviziju na bankin racun u valuti order-a i kreira transakciju.
     * No-op ako je commission = 0 (zaposleni).
     */
    private void creditBankCommission(Order order, BigDecimal commission) {
        if (commission.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Long accountId = order.getReservedAccountId() != null
                ? order.getReservedAccountId()
                : order.getAccountId();
        Account userAccount = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found for commission routing"));

        Account bankAccount = getBankAccount(userAccount.getCurrency().getId());
        bankAccount.setBalance(bankAccount.getBalance().add(commission));
        bankAccount.setAvailableBalance(bankAccount.getAvailableBalance().add(commission));
        accountRepository.save(bankAccount);

        createCommissionTransaction(order, bankAccount, commission);
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

    /**
     * Azurira portfolio nakon BUY fill-a. SELL fillovi NE prolaze ovuda — oni
     * se obradjuju kroz {@link FundReservationService#consumeForSellFill}.
     * Zato ovde tretiramo samo BUY (quantity > 0).
     *
     * Za fond-ordere (fundId != null), hartije se stavljaju u FUND portfolio,
     * ne u portfolio supervizora.
     */
    private void updatePortfolio(Order order, int quantity, BigDecimal price) {
        // Za fond-ordere: koristi fundId i "FUND" ulogu
        Long portfolioUserId = order.getFundId() != null ? order.getFundId() : order.getUserId();
        String portfolioUserRole = order.getFundId() != null ? UserRole.FUND : order.getUserRole();

        Optional<Portfolio> existing = portfolioRepository
                .findByUserIdAndUserRole(portfolioUserId, portfolioUserRole)
                .stream()
                .filter(p -> p.getListingId().equals(order.getListing().getId()))
                .findFirst();

        if (existing.isPresent()) {
            Portfolio portfolio = existing.get();
            int oldQty = portfolio.getQuantity();
            BigDecimal oldTotal = portfolio.getAverageBuyPrice().multiply(BigDecimal.valueOf(oldQty));
            BigDecimal newFillTotal = price.multiply(BigDecimal.valueOf(quantity));
            int newQty = oldQty + quantity;

            BigDecimal newAvg = oldTotal.add(newFillTotal)
                    .divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP);

            portfolio.setQuantity(newQty);
            portfolio.setAverageBuyPrice(newAvg);
            portfolioRepository.save(portfolio);
        } else {
            Portfolio portfolio = new Portfolio();
            portfolio.setUserId(portfolioUserId);
            portfolio.setUserRole(portfolioUserRole);
            portfolio.setListingId(order.getListing().getId());
            portfolio.setListingTicker(order.getListing().getTicker());
            portfolio.setListingName(order.getListing().getName());
            portfolio.setListingType(order.getListing().getListingType().name());
            portfolio.setQuantity(quantity);
            portfolio.setAverageBuyPrice(price);
            portfolio.setPublicQuantity(0);

            portfolioRepository.save(portfolio);
        }
    }

    /**
     * Racuna proviziju: MARKET min(14% * price, $7), LIMIT min(24% * price, $12)
     * Spec: "u zavisnosti od toga koji iznos je manji"
     */
    private BigDecimal calculateCommission(BigDecimal totalPrice, OrderType orderType) {
        if (orderType == OrderType.MARKET) {
            return totalPrice.multiply(MARKET_COMMISSION_RATE).min(MARKET_COMMISSION_CAP);
        } else {
            return totalPrice.multiply(LIMIT_COMMISSION_RATE).min(LIMIT_COMMISSION_CAP);
        }
    }

    /**
     * Pro-rata deo ukupne FX provizije ordera za jedan fill.
     * Vraca ZERO ako FX provizija nije obracunata (zaposleni / iste valute)
     * ili ako je quantity <= 0.
     */
    private BigDecimal proRataFxCommission(Order order, int fillQuantity) {
        BigDecimal totalFx = order.getFxCommission();
        if (totalFx == null || totalFx.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        Integer totalQty = order.getQuantity();
        if (totalQty == null || totalQty <= 0) {
            return BigDecimal.ZERO;
        }
        return totalFx.multiply(BigDecimal.valueOf(fillQuantity))
                .divide(BigDecimal.valueOf(totalQty), 4, RoundingMode.HALF_UP);
    }

    /**
     * Pronalazi racun banke u valuti naloga koristeci optimizovan query.
     */
    private Account getBankAccount(Long currencyId) {
        return accountRepository.findBankAccountByCurrencyId(bankRegistrationNumber, currencyId)
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