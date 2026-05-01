package rs.raf.banka2_bek.order.service.implementation;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserRole;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.order.dto.CreateOrderDto;
import rs.raf.banka2_bek.order.dto.OrderDto;
import rs.raf.banka2_bek.order.exception.InsufficientFundsException;
import rs.raf.banka2_bek.order.exception.InsufficientHoldingsException;
import rs.raf.banka2_bek.order.mapper.OrderMapper;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.order.service.BankTradingAccountResolver;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.order.service.FundReservationService;
import rs.raf.banka2_bek.order.service.ListingPriceService;
import rs.raf.banka2_bek.order.service.OrderService;
import rs.raf.banka2_bek.order.service.OrderStatusService;
import rs.raf.banka2_bek.order.service.OrderValidationService;
import rs.raf.banka2_bek.berza.service.ExchangeManagementService;
import rs.raf.banka2_bek.investmentfund.model.InvestmentFund;
import rs.raf.banka2_bek.investmentfund.repository.InvestmentFundRepository;
import org.springframework.security.access.AccessDeniedException;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.stock.util.ListingCurrencyResolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;
    private final ActuaryInfoRepository actuaryInfoRepository;
    private final ClientRepository clientRepository;
    private final EmployeeRepository employeeRepository;
    private final OrderValidationService orderValidationService;
    private final ListingPriceService listingPriceService;
    private final OrderStatusService orderStatusService;
    private final ExchangeManagementService exchangeManagementService;
    private final AccountRepository accountRepository;
    private final FundReservationService fundReservationService;
    private final BankTradingAccountResolver bankTradingAccountResolver;
    private final CurrencyConversionService currencyConversionService;
    private final PortfolioRepository portfolioRepository;
    private final InvestmentFundRepository investmentFundRepository;

    @Override
    @Transactional
    public OrderDto createOrder(CreateOrderDto dto) {
        // Step 1: Validate input
        orderValidationService.validate(dto);

        OrderType orderType = orderValidationService.parseOrderType(dto.getOrderType());
        OrderDirection direction = orderValidationService.parseDirection(dto.getDirection());

        // Step 2: Fetch listing
        Listing listing = listingRepository.findById(dto.getListingId())
                .orElseThrow(() -> new EntityNotFoundException("Listing not found"));

        // Step 3: Determine price
        BigDecimal pricePerUnit = listingPriceService.getPricePerUnit(dto, listing, orderType, direction);
        BigDecimal approximatePrice = listingPriceService.calculateApproximatePrice(
                dto.getContractSize(), pricePerUnit, dto.getQuantity());

        // Step 4: Resolve current user
        UserContext userContext = resolveCurrentUser();
        boolean isEmployee = UserRole.isEmployee(userContext.userRole());

        // Step 5: Resolve account.
        //   Klijent: licni racun
        //   Supervizor sa fundId: fond.account (RSD) — P3 / Celina 4 (Nova) §3883-3964
        //   Supervizor/agent bez fundId: bankin trading racun (postojeci flow)
        String listingCurrencyCode = resolveListingCurrency(listing);
        final InvestmentFund fund;
        Account account;
        if (dto.getFundId() != null) {
            if (!isEmployee) {
                throw new AccessDeniedException(
                        "Samo supervizori mogu da kupuju u ime investicionog fonda.");
            }
            fund = investmentFundRepository.findById(dto.getFundId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Investicioni fond ne postoji: " + dto.getFundId()));
            // P5 — proverava se da je supervizor manager fonda; ako nije, 403.
            if (!userContext.userId().equals(fund.getManagerEmployeeId())) {
                throw new AccessDeniedException(
                        "Niste manager fonda " + fund.getName() + " — ne mozete kupovati u njegovo ime.");
            }
            account = accountRepository.findForUpdateById(fund.getAccountId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Racun fonda ne postoji: " + fund.getAccountId()));
        } else {
            fund = null;
            account = resolveTradingAccount(dto.getAccountId(), isEmployee, listingCurrencyCode);
        }
        Portfolio portfolio = null;
        if (direction == OrderDirection.SELL) {
            portfolio = portfolioRepository
                    .findByUserIdAndUserRoleAndListingIdForUpdate(userContext.userId(), userContext.userRole(), listing.getId())
                    .orElseThrow(() -> new InsufficientHoldingsException(
                            "Nemate ovu hartiju u portfoliju"));
            int available = portfolio.getAvailableQuantity();
            if (available < dto.getQuantity()) {
                throw new InsufficientHoldingsException(
                        "Nedovoljno raspolozivih hartija. Dostupno: " + available
                                + ", traženo: " + dto.getQuantity());
            }
        }

        BigDecimal exchangeRate = null;
        BigDecimal totalReservation = null;
        BigDecimal fxCommission = BigDecimal.ZERO;
        // account je uvek non-null nakon if/else iznad — guard zadrzan iz citljivosti uklonjen
        if (direction == OrderDirection.BUY) {
            String accountCurrencyCode = account.getCurrency().getCode();
            boolean chargeFx = !isEmployee && !listingCurrencyCode.equals(accountCurrencyCode);

            CurrencyConversionService.ConversionResult priceConv = currencyConversionService
                    .convertForPurchase(approximatePrice, listingCurrencyCode, accountCurrencyCode, chargeFx);
            exchangeRate = priceConv.midRate();
            BigDecimal approxInAccountCurrency = priceConv.amount();
            fxCommission = priceConv.commission();

            // Provizija se obracunava u listing (USD-denominovanoj) valuti, zatim se konvertuje
            // u valutu racuna — tako cap od $7/$12 ostaje ispravan za sve kombinacije valuta.
            // Na FX konverziju provizije ordera takodje se primenjuje menjacnica (ako je obracunata).
            BigDecimal commissionInAccountCurrency;
            if (isEmployee) {
                commissionInAccountCurrency = BigDecimal.ZERO;
            } else {
                BigDecimal commissionInListingCurrency = calculateCommissionInListingCurrency(approximatePrice, orderType);
                CurrencyConversionService.ConversionResult commConv = currencyConversionService
                        .convertForPurchase(commissionInListingCurrency, listingCurrencyCode, accountCurrencyCode, chargeFx);
                commissionInAccountCurrency = commConv.amount();
                fxCommission = fxCommission.add(commConv.commission());
            }
            totalReservation = approxInAccountCurrency.add(commissionInAccountCurrency)
                    .setScale(4, RoundingMode.HALF_UP);
        } else { // SELL
            // Za SELL ne rezervisemo novac; ipak sacuvamo kurs listing→receiving account
            // kako bi fill engine znao u kojoj valuti da prihoduje pare na receiving racun.
            String accountCurrencyCode = account.getCurrency().getCode();
            exchangeRate = currencyConversionService.getRate(listingCurrencyCode, accountCurrencyCode);
        }

        // Step 6: Verify funds / holdings
        //   BUY: availableBalance >= totalReservation
        //   SELL: portfolio.availableQuantity >= dto.quantity (provereno iznad pri portfolio lookup-u)
        if (direction == OrderDirection.BUY) {
            if (account.getAvailableBalance().compareTo(totalReservation) < 0) {
                throw new InsufficientFundsException(
                        "Nedovoljno raspolozivih sredstava na racunu " + account.getAccountNumber());
            }
        }

        // Step 7: Determine status
        OrderStatus status = orderStatusService.determineStatus(userContext.userRole(), userContext.userId(), approximatePrice);
        String approvedBy = (status == OrderStatus.APPROVED) ? "No need for approval" : null;

        // Step 8: Compute afterHours
        boolean afterHours = computeAfterHours(listing);

        // Step 9: Build and save order
        Order order = OrderMapper.fromCreateDto(dto, listing);
        order.setUserId(userContext.userId());
        // S44 fix: eksplicitno setujemo userRole sa resolved userContext-a
        order.setUserRole(userContext.userRole());
        order.setPricePerUnit(pricePerUnit);
        order.setApproximatePrice(approximatePrice);
        order.setStatus(status);
        order.setApprovedBy(approvedBy);
        order.setAfterHours(afterHours);
        if (fund != null) {
            order.setFundId(fund.getId());
            // Za fond-ordere: userId je fundId i userRole je "FUND" (ne supervizora)
            order.setUserId(fund.getId());
            order.setUserRole(UserRole.FUND);
        }

        if (direction == OrderDirection.BUY) {
            order.setReservedAccountId(account.getId());
            order.setReservedAmount(totalReservation);
            order.setExchangeRate(exchangeRate);
            order.setFxCommission(fxCommission.compareTo(BigDecimal.ZERO) > 0 ? fxCommission : null);
            // Za agente pisemo bankin racun i na accountId da fill ima referencu
            if (isEmployee) {
                order.setAccountId(account.getId());
            }
        } else { // SELL
            // Za SELL "reservedAccountId" drzi receiving account (kuda idu pare po fill-u).
            // reservedAmount ostaje null — nema novcane rezervacije.
            order.setReservedAccountId(account.getId());
            order.setExchangeRate(exchangeRate);
            if (isEmployee) {
                order.setAccountId(account.getId());
            }
        }

        if (status == OrderStatus.APPROVED) {
            order.setApprovedAt(LocalDateTime.now());
        }

        Order savedOrder = orderRepository.save(order);

        // Step 10: Rezervacija (sredstva za BUY, kolicina hartija za SELL) za APPROVED ordere
        if (status == OrderStatus.APPROVED) {
            if (direction == OrderDirection.BUY) {
                fundReservationService.reserveForBuy(savedOrder, account);
            } else { // SELL — portfolio je uvek non-null nakon SELL grane iznad
                fundReservationService.reserveForSell(savedOrder, portfolio);
            }
        }

        // Step 11: Update agent usedLimit if APPROVED
        if (status == OrderStatus.APPROVED && isEmployee) {
            final BigDecimal limitDelta = totalReservation != null ? totalReservation : approximatePrice;
            Optional<ActuaryInfo> actuaryOpt = orderStatusService.getAgentInfo(userContext.userId());
            actuaryOpt.ifPresent(actuary -> {
                if (actuary.getActuaryType() == ActuaryType.AGENT) {
                    BigDecimal current = actuary.getUsedLimit() != null ? actuary.getUsedLimit() : BigDecimal.ZERO;
                    actuary.setUsedLimit(current.add(limitDelta));
                    actuaryInfoRepository.save(actuary);
                }
            });
        }

        // Step 12: Execution handled by OrderScheduler cron job

        return toDtoWithUserName(savedOrder);
    }

    /**
     * Resolve-uje ISO kod valute za dati listing. Delegira na
     * {@link ListingCurrencyResolver} — jedinstven util koriscen u vise
     * servisa (tax, OTC).
     */
    private String resolveListingCurrency(Listing listing) {
        return ListingCurrencyResolver.resolve(listing);
    }

    /**
     * Zajednicka logika za pronalazenje trading racuna za BUY i SELL orderi:
     *  - ako je {@code accountId} eksplicitno prosledjen, load-uje se pod lockom;
     *  - inace ako je korisnik zaposleni, uzima se bankin racun u valuti hartije;
     *  - inace je to greska (klijent mora navesti racun).
     */
    private Account resolveTradingAccount(Long accountId, boolean isEmployee, String listingCurrencyCode) {
        if (accountId != null) {
            return accountRepository.findForUpdateById(accountId)
                    .orElseThrow(() -> new EntityNotFoundException("Racun ne postoji: " + accountId));
        }
        if (isEmployee) {
            return bankTradingAccountResolver.resolve(listingCurrencyCode);
        }
        throw new EntityNotFoundException("Racun ne postoji: null");
    }

    /**
     * Racuna proviziju u valuti listinga (gde USD cap ima smisla).
     * Spec: Market min(14% * cena, $7), Limit min(24% * cena, $12).
     * Za non-USD listinge, cap od $7/$12 se tretira kao literal iznos
     * u listing valuti — pragmaticna aproksimacija jer se vecina listinga
     * denominuje u USD.
     */
    private BigDecimal calculateCommissionInListingCurrency(BigDecimal approxInListingCurrency, OrderType orderType) {
        return switch (orderType) {
            case MARKET, STOP -> approxInListingCurrency.multiply(new BigDecimal("0.14"))
                    .min(new BigDecimal("7"))
                    .setScale(4, RoundingMode.HALF_UP);
            case LIMIT, STOP_LIMIT -> approxInListingCurrency.multiply(new BigDecimal("0.24"))
                    .min(new BigDecimal("12"))
                    .setScale(4, RoundingMode.HALF_UP);
        };
    }

    @Override
    @Transactional
    public OrderDto approveOrder(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found" + orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be approved");
        }

        String supervisorName = getSupervisorName();

        Listing listing = order.getListing();
        if (listing.getSettlementDate() != null &&
                listing.getSettlementDate().isBefore(java.time.LocalDate.now())) {
            order.setStatus(OrderStatus.DECLINED);
            order.setApprovedBy(supervisorName);
            order.setLastModification(LocalDateTime.now());
            Order saved = orderRepository.save(order);
            return toDtoWithUserName(saved);
        }

        // Phase 5.1: Rezervacija sredstava / hartija u trenutku odobravanja.
        // Cena se mogla promeniti izmedju PENDING i sada — koristimo
        // order.approximatePrice kao polaznu tacku (vec izracunato pri createOrder).
        boolean isEmployee = UserRole.isEmployee(order.getUserRole());
        String listingCurrencyCode = resolveListingCurrency(listing);
        BigDecimal totalReservation = null;

        if (order.getDirection() == OrderDirection.BUY) {
            Account account;
            Long accountId = order.getReservedAccountId() != null
                    ? order.getReservedAccountId()
                    : order.getAccountId();
            if (accountId != null) {
                account = accountRepository.findForUpdateById(accountId)
                        .orElseThrow(() -> new EntityNotFoundException("Racun ne postoji: " + accountId));
            } else if (isEmployee) {
                account = bankTradingAccountResolver.resolve(listingCurrencyCode);
            } else {
                throw new EntityNotFoundException("Order nema povezan racun za rezervaciju");
            }

            String accountCurrencyCode = account.getCurrency().getCode();
            boolean chargeFx = !isEmployee && !listingCurrencyCode.equals(accountCurrencyCode);
            BigDecimal approxInListing = order.getApproximatePrice() != null
                    ? order.getApproximatePrice()
                    : BigDecimal.ZERO;

            CurrencyConversionService.ConversionResult priceConv = currencyConversionService
                    .convertForPurchase(approxInListing, listingCurrencyCode, accountCurrencyCode, chargeFx);
            BigDecimal exchangeRate = priceConv.midRate();
            BigDecimal approxInAccountCurrency = priceConv.amount();
            BigDecimal fxCommission = priceConv.commission();

            BigDecimal commissionInAccountCurrency;
            if (isEmployee) {
                commissionInAccountCurrency = BigDecimal.ZERO;
            } else {
                BigDecimal commissionInListing = calculateCommissionInListingCurrency(
                        approxInListing, order.getOrderType());
                CurrencyConversionService.ConversionResult commConv = currencyConversionService
                        .convertForPurchase(commissionInListing, listingCurrencyCode, accountCurrencyCode, chargeFx);
                commissionInAccountCurrency = commConv.amount();
                fxCommission = fxCommission.add(commConv.commission());
            }
            totalReservation = approxInAccountCurrency.add(commissionInAccountCurrency)
                    .setScale(4, RoundingMode.HALF_UP);

            if (account.getAvailableBalance().compareTo(totalReservation) < 0) {
                throw new InsufficientFundsException(
                        "Nedovoljno sredstava u trenutku odobravanja na racunu " + account.getAccountNumber());
            }

            order.setReservedAccountId(account.getId());
            order.setReservedAmount(totalReservation);
            order.setExchangeRate(exchangeRate);
            order.setFxCommission(fxCommission.compareTo(BigDecimal.ZERO) > 0 ? fxCommission : null);
            if (isEmployee) {
                order.setAccountId(account.getId());
            }
            fundReservationService.reserveForBuy(order, account);
        } else { // SELL

            Portfolio portfolio = portfolioRepository
                    .findByUserIdAndUserRoleAndListingIdForUpdate(order.getUserId(), order.getUserRole(), listing.getId())
                    .orElseThrow(() -> new InsufficientHoldingsException(
                            "Nemate ovu hartiju u portfoliju"));
            if (portfolio.getAvailableQuantity() < order.getQuantity()) {
                throw new InsufficientHoldingsException(
                        "Nedovoljno raspolozivih hartija. Dostupno: "
                                + portfolio.getAvailableQuantity() + ", traženo: " + order.getQuantity());
            }
            fundReservationService.reserveForSell(order, portfolio);
        }

        order.setStatus(OrderStatus.APPROVED);
        order.setApprovedBy(supervisorName);
        order.setApprovedAt(LocalDateTime.now());
        order.setLastModification(LocalDateTime.now());

        Order saved = orderRepository.save(order);

        // Update agent usedLimit when supervisor approves — koristimo totalReservation
        // (u valuti racuna) kao delta. Za SELL nema novcane rezervacije pa padamo na
        // approximatePrice fallback radi backward compat.
        if (isEmployee) {
            final BigDecimal limitDelta = totalReservation != null
                    ? totalReservation
                    : (order.getApproximatePrice() != null ? order.getApproximatePrice() : BigDecimal.ZERO);
            Optional<ActuaryInfo> actuaryOpt = orderStatusService.getAgentInfo(order.getUserId());
            actuaryOpt.ifPresent(actuary -> {
                if (actuary.getActuaryType() == ActuaryType.AGENT) {
                    BigDecimal current = actuary.getUsedLimit() != null ? actuary.getUsedLimit() : BigDecimal.ZERO;
                    actuary.setUsedLimit(current.add(limitDelta));
                    actuaryInfoRepository.save(actuary);
                }
            });
        }

        return toDtoWithUserName(saved);
    }

    @Override
    @Transactional
    public OrderDto declineOrder(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found " + orderId));

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.APPROVED) {
            throw new IllegalStateException("Only PENDING or APPROVED orders can be declined/cancelled");
        }

        String supervisorName = getSupervisorName();

        // Phase 5.2: Ako je order bio APPROVED, treba osloboditi rezervaciju
        // (novcanu za BUY, kolicinu hartija za SELL) + rollback agent usedLimit.
        boolean hadReservation = order.getStatus() == OrderStatus.APPROVED;
        if (hadReservation && !order.isReservationReleased()) {
            if (order.getDirection() == OrderDirection.BUY) {
                fundReservationService.releaseForBuy(order);
            } else { // SELL
                Portfolio portfolio = portfolioRepository
                        .findByUserIdAndUserRoleAndListingIdForUpdate(order.getUserId(), order.getUserRole(), order.getListing().getId())
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Portfolio ne postoji za order " + order.getId()));
                fundReservationService.releaseForSell(order, portfolio);
            }

            if (UserRole.isEmployee(order.getUserRole()) && order.getReservedAmount() != null) {
                Optional<ActuaryInfo> actuaryOpt = orderStatusService.getAgentInfo(order.getUserId());
                actuaryOpt.ifPresent(actuary -> {
                    if (actuary.getActuaryType() == ActuaryType.AGENT) {
                        BigDecimal current = actuary.getUsedLimit() != null ? actuary.getUsedLimit() : BigDecimal.ZERO;
                        BigDecimal rolledBack = current.subtract(order.getReservedAmount());
                        actuary.setUsedLimit(rolledBack.max(BigDecimal.ZERO));
                        actuaryInfoRepository.save(actuary);
                    }
                });
            }
        }

        order.setStatus(OrderStatus.DECLINED);
        order.setApprovedBy(supervisorName);
        order.setLastModification(LocalDateTime.now());

        Order saved = orderRepository.save(order);
        return toDtoWithUserName(saved);
    }

    @Override
    @Transactional
    public OrderDto cancelOrder(Long orderId, Integer quantityToCancel) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found " + orderId));

        int remaining = order.getRemainingPortions() != null
                ? order.getRemainingPortions()
                : (order.getQuantity() != null ? order.getQuantity() : 0);

        // Full cancel delegates to declineOrder which handles both PENDING
        // and APPROVED states (releases reservation + rollbackuje usedLimit).
        boolean fullCancel = quantityToCancel == null
                || quantityToCancel <= 0
                || quantityToCancel >= remaining
                || order.getStatus() == OrderStatus.PENDING
                || order.isDone();
        if (fullCancel) {
            return declineOrder(orderId);
        }

        if (order.getStatus() != OrderStatus.APPROVED) {
            throw new IllegalStateException(
                    "Parcijalni cancel je dozvoljen samo za APPROVED ordere.");
        }

        int cancelQty = quantityToCancel;
        int newRemaining = remaining - cancelQty;
        Integer originalQty = order.getQuantity();

        // Oslobodi pro-ratu rezervisanog sredstva / hartija
        if (order.getDirection() == OrderDirection.BUY
                && order.getReservedAccountId() != null
                && order.getReservedAmount() != null
                && originalQty != null && originalQty > 0) {
            Account reservedAcc = accountRepository.findForUpdateById(order.getReservedAccountId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Racun ne postoji: " + order.getReservedAccountId()));
            BigDecimal fraction = new BigDecimal(cancelQty)
                    .divide(new BigDecimal(originalQty), 10, RoundingMode.HALF_UP);
            BigDecimal releaseAmount = order.getReservedAmount().multiply(fraction)
                    .setScale(4, RoundingMode.HALF_UP);
            BigDecimal currentReserved = reservedAcc.getReservedAmount();
            if (releaseAmount.compareTo(currentReserved) > 0) {
                releaseAmount = currentReserved;
            }
            reservedAcc.setAvailableBalance(reservedAcc.getAvailableBalance().add(releaseAmount));
            reservedAcc.setReservedAmount(currentReserved.subtract(releaseAmount));
            accountRepository.save(reservedAcc);
        } else if (order.getDirection() == OrderDirection.SELL) {
            Portfolio portfolio = portfolioRepository
                    .findByUserIdAndUserRoleAndListingIdForUpdate(order.getUserId(), order.getUserRole(), order.getListing().getId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Portfolio ne postoji za order " + order.getId()));
            int releaseQty = Math.min(cancelQty, portfolio.getReservedQuantity());
            portfolio.setReservedQuantity(portfolio.getReservedQuantity() - releaseQty);
            portfolioRepository.save(portfolio);
        }

        // Rollback proporcionalnog usedLimit-a za AGENT-a
        if (order.getDirection() == OrderDirection.BUY
                && UserRole.isEmployee(order.getUserRole())
                && order.getReservedAmount() != null
                && originalQty != null && originalQty > 0) {
            Optional<ActuaryInfo> actuaryOpt = orderStatusService.getAgentInfo(order.getUserId());
            int cancelQtyFinal = cancelQty;
            actuaryOpt.ifPresent(actuary -> {
                if (actuary.getActuaryType() == ActuaryType.AGENT) {
                    BigDecimal fraction = new BigDecimal(cancelQtyFinal)
                            .divide(new BigDecimal(originalQty), 10, RoundingMode.HALF_UP);
                    BigDecimal rollback = order.getReservedAmount().multiply(fraction)
                            .setScale(4, RoundingMode.HALF_UP);
                    BigDecimal current = actuary.getUsedLimit() != null
                            ? actuary.getUsedLimit() : BigDecimal.ZERO;
                    actuary.setUsedLimit(current.subtract(rollback).max(BigDecimal.ZERO));
                    actuaryInfoRepository.save(actuary);
                }
            });
        }

        order.setRemainingPortions(newRemaining);
        order.setLastModification(LocalDateTime.now());
        order.setApprovedBy(getSupervisorName()); // audit trail ko je skratio order
        Order saved = orderRepository.save(order);
        return toDtoWithUserName(saved);
    }

    @Override
    public Page<OrderDto> getAllOrders(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) {
            return orderRepository.findAll(pageable).map(this::toDtoWithUserName);
        }

        OrderStatus orderStatus;
        try {
            orderStatus = OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + status +
                    ". Valid status: ALL, PENDING, APPROVED, DECLINED, DONE");
        }

        return orderRepository.findByStatus(orderStatus, pageable).map(this::toDtoWithUserName);
    }

    @Override
    public Page<OrderDto> getMyOrders(int page, int size) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Optional<Client> clientOpt = clientRepository.findByEmail(email);
        if (clientOpt.isPresent()) {
            return orderRepository.findByUserId(clientOpt.get().getId(), pageable).map(this::toDtoWithUserName);
        }

        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return orderRepository.findByUserId(employee.getId(), pageable).map(this::toDtoWithUserName);
    }
    @Override
    public OrderDto getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order with ID " + orderId + " not found"));

        boolean isSupervisor = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_EMPLOYEE"));

        if (isSupervisor) {
            return toDtoWithUserName(order);
        }

        Long currentUserId = resolveCurrentUser().userId();
        if (!order.getUserId().equals(currentUserId)) {
            throw new IllegalStateException("You dont have access to this account");
        }

        return toDtoWithUserName(order);
    }

    private UserContext resolveCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        Optional<Client> clientOpt = clientRepository.findByEmail(email);
        if (clientOpt.isPresent()) {
            return new UserContext(clientOpt.get().getId(), UserRole.CLIENT);
        }

        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        return new UserContext(employee.getId(), UserRole.EMPLOYEE);
    }

    private boolean computeAfterHours(Listing listing) {
        String exchange = listing.getExchangeAcronym();
        if (exchange == null) return false;

        try {
            return exchangeManagementService.isAfterHours(exchange);
        } catch (Exception e) {
            // Exchange not found or unknown — treat as not after-hours
            return false;
        }
    }
    private String getSupervisorName() {
        UserContext userContext = resolveCurrentUser();
        return employeeRepository.findById(userContext.userId())
                .map(e -> e.getFirstName() + " " + e.getLastName())
                .orElseThrow(() -> new IllegalStateException("Supervisor not found"));
    }

    private String resolveUserName(Long userId, String userRole) {
        if (UserRole.isClient(userRole)) {
            return clientRepository.findById(userId)
                    .map(c -> c.getFirstName() + " " + c.getLastName())
                    .orElse("Unknown");
        }
        return employeeRepository.findById(userId)
                .map(e -> e.getFirstName() + " " + e.getLastName())
                .orElse("Unknown");
    }

    private OrderDto toDtoWithUserName(Order order) {
        String userName = resolveUserName(order.getUserId(), order.getUserRole());
        return OrderMapper.toDto(order, userName);
    }
}
