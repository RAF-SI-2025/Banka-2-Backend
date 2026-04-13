package rs.raf.banka2_bek.order.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountCategory;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.berza.service.ExchangeManagementService;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.order.dto.CreateOrderDto;
import rs.raf.banka2_bek.order.dto.OrderDto;
import rs.raf.banka2_bek.order.exception.InsufficientHoldingsException;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.order.service.implementation.OrderServiceImpl;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dodatni testovi za {@link OrderServiceImpl} koji pokrivaju grane
 * koje nisu pokrili {@code OrderServiceImplTest}:
 *
 * - {@code resolveListingCurrency}: FOREX+baseCurrency, LSE, XETRA, BELEX, default, null acronym
 * - {@code calculateCommissionInListingCurrency}: LIMIT/STOP_LIMIT
 * - createOrder SELL sa account != null (exchangeRate racuna)
 * - {@code computeAfterHours} exception fallback
 * - approveOrder: CLIENT BUY sa accountId fallback na reservedAccountId,
 *                 CLIENT BUY sa null approximatePrice,
 *                 CLIENT BUY bez povezanog racuna (throw),
 *                 SELL insufficient quantity
 * - declineOrder: APPROVED SELL release + rollback employee usedLimit
 * - getOrderById: supervisor vidi tudji order
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplCoverageTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private ActuaryInfoRepository actuaryInfoRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private OrderValidationService orderValidationService;
    @Mock private ListingPriceService listingPriceService;
    @Mock private OrderStatusService orderStatusService;
    @Mock private ExchangeManagementService exchangeManagementService;
    @Mock private AccountRepository accountRepository;
    @Mock private FundReservationService fundReservationService;
    @Mock private BankTradingAccountResolver bankTradingAccountResolver;
    @Mock private CurrencyConversionService currencyConversionService;
    @Mock private PortfolioRepository portfolioRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Client testClient;
    private Employee testEmployee;
    private Account clientUsd;
    private Account bankUsd;
    private Portfolio testPortfolio;

    private Currency curr(String code) {
        Currency c = new Currency();
        c.setCode(code);
        return c;
    }

    @BeforeEach
    void setUp() {
        testClient = new Client();
        testClient.setId(42L);
        testClient.setEmail("client@test.com");
        testClient.setFirstName("Cli");
        testClient.setLastName("Ent");

        testEmployee = new Employee();
        testEmployee.setId(99L);
        testEmployee.setEmail("agent@test.com");
        testEmployee.setFirstName("Ag");
        testEmployee.setLastName("Ent");

        clientUsd = Account.builder()
                .id(100L)
                .accountNumber("111000000000000100")
                .currency(curr("USD"))
                .balance(new BigDecimal("10000.0000"))
                .availableBalance(new BigDecimal("10000.0000"))
                .reservedAmount(BigDecimal.ZERO)
                .accountCategory(AccountCategory.CLIENT)
                .build();
        bankUsd = Account.builder()
                .id(900L)
                .currency(curr("USD"))
                .balance(new BigDecimal("5000000"))
                .availableBalance(new BigDecimal("5000000"))
                .reservedAmount(BigDecimal.ZERO)
                .accountCategory(AccountCategory.BANK_TRADING)
                .accountNumber("999000000000000002")
                .build();

        testPortfolio = new Portfolio();
        testPortfolio.setId(500L);
        testPortfolio.setUserId(42L);
        testPortfolio.setListingId(1L);
        testPortfolio.setListingTicker("AAPL");
        testPortfolio.setListingType("STOCK");
        testPortfolio.setQuantity(30);
        testPortfolio.setReservedQuantity(0);
        testPortfolio.setPublicQuantity(0);
        testPortfolio.setAverageBuyPrice(new BigDecimal("140"));

        lenient().when(currencyConversionService.getRate(anyString(), anyString())).thenReturn(BigDecimal.ONE);
        lenient().when(currencyConversionService.convert(any(BigDecimal.class), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(bankTradingAccountResolver.resolve(anyString())).thenReturn(bankUsd);
        lenient().when(accountRepository.findForUpdateById(100L)).thenReturn(Optional.of(clientUsd));
        lenient().when(portfolioRepository.findByUserIdAndListingIdForUpdate(anyLong(), anyLong()))
                .thenReturn(Optional.of(testPortfolio));
        lenient().when(orderValidationService.parseOrderType(anyString())).thenReturn(OrderType.MARKET);
        lenient().when(orderValidationService.parseDirection(anyString())).thenReturn(OrderDirection.BUY);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authClient(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
        lenient().when(clientRepository.findByEmail(email)).thenReturn(Optional.of(testClient));
    }

    private void authEmployee(String email, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        email, null,
                        List.of(new SimpleGrantedAuthority(authority))));
        lenient().when(clientRepository.findByEmail(email)).thenReturn(Optional.empty());
        lenient().when(employeeRepository.findByEmail(email)).thenReturn(Optional.of(testEmployee));
        lenient().when(employeeRepository.findById(99L)).thenReturn(Optional.of(testEmployee));
    }

    private Listing listing(ListingType type, String acronym) {
        Listing l = new Listing();
        l.setId(1L);
        l.setTicker("AAPL");
        l.setName("Apple");
        l.setListingType(type);
        l.setPrice(new BigDecimal("150"));
        l.setAsk(new BigDecimal("151"));
        l.setBid(new BigDecimal("149"));
        l.setExchangeAcronym(acronym);
        return l;
    }

    private CreateOrderDto dtoMarketBuy() {
        CreateOrderDto d = new CreateOrderDto();
        d.setListingId(1L);
        d.setOrderType("MARKET");
        d.setDirection("BUY");
        d.setQuantity(5);
        d.setContractSize(1);
        d.setAccountId(100L);
        return d;
    }

    private CreateOrderDto dtoMarketSell() {
        CreateOrderDto d = dtoMarketBuy();
        d.setDirection("SELL");
        return d;
    }

    // ─── resolveListingCurrency grane ─────────────────────────────────────

    @Test
    @DisplayName("createOrder — FOREX listing sa baseCurrency koristi tu valutu")
    void forexListingUsesBaseCurrency() {
        authClient("client@test.com");
        Listing l = listing(ListingType.FOREX, "FX");
        l.setBaseCurrency("EUR");

        when(listingRepository.findById(1L)).thenReturn(Optional.of(l));
        when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
        when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755"));
        when(orderStatusService.determineStatus(anyString(), anyLong(), any())).thenReturn(OrderStatus.PENDING);
        when(orderRepository.save(any())).thenAnswer(inv -> { Order o = inv.getArgument(0); o.setId(1L); return o; });

        orderService.createOrder(dtoMarketBuy());

        verify(currencyConversionService).getRate("EUR", "USD");
    }

    @Test
    @DisplayName("createOrder — LSE exchange → GBP valuta")
    void lseExchangeUsesGbp() {
        authClient("client@test.com");
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing(ListingType.STOCK, "LSE")));
        when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
        when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755"));
        when(orderStatusService.determineStatus(anyString(), anyLong(), any())).thenReturn(OrderStatus.PENDING);
        when(orderRepository.save(any())).thenAnswer(inv -> { Order o = inv.getArgument(0); o.setId(1L); return o; });

        orderService.createOrder(dtoMarketBuy());

        verify(currencyConversionService).getRate("GBP", "USD");
    }

    @Test
    @DisplayName("createOrder — XETRA → EUR, BELEX → RSD, default → USD")
    void xetraBelexDefaultExchange() {
        // XETRA
        authClient("client@test.com");
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing(ListingType.STOCK, "XETRA")));
        when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("1"));
        when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("5"));
        when(orderStatusService.determineStatus(anyString(), anyLong(), any())).thenReturn(OrderStatus.PENDING);
        when(orderRepository.save(any())).thenAnswer(inv -> { Order o = inv.getArgument(0); o.setId(1L); return o; });
        orderService.createOrder(dtoMarketBuy());
        verify(currencyConversionService).getRate("EUR", "USD");

        // BELEX
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing(ListingType.STOCK, "BELEX")));
        orderService.createOrder(dtoMarketBuy());
        verify(currencyConversionService).getRate("RSD", "USD");

        // Unknown → default USD
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing(ListingType.STOCK, "UNKNOWN")));
        orderService.createOrder(dtoMarketBuy());
        // unknown → USD; racun je vec USD pa convert se poziva sa ("USD","USD")
        verify(currencyConversionService).getRate("USD", "USD");
    }

    @Test
    @DisplayName("createOrder — FOREX bez baseCurrency sa null acronym pada na USD")
    void forexNullAcronymFallsThroughToUsd() {
        authClient("client@test.com");
        Listing l = listing(ListingType.FOREX, null);
        l.setBaseCurrency(null); // must hit null acronym fallback

        when(listingRepository.findById(1L)).thenReturn(Optional.of(l));
        when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
        when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755"));
        when(orderStatusService.determineStatus(anyString(), anyLong(), any())).thenReturn(OrderStatus.PENDING);
        when(orderRepository.save(any())).thenAnswer(inv -> { Order o = inv.getArgument(0); o.setId(1L); return o; });

        orderService.createOrder(dtoMarketBuy());

        verify(currencyConversionService).getRate("USD", "USD");
    }

    // ─── calculateCommissionInListingCurrency: LIMIT grana ────────────────

    @Test
    @DisplayName("createOrder — LIMIT tip aktivira 24% commission granu")
    void limitOrderCommissionBranch() {
        authClient("client@test.com");
        when(orderValidationService.parseOrderType(anyString())).thenReturn(OrderType.LIMIT);
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing(ListingType.STOCK, "NASDAQ")));
        when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("10"));
        when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("50"));
        when(orderStatusService.determineStatus(anyString(), anyLong(), any())).thenReturn(OrderStatus.PENDING);
        when(orderRepository.save(any())).thenAnswer(inv -> { Order o = inv.getArgument(0); o.setId(1L); return o; });

        OrderDto result = orderService.createOrder(dtoMarketBuy());

        assertThat(result).isNotNull();
    }

    // ─── createOrder SELL sa account != null (L141) ───────────────────────

    @Test
    @DisplayName("createOrder — CLIENT SELL postavlja exchangeRate")
    void clientSellSetsExchangeRate() {
        authClient("client@test.com");
        when(orderValidationService.parseDirection(anyString())).thenReturn(OrderDirection.SELL);
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing(ListingType.STOCK, "NASDAQ")));
        when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
        when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755"));
        when(orderStatusService.determineStatus(anyString(), anyLong(), any())).thenReturn(OrderStatus.PENDING);
        when(orderRepository.save(any())).thenAnswer(inv -> { Order o = inv.getArgument(0); o.setId(1L); return o; });

        orderService.createOrder(dtoMarketSell());

        verify(currencyConversionService).getRate("USD", "USD");
    }

    // ─── computeAfterHours exception fallback (L501-503) ─────────────────

    @Test
    @DisplayName("createOrder — computeAfterHours exception = afterHours false")
    void computeAfterHoursExceptionFallback() {
        authClient("client@test.com");
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing(ListingType.STOCK, "NASDAQ")));
        when(exchangeManagementService.isAfterHours("NASDAQ"))
                .thenThrow(new RuntimeException("boom"));
        when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
        when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755"));
        when(orderStatusService.determineStatus(anyString(), anyLong(), any())).thenReturn(OrderStatus.PENDING);
        when(orderRepository.save(any())).thenAnswer(inv -> { Order o = inv.getArgument(0); o.setId(1L); return o; });

        OrderDto result = orderService.createOrder(dtoMarketBuy());

        assertThat(result).isNotNull();
    }

    // ─── approveOrder: CLIENT BUY grane ───────────────────────────────────

    private Order pendingClientBuy(Listing l) {
        Order o = new Order();
        o.setId(1L);
        o.setUserId(42L);
        o.setUserRole("CLIENT");
        o.setListing(l);
        o.setDirection(OrderDirection.BUY);
        o.setOrderType(OrderType.MARKET);
        o.setStatus(OrderStatus.PENDING);
        o.setQuantity(5);
        o.setContractSize(1);
        o.setApproximatePrice(new BigDecimal("755"));
        o.setAccountId(100L);
        o.setRemainingPortions(5);
        return o;
    }

    @Test
    @DisplayName("approveOrder — CLIENT BUY rezervise sredstva i obracunava proviziju")
    void approveClientBuyCalculatesCommission() {
        authEmployee("agent@test.com", "ROLE_ADMIN");
        Listing l = listing(ListingType.STOCK, "NASDAQ");
        Order o = pendingClientBuy(l);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderDto result = orderService.approveOrder(1L);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        verify(fundReservationService).reserveForBuy(any(), any());
        // CLIENT → commission grana se izvrsava
        verify(currencyConversionService, org.mockito.Mockito.atLeastOnce())
                .convert(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("approveOrder — CLIENT BUY fallback na reservedAccountId kad je accountId null")
    void approveClientBuyFallbackAccount() {
        authEmployee("agent@test.com", "ROLE_ADMIN");
        Listing l = listing(ListingType.STOCK, "NASDAQ");
        Order o = pendingClientBuy(l);
        o.setAccountId(null);
        o.setReservedAccountId(100L);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderDto result = orderService.approveOrder(1L);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("approveOrder — CLIENT BUY bez racuna baca EntityNotFoundException")
    void approveClientBuyNoAccount() {
        authEmployee("agent@test.com", "ROLE_ADMIN");
        Listing l = listing(ListingType.STOCK, "NASDAQ");
        Order o = pendingClientBuy(l);
        o.setAccountId(null);
        o.setReservedAccountId(null);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> orderService.approveOrder(1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("povezan racun");
    }

    @Test
    @DisplayName("approveOrder — CLIENT BUY sa null approximatePrice (koristi ZERO)")
    void approveClientBuyNullApproxPrice() {
        authEmployee("agent@test.com", "ROLE_ADMIN");
        Listing l = listing(ListingType.STOCK, "NASDAQ");
        Order o = pendingClientBuy(l);
        o.setApproximatePrice(null);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderDto result = orderService.approveOrder(1L);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("approveOrder — CLIENT BUY racun ne postoji")
    void approveClientBuyAccountMissing() {
        authEmployee("agent@test.com", "ROLE_ADMIN");
        Listing l = listing(ListingType.STOCK, "NASDAQ");
        Order o = pendingClientBuy(l);
        o.setAccountId(777L);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        when(accountRepository.findForUpdateById(777L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.approveOrder(1L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("approveOrder — SELL nedovoljno hartija baca InsufficientHoldings")
    void approveSellInsufficientHoldings() {
        authEmployee("agent@test.com", "ROLE_ADMIN");
        Listing l = listing(ListingType.STOCK, "NASDAQ");
        Order o = pendingClientBuy(l);
        o.setDirection(OrderDirection.SELL);
        o.setQuantity(100); // more than available

        Portfolio pf = new Portfolio();
        pf.setUserId(42L);
        pf.setListingId(1L);
        pf.setQuantity(10);
        pf.setReservedQuantity(0);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        when(portfolioRepository.findByUserIdAndListingIdForUpdate(42L, 1L))
                .thenReturn(Optional.of(pf));

        assertThatThrownBy(() -> orderService.approveOrder(1L))
                .isInstanceOf(InsufficientHoldingsException.class);
    }

    // ─── declineOrder: APPROVED SELL + employee rollback ───────────────────

    @Test
    @DisplayName("declineOrder — APPROVED SELL od klijenta oslobadja portfolio rezervaciju")
    void declineApprovedSellClient() {
        authEmployee("agent@test.com", "ROLE_ADMIN");
        Listing l = listing(ListingType.STOCK, "NASDAQ");
        Order o = pendingClientBuy(l);
        o.setDirection(OrderDirection.SELL);
        o.setStatus(OrderStatus.APPROVED);
        o.setReservationReleased(false);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        when(portfolioRepository.findByUserIdAndListingIdForUpdate(42L, 1L))
                .thenReturn(Optional.of(testPortfolio));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderDto result = orderService.declineOrder(1L);

        assertThat(result.getStatus()).isEqualTo("DECLINED");
        verify(fundReservationService).releaseForSell(any(), any());
    }

    @Test
    @DisplayName("declineOrder — APPROVED SELL portfolio ne postoji = throw")
    void declineApprovedSellPortfolioMissing() {
        authEmployee("agent@test.com", "ROLE_ADMIN");
        Listing l = listing(ListingType.STOCK, "NASDAQ");
        Order o = pendingClientBuy(l);
        o.setDirection(OrderDirection.SELL);
        o.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        when(portfolioRepository.findByUserIdAndListingIdForUpdate(42L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.declineOrder(1L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("declineOrder — APPROVED BUY employee rollback usedLimit (nenegativno)")
    void declineEmployeeRollbackUsedLimit() {
        authEmployee("agent@test.com", "ROLE_ADMIN");
        Listing l = listing(ListingType.STOCK, "NASDAQ");
        Order o = pendingClientBuy(l);
        o.setUserRole("EMPLOYEE");
        o.setUserId(99L);
        o.setStatus(OrderStatus.APPROVED);
        o.setReservedAmount(new BigDecimal("500"));

        ActuaryInfo info = new ActuaryInfo();
        info.setActuaryType(ActuaryType.AGENT);
        info.setUsedLimit(new BigDecimal("1000"));

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        when(orderStatusService.getAgentInfo(99L)).thenReturn(Optional.of(info));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.declineOrder(1L);

        assertThat(info.getUsedLimit()).isEqualByComparingTo("500");
        verify(actuaryInfoRepository).save(info);
    }

    @Test
    @DisplayName("declineOrder — APPROVED employee SUPERVISOR ne rolluje limit")
    void declineEmployeeSupervisorNoRollback() {
        authEmployee("agent@test.com", "ROLE_ADMIN");
        Listing l = listing(ListingType.STOCK, "NASDAQ");
        Order o = pendingClientBuy(l);
        o.setUserRole("EMPLOYEE");
        o.setUserId(99L);
        o.setStatus(OrderStatus.APPROVED);
        o.setReservedAmount(new BigDecimal("500"));

        ActuaryInfo info = new ActuaryInfo();
        info.setActuaryType(ActuaryType.SUPERVISOR);
        info.setUsedLimit(new BigDecimal("1000"));

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        when(orderStatusService.getAgentInfo(99L)).thenReturn(Optional.of(info));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.declineOrder(1L);

        // Supervisor nije AGENT — limit ostaje
        assertThat(info.getUsedLimit()).isEqualByComparingTo("1000");
        verify(actuaryInfoRepository, never()).save(any());
    }

    @Test
    @DisplayName("declineOrder — APPROVED sa reservationReleased=true preskace release")
    void declineAlreadyReleased() {
        authEmployee("agent@test.com", "ROLE_ADMIN");
        Listing l = listing(ListingType.STOCK, "NASDAQ");
        Order o = pendingClientBuy(l);
        o.setStatus(OrderStatus.APPROVED);
        o.setReservationReleased(true);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderDto result = orderService.declineOrder(1L);

        assertThat(result.getStatus()).isEqualTo("DECLINED");
        verify(fundReservationService, never()).releaseForBuy(any());
    }

    // ─── approveOrder employee usedLimit — AGENT sa null usedLimit ────────

    @Test
    @DisplayName("approveOrder — EMPLOYEE agent sa null usedLimit startuje sa 0")
    void approveEmployeeAgentNullUsedLimit() {
        authEmployee("agent@test.com", "ROLE_ADMIN");
        Listing l = listing(ListingType.STOCK, "NASDAQ");
        Order o = pendingClientBuy(l);
        o.setUserRole("EMPLOYEE");
        o.setUserId(99L);

        ActuaryInfo info = new ActuaryInfo();
        info.setActuaryType(ActuaryType.AGENT);
        info.setUsedLimit(null);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        when(orderStatusService.getAgentInfo(99L)).thenReturn(Optional.of(info));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.approveOrder(1L);

        assertThat(info.getUsedLimit()).isNotNull();
        assertThat(info.getUsedLimit().compareTo(BigDecimal.ZERO)).isGreaterThan(0);
    }

    // ─── getOrderById: ROLE_ADMIN supervisor vidi tudji order ────────────

    @Test
    @DisplayName("getOrderById — korisnik sa ROLE_ADMIN moze da vidi tudji order")
    void getOrderByIdSupervisorSeesOthers() {
        authEmployee("agent@test.com", "ROLE_ADMIN");
        Order o = new Order();
        o.setId(10L);
        o.setUserId(7777L); // ne trenutni
        o.setUserRole("CLIENT");
        o.setListing(listing(ListingType.STOCK, "NASDAQ"));
        o.setDirection(OrderDirection.BUY);
        o.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findById(10L)).thenReturn(Optional.of(o));

        OrderDto result = orderService.getOrderById(10L);

        assertThat(result).isNotNull();
    }
}
