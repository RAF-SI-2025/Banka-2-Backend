package rs.raf.banka2_bek.order.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountCategory;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.order.dto.CreateOrderDto;
import rs.raf.banka2_bek.order.dto.OrderDto;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.order.service.implementation.OrderServiceImpl;
import rs.raf.banka2_bek.berza.service.ExchangeManagementService;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("OrderServiceImpl — createOrder")
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private ActuaryInfoRepository actuaryInfoRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private OrderValidationService orderValidationService;
    @Mock private ListingPriceService listingPriceService;
    @Mock private FundsVerificationService fundsVerificationService;
    @Mock private OrderStatusService orderStatusService;
    @Mock private ExchangeManagementService exchangeManagementService;
    @Mock private AccountRepository accountRepository;
    @Mock private FundReservationService fundReservationService;
    @Mock private BankTradingAccountResolver bankTradingAccountResolver;
    @Mock private CurrencyConversionService currencyConversionService;
    @Mock private PortfolioRepository portfolioRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Listing testListing;
    private Client testClient;
    private Employee testEmployee;
    private Account testClientAccount;
    private Account testBankUsdAccount;
    private Portfolio testPortfolio;

    private Currency usd() {
        Currency c = new Currency();
        c.setCode("USD");
        return c;
    }

    private Currency rsd() {
        Currency c = new Currency();
        c.setCode("RSD");
        return c;
    }

    @BeforeEach
    void setUp() {
        testListing = new Listing();
        testListing.setId(1L);
        testListing.setTicker("AAPL");
        testListing.setName("Apple Inc.");
        testListing.setListingType(ListingType.STOCK);
        testListing.setPrice(new BigDecimal("150"));
        testListing.setAsk(new BigDecimal("151"));
        testListing.setBid(new BigDecimal("149"));
        testListing.setExchangeAcronym("NASDAQ");

        testClient = new Client();
        testClient.setId(42L);
        testClient.setEmail("client@test.com");

        testEmployee = new Employee();
        testEmployee.setId(99L);
        testEmployee.setEmail("agent@test.com");

        testClientAccount = Account.builder()
                .id(100L)
                .accountNumber("111000000000000100")
                .currency(usd())
                .balance(new BigDecimal("10000.0000"))
                .availableBalance(new BigDecimal("10000.0000"))
                .reservedAmount(BigDecimal.ZERO)
                .accountCategory(AccountCategory.CLIENT)
                .build();

        testBankUsdAccount = Account.builder()
                .id(900L)
                .accountNumber("999000000000000002")
                .currency(usd())
                .balance(new BigDecimal("5000000.0000"))
                .availableBalance(new BigDecimal("5000000.0000"))
                .reservedAmount(BigDecimal.ZERO)
                .accountCategory(AccountCategory.BANK_TRADING)
                .build();

        testPortfolio = new Portfolio();
        testPortfolio.setId(500L);
        testPortfolio.setUserId(42L);
        testPortfolio.setListingId(1L);
        testPortfolio.setListingTicker("AAPL");
        testPortfolio.setListingName("Apple Inc.");
        testPortfolio.setListingType("STOCK");
        testPortfolio.setQuantity(30);
        testPortfolio.setReservedQuantity(0);
        testPortfolio.setPublicQuantity(0);
        testPortfolio.setAverageBuyPrice(new BigDecimal("140.0000"));

        // Default stubovi za Phase 3 servise — strictness=LENIENT pa ne smetaju
        // testovima koji ne idu kroz createOrder.
        lenient().when(accountRepository.findForUpdateById(100L)).thenReturn(Optional.of(testClientAccount));
        lenient().when(bankTradingAccountResolver.resolve(anyString())).thenReturn(testBankUsdAccount);
        lenient().when(portfolioRepository.findByUserIdAndListingIdForUpdate(anyLong(), anyLong()))
                .thenReturn(Optional.of(testPortfolio));
        // Role-aware metoda delegira na staru kad test-level stub ne kaze drugacije.
        lenient().when(portfolioRepository.findByUserIdAndUserRoleAndListingIdForUpdate(anyLong(), anyString(), anyLong()))
                .thenAnswer(inv -> portfolioRepository.findByUserIdAndListingIdForUpdate(inv.getArgument(0), inv.getArgument(2)));
        lenient().when(currencyConversionService.getRate(anyString(), anyString())).thenReturn(BigDecimal.ONE);
        lenient().when(currencyConversionService.convert(any(BigDecimal.class), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(currencyConversionService.convertForPurchase(
                any(BigDecimal.class), anyString(), anyString(), anyBoolean()))
                .thenAnswer(inv -> new CurrencyConversionService.ConversionResult(
                        inv.getArgument(0), BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE));
        // Validation service mora vratiti non-null enum vrednosti inace direction/orderType
        // branching u createOrder preskace BUY/SELL granu i reservedAccountId ostaje null.
        lenient().when(orderValidationService.parseOrderType(anyString())).thenReturn(OrderType.MARKET);
        lenient().when(orderValidationService.parseDirection(anyString())).thenReturn(OrderDirection.BUY);
    }

    private void mockSecurityContext(String email) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(email);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private CreateOrderDto validMarketBuyDto() {
        CreateOrderDto dto = new CreateOrderDto();
        dto.setListingId(1L);
        dto.setOrderType("MARKET");
        dto.setDirection("BUY");
        dto.setQuantity(5);
        dto.setContractSize(1);
        dto.setAccountId(100L);
        return dto;
    }

    private Order savedOrder(CreateOrderDto dto, Listing listing, OrderStatus status) {
        Order order = new Order();
        order.setId(1L);
        order.setListing(listing);
        order.setOrderType(OrderType.MARKET);
        order.setDirection(OrderDirection.BUY);
        order.setQuantity(dto.getQuantity());
        order.setContractSize(dto.getContractSize());
        order.setPricePerUnit(new BigDecimal("151"));
        order.setApproximatePrice(new BigDecimal("755.0000"));
        order.setStatus(status);
        order.setApprovedBy(status == OrderStatus.APPROVED ? "No need for approval" : null);
        order.setAllOrNone(false);
        order.setMargin(false);
        order.setAfterHours(false);
        order.setDone(false);
        order.setRemainingPortions(dto.getQuantity());
        return order;
    }

    @Nested
    @DisplayName("CLIENT kreiranje ordera")
    class ClientCreateOrder {

        @Test
        @DisplayName("CLIENT MARKET BUY → status APPROVED, approvedBy='No need for approval'")
        void clientMarketBuyApproved() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("client@test.com");

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));
            when(orderStatusService.determineStatus("CLIENT", 42L, new BigDecimal("755.0000"))).thenReturn(OrderStatus.APPROVED);
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(1L);
                return o;
            });

            OrderDto result = orderService.createOrder(dto);

            assertNotNull(result);
            assertEquals("APPROVED", result.getStatus());
            assertEquals("No need for approval", result.getApprovedBy());
            assertEquals("CLIENT", result.getUserRole());

            verify(orderRepository).save(any(Order.class));
            // CLIENT → usedLimit se NE ažurira
            verify(actuaryInfoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("AGENT kreiranje ordera")
    class AgentCreateOrder {

        @Test
        @DisplayName("AGENT — APPROVED → usedLimit se ažurira")
        void agentApprovedUpdatesUsedLimit() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("agent@test.com");

            ActuaryInfo agentInfo = new ActuaryInfo();
            agentInfo.setActuaryType(ActuaryType.AGENT);
            agentInfo.setUsedLimit(new BigDecimal("1000"));
            agentInfo.setDailyLimit(new BigDecimal("10000"));
            agentInfo.setNeedApproval(false);

            when(clientRepository.findByEmail("agent@test.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("agent@test.com")).thenReturn(Optional.of(testEmployee));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));
            when(orderStatusService.determineStatus("EMPLOYEE", 99L, new BigDecimal("755.0000"))).thenReturn(OrderStatus.APPROVED);
            when(orderStatusService.getAgentInfo(99L)).thenReturn(Optional.of(agentInfo));
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(1L);
                return o;
            });

            OrderDto result = orderService.createOrder(dto);

            assertEquals("APPROVED", result.getStatus());
            // Proveri da je usedLimit ažuriran
            verify(actuaryInfoRepository).save(agentInfo);
            assertEquals(new BigDecimal("1755.0000"), agentInfo.getUsedLimit());
        }

        @Test
        @DisplayName("AGENT — PENDING → usedLimit se NE ažurira")
        void agentPendingDoesNotUpdateUsedLimit() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("agent@test.com");

            when(clientRepository.findByEmail("agent@test.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("agent@test.com")).thenReturn(Optional.of(testEmployee));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));
            when(orderStatusService.determineStatus("EMPLOYEE", 99L, new BigDecimal("755.0000"))).thenReturn(OrderStatus.PENDING);
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(1L);
                return o;
            });

            OrderDto result = orderService.createOrder(dto);

            assertEquals("PENDING", result.getStatus());
            assertNull(result.getApprovedBy());
            verify(actuaryInfoRepository, never()).save(any());
        }

        @Test
        @DisplayName("AGENT APPROVED koji je SUPERVISOR — usedLimit se NE ažurira")
        void supervisorApprovedDoesNotUpdateUsedLimit() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("agent@test.com");

            ActuaryInfo supervisorInfo = new ActuaryInfo();
            supervisorInfo.setActuaryType(ActuaryType.SUPERVISOR);

            when(clientRepository.findByEmail("agent@test.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("agent@test.com")).thenReturn(Optional.of(testEmployee));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));
            when(orderStatusService.determineStatus("EMPLOYEE", 99L, new BigDecimal("755.0000"))).thenReturn(OrderStatus.APPROVED);
            when(orderStatusService.getAgentInfo(99L)).thenReturn(Optional.of(supervisorInfo));
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(1L);
                return o;
            });

            orderService.createOrder(dto);

            // Supervisor nije AGENT, ne ažuriramo usedLimit
            verify(actuaryInfoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Greške")
    class ErrorCases {

        @Test
        @DisplayName("Listing ne postoji → EntityNotFoundException")
        void listingNotFound() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("client@test.com");
            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(listingRepository.findById(1L)).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> orderService.createOrder(dto));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Validacija baca grešku → ne nastavlja se")
        void validationFailurePropagates() {
            CreateOrderDto dto = validMarketBuyDto();
            doThrow(new IllegalArgumentException("Invalid order type or direction"))
                    .when(orderValidationService).validate(dto);

            assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(dto));
            verify(listingRepository, never()).findById(any());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("availableBalance nedovoljan → InsufficientFundsException, order se ne čuva")
        void insufficientAvailableBalancePropagates() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("client@test.com");

            // postavi racun sa premalo raspolozivih sredstava
            testClientAccount.setAvailableBalance(new BigDecimal("100.0000"));

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));

            rs.raf.banka2_bek.order.exception.InsufficientFundsException ex = assertThrows(
                    rs.raf.banka2_bek.order.exception.InsufficientFundsException.class,
                    () -> orderService.createOrder(dto));
            assertTrue(ex.getMessage().contains("Nedovoljno"));
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Sistemska polja ordera")
    class SystemFields {

        @Test
        @DisplayName("Order se čuva sa isDone=false, remainingPortions=quantity, createdAt != null")
        void systemFieldsSetCorrectly() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("client@test.com");

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));
            when(orderStatusService.determineStatus(any(), any(), any())).thenReturn(OrderStatus.APPROVED);
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(1L);
                return o;
            });

            orderService.createOrder(dto);

            verify(orderRepository).save(argThat(order ->
                    !order.isDone() &&
                            order.getRemainingPortions().equals(dto.getQuantity()) &&
                            order.getCreatedAt() != null &&
                            order.getLastModification() != null
            ));
        }

        @Test
        @DisplayName("userId i userRole se ispravno postavljaju za CLIENT")
        void userIdAndRoleSetForClient() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("client@test.com");

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));
            when(orderStatusService.determineStatus(any(), any(), any())).thenReturn(OrderStatus.APPROVED);
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(1L);
                return o;
            });

            orderService.createOrder(dto);

            verify(orderRepository).save(argThat(order ->
                    order.getUserId().equals(42L) &&
                            "CLIENT".equals(order.getUserRole())
            ));
        }



    }

    @Nested
    @DisplayName("Phase 3 — reservation + bank trading account + currency conversion")
    class Phase3ReservationFlow {

        @Test
        @DisplayName("CLIENT BUY — rezervise sredstva, APPROVED, userRole=CLIENT")
        void createOrder_clientBuy_reservesFunds_statusApproved_userRoleClient() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("client@test.com");

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));
            when(orderStatusService.determineStatus(eq("CLIENT"), eq(42L), any())).thenReturn(OrderStatus.APPROVED);
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(777L);
                return o;
            });

            orderService.createOrder(dto);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            Order saved = captor.getValue();
            assertEquals(OrderStatus.APPROVED, saved.getStatus());
            assertEquals("CLIENT", saved.getUserRole());
            assertEquals(testClientAccount.getId(), saved.getReservedAccountId());
            assertNotNull(saved.getReservedAmount());
            assertTrue(saved.getReservedAmount().compareTo(BigDecimal.ZERO) > 0);
            assertNotNull(saved.getApprovedAt());

            // Rezervacija mora biti pozvana
            verify(fundReservationService).reserveForBuy(any(Order.class), eq(testClientAccount));
        }

        @Test
        @DisplayName("AGENT BUY — koristi bankin trading racun, provizija=0, userRole=EMPLOYEE")
        void createOrder_agentBuy_usesBankTradingAccount_commissionZero() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setAccountId(null); // agent bez eksplicitnog accountId → resolver
            mockSecurityContext("agent@test.com");

            ActuaryInfo agentInfo = new ActuaryInfo();
            agentInfo.setActuaryType(ActuaryType.AGENT);
            agentInfo.setUsedLimit(BigDecimal.ZERO);
            agentInfo.setDailyLimit(new BigDecimal("100000"));
            agentInfo.setNeedApproval(false);

            when(clientRepository.findByEmail("agent@test.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("agent@test.com")).thenReturn(Optional.of(testEmployee));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));
            when(orderStatusService.determineStatus(eq("EMPLOYEE"), eq(99L), any())).thenReturn(OrderStatus.APPROVED);
            when(orderStatusService.getAgentInfo(99L)).thenReturn(Optional.of(agentInfo));
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(888L);
                return o;
            });

            orderService.createOrder(dto);

            // Resolver je pozvan sa USD (NASDAQ exchange)
            verify(bankTradingAccountResolver).resolve("USD");

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            Order saved = captor.getValue();
            assertEquals("EMPLOYEE", saved.getUserRole());
            assertEquals(testBankUsdAccount.getId(), saved.getReservedAccountId());
            // Provizija = 0 za employee: reservedAmount == approximatePrice (bez commission-a)
            assertEquals(0, new BigDecimal("755.0000").compareTo(saved.getReservedAmount()));

            // Klijentski racunovi ne smeju biti dodirnuti
            verify(accountRepository, never()).findForUpdateById(any());
        }

        @Test
        @DisplayName("CLIENT BUY — konverzija USD → RSD, order.exchangeRate se cuva")
        void createOrder_clientBuy_convertsCurrency_fromUsdToRsd() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("client@test.com");

            // Klijent ima RSD racun sa dovoljno sredstava
            Account rsdAccount = Account.builder()
                    .id(101L)
                    .accountNumber("111000000000000101")
                    .currency(rsd())
                    .balance(new BigDecimal("10000000.0000"))
                    .availableBalance(new BigDecimal("10000000.0000"))
                    .reservedAmount(BigDecimal.ZERO)
                    .accountCategory(AccountCategory.CLIENT)
                    .build();
            dto.setAccountId(101L);

            when(accountRepository.findForUpdateById(101L)).thenReturn(Optional.of(rsdAccount));
            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));
            when(currencyConversionService.getRate("USD", "RSD")).thenReturn(new BigDecimal("109.20"));
            when(currencyConversionService.convert(any(BigDecimal.class), eq("USD"), eq("RSD")))
                    .thenAnswer(inv -> {
                        BigDecimal amt = inv.getArgument(0);
                        return amt.multiply(new BigDecimal("109.20")).setScale(4, java.math.RoundingMode.HALF_UP);
                    });
            when(currencyConversionService.convertForPurchase(
                    any(BigDecimal.class), eq("USD"), eq("RSD"), anyBoolean()))
                    .thenAnswer(inv -> {
                        BigDecimal amt = inv.getArgument(0);
                        boolean charge = inv.getArgument(3);
                        BigDecimal mid = new BigDecimal("109.20");
                        BigDecimal midAmount = amt.multiply(mid).setScale(4, java.math.RoundingMode.HALF_UP);
                        if (!charge) {
                            return new CurrencyConversionService.ConversionResult(
                                    midAmount, BigDecimal.ZERO, mid, mid);
                        }
                        BigDecimal gross = midAmount.multiply(new BigDecimal("1.01"))
                                .setScale(4, java.math.RoundingMode.HALF_UP);
                        return new CurrencyConversionService.ConversionResult(
                                gross, gross.subtract(midAmount).setScale(4, java.math.RoundingMode.HALF_UP), mid, mid);
                    });
            when(orderStatusService.determineStatus(any(), any(), any())).thenReturn(OrderStatus.APPROVED);
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(555L);
                return o;
            });

            orderService.createOrder(dto);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            Order saved = captor.getValue();
            assertEquals(0, new BigDecimal("109.20").compareTo(saved.getExchangeRate()));
            // reservedAmount je u RSD (mnogo vece od 755 USD)
            assertTrue(saved.getReservedAmount().compareTo(new BigDecimal("80000")) > 0);
        }

        @Test
        @DisplayName("CLIENT BUY — premalo availableBalance → InsufficientFundsException")
        void createOrder_clientBuy_throwsInsufficientFunds_whenAvailableBalanceTooLow() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("client@test.com");

            testClientAccount.setAvailableBalance(new BigDecimal("50.0000"));

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));

            assertThrows(rs.raf.banka2_bek.order.exception.InsufficientFundsException.class,
                    () -> orderService.createOrder(dto));

            verify(orderRepository, never()).save(any());
            verify(fundReservationService, never()).reserveForBuy(any(), any());
        }
    }

    @Nested
    @DisplayName("Phase 4 — SELL flow sa portfolio rezervacijom")
    class Phase4SellFlow {

        private CreateOrderDto validMarketSellDto() {
            CreateOrderDto dto = new CreateOrderDto();
            dto.setListingId(1L);
            dto.setOrderType("MARKET");
            dto.setDirection("SELL");
            dto.setQuantity(5);
            dto.setContractSize(1);
            dto.setAccountId(100L);
            return dto;
        }

        @Test
        @DisplayName("CLIENT SELL — rezervise kolicinu u portfoliu, status APPROVED")
        void createOrder_clientSell_reservesPortfolioQuantity_statusApproved() {
            CreateOrderDto dto = validMarketSellDto();
            mockSecurityContext("client@test.com");

            when(orderValidationService.parseDirection(anyString())).thenReturn(OrderDirection.SELL);
            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("149"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("745.0000"));
            when(orderStatusService.determineStatus(eq("CLIENT"), eq(42L), any())).thenReturn(OrderStatus.APPROVED);
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(1001L);
                return o;
            });

            orderService.createOrder(dto);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            Order saved = captor.getValue();
            assertEquals(OrderStatus.APPROVED, saved.getStatus());
            assertEquals(OrderDirection.SELL, saved.getDirection());
            assertEquals(testClientAccount.getId(), saved.getReservedAccountId());

            verify(fundReservationService).reserveForSell(any(Order.class), eq(testPortfolio));
            // BUY rezervacija ne sme biti pozvana
            verify(fundReservationService, never()).reserveForBuy(any(), any());
        }

        @Test
        @DisplayName("CLIENT SELL — odbija kolicinu 27 kada je dostupno samo 3 (reservedQuantity=27)")
        void createOrder_clientSell_rejects27_whenOnlyThreeAvailable() {
            CreateOrderDto dto = validMarketSellDto();
            dto.setQuantity(27);
            mockSecurityContext("client@test.com");

            // Portfolio ima 30 total, ali 27 je vec rezervisano → dostupno 3
            testPortfolio.setReservedQuantity(27);

            when(orderValidationService.parseDirection(anyString())).thenReturn(OrderDirection.SELL);
            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("149"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("4023.0000"));

            rs.raf.banka2_bek.order.exception.InsufficientHoldingsException ex = assertThrows(
                    rs.raf.banka2_bek.order.exception.InsufficientHoldingsException.class,
                    () -> orderService.createOrder(dto));
            assertTrue(ex.getMessage().contains("Nedovoljno"));

            verify(orderRepository, never()).save(any());
            verify(fundReservationService, never()).reserveForSell(any(), any());
        }

        @Test
        @DisplayName("CLIENT SELL — baca InsufficientHoldings kada portfolio ne postoji")
        void createOrder_clientSell_throwsWhenNoPortfolio() {
            CreateOrderDto dto = validMarketSellDto();
            mockSecurityContext("client@test.com");

            when(orderValidationService.parseDirection(anyString())).thenReturn(OrderDirection.SELL);
            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("149"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("745.0000"));
            when(portfolioRepository.findByUserIdAndListingIdForUpdate(42L, 1L)).thenReturn(Optional.empty());
            when(portfolioRepository.findByUserIdAndUserRoleAndListingIdForUpdate(42L, "CLIENT", 1L)).thenReturn(Optional.empty());

            assertThrows(rs.raf.banka2_bek.order.exception.InsufficientHoldingsException.class,
                    () -> orderService.createOrder(dto));

            verify(orderRepository, never()).save(any());
            verify(fundReservationService, never()).reserveForSell(any(), any());
        }

        @Test
        @DisplayName("AGENT SELL — koristi bankin receiving racun, portfolio rezervacija ide")
        void createOrder_agentSell_usesBankTradingReceivingAccount() {
            CreateOrderDto dto = validMarketSellDto();
            dto.setAccountId(null); // agent bez eksplicitnog accountId → resolver
            mockSecurityContext("agent@test.com");

            // Agent portfolio — premapiramo na employee userId=99
            Portfolio agentPortfolio = new Portfolio();
            agentPortfolio.setId(501L);
            agentPortfolio.setUserId(99L);
            agentPortfolio.setListingId(1L);
            agentPortfolio.setListingTicker("AAPL");
            agentPortfolio.setListingName("Apple Inc.");
            agentPortfolio.setListingType("STOCK");
            agentPortfolio.setQuantity(20);
            agentPortfolio.setReservedQuantity(0);
            agentPortfolio.setPublicQuantity(0);
            agentPortfolio.setAverageBuyPrice(new BigDecimal("140.0000"));

            ActuaryInfo agentInfo = new ActuaryInfo();
            agentInfo.setActuaryType(ActuaryType.AGENT);
            agentInfo.setUsedLimit(BigDecimal.ZERO);
            agentInfo.setDailyLimit(new BigDecimal("100000"));
            agentInfo.setNeedApproval(false);

            when(orderValidationService.parseDirection(anyString())).thenReturn(OrderDirection.SELL);
            when(clientRepository.findByEmail("agent@test.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("agent@test.com")).thenReturn(Optional.of(testEmployee));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("149"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("745.0000"));
            when(portfolioRepository.findByUserIdAndListingIdForUpdate(99L, 1L))
                    .thenReturn(Optional.of(agentPortfolio));
            when(portfolioRepository.findByUserIdAndUserRoleAndListingIdForUpdate(99L, "EMPLOYEE", 1L))
                    .thenReturn(Optional.of(agentPortfolio));
            when(orderStatusService.determineStatus(eq("EMPLOYEE"), eq(99L), any())).thenReturn(OrderStatus.APPROVED);
            when(orderStatusService.getAgentInfo(99L)).thenReturn(Optional.of(agentInfo));
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(1002L);
                return o;
            });

            orderService.createOrder(dto);

            // Bank trading USD racun je resolvovan za NASDAQ listing
            verify(bankTradingAccountResolver).resolve("USD");

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            Order saved = captor.getValue();
            assertEquals("EMPLOYEE", saved.getUserRole());
            assertEquals(OrderDirection.SELL, saved.getDirection());
            // Bank account se koristi kao receiving — i accountId i reservedAccountId
            assertEquals(testBankUsdAccount.getId(), saved.getReservedAccountId());
            assertEquals(testBankUsdAccount.getId(), saved.getAccountId());

            // Agent portfolio se rezervise
            verify(fundReservationService).reserveForSell(any(Order.class), eq(agentPortfolio));
            // Klijentski racuni NE smeju biti ucitani za agenta
            verify(accountRepository, never()).findForUpdateById(any());
        }
    }

    @Nested
    @DisplayName("Odobravanje ordera")
    class ApproveOrder {

        private Order pendingOrder;
        private Employee supervisor;
        private Listing listing;

        @BeforeEach
        void setUp() {
            listing = new Listing();
            listing.setId(1L);
            listing.setTicker("AAPL");
            listing.setListingType(ListingType.STOCK);
            listing.setSettlementDate(null);

            supervisor = new Employee();
            supervisor.setId(10L);
            supervisor.setFirstName("Nina");
            supervisor.setLastName("Nikolic");
            supervisor.setEmail("nina@bank.com");

            pendingOrder = new Order();
            pendingOrder.setId(1L);
            pendingOrder.setStatus(OrderStatus.PENDING);
            pendingOrder.setListing(listing);
            pendingOrder.setUserId(5L);
            pendingOrder.setUserRole("EMPLOYEE");
            pendingOrder.setDirection(OrderDirection.BUY);
            pendingOrder.setOrderType(OrderType.MARKET);
            pendingOrder.setQuantity(5);
            pendingOrder.setContractSize(1);
            pendingOrder.setApproximatePrice(new BigDecimal("755.0000"));

            mockSecurityContext("nina@bank.com");
            lenient().when(employeeRepository.findById(10L)).thenReturn(Optional.of(supervisor));

        }

        @Test
        @DisplayName("PENDING order se uspesno odobrava")
        void approveOrder_success() {
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDto result = orderService.approveOrder(1L);

            assertEquals("APPROVED", result.getStatus());
            assertEquals("Nina Nikolic", result.getApprovedBy());
            assertNotNull(result.getLastModification());
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("Order ne postoji → EntityNotFoundException")
        void approveOrder_orderNotFound() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> orderService.approveOrder(99L));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Order nije PENDING → IllegalStateException")
        void approveOrder_orderNotPending() {
            pendingOrder.setStatus(OrderStatus.APPROVED);
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingOrder));

            assertThrows(IllegalStateException.class, () -> orderService.approveOrder(1L));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Order DECLINED → IllegalStateException")
        void approveOrder_orderDeclined() {
            pendingOrder.setStatus(OrderStatus.DECLINED);
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingOrder));

            assertThrows(IllegalStateException.class, () -> orderService.approveOrder(1L));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Settlement date prosao → automatski DECLINED")
        void approveOrder_settlementDatePassed_automaticallyDeclines() {
            listing.setSettlementDate(java.time.LocalDate.now().minusDays(1));
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDto result = orderService.approveOrder(1L);

            assertEquals("DECLINED", result.getStatus());
            assertEquals("Nina Nikolic", result.getApprovedBy());
            assertNotNull(result.getLastModification());
        }

        @Test
        @DisplayName("Settlement date danas → APPROVED")
        void approveOrder_settlementDateToday_approves() {
            listing.setSettlementDate(java.time.LocalDate.now());
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDto result = orderService.approveOrder(1L);

            assertEquals("APPROVED", result.getStatus());
        }

        @Test
        @DisplayName("Settlement date u buducnosti → APPROVED")
        void approveOrder_settlementDateFuture_approves() {
            listing.setSettlementDate(java.time.LocalDate.now().plusDays(10));
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDto result = orderService.approveOrder(1L);

            assertEquals("APPROVED", result.getStatus());
        }

        @Test
        @DisplayName("Nema settlement date (akcije) → APPROVED")
        void approveOrder_noSettlementDate_approves() {
            listing.setSettlementDate(null);
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDto result = orderService.approveOrder(1L);

            assertEquals("APPROVED", result.getStatus());
            assertEquals("Nina Nikolic", result.getApprovedBy());
        }

        @Test
        @DisplayName("Phase 5.1: PENDING BUY order rezervise sredstva u trenutku odobravanja")
        void approveOrder_pendingBuy_reservesFundsAtApprovalTime() {
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDto result = orderService.approveOrder(1L);

            assertEquals("APPROVED", result.getStatus());
            assertNotNull(pendingOrder.getApprovedAt());
            assertEquals(testBankUsdAccount.getId(), pendingOrder.getReservedAccountId());
            assertNotNull(pendingOrder.getReservedAmount());
            verify(fundReservationService).reserveForBuy(eq(pendingOrder), any(Account.class));
            verify(fundReservationService, never()).reserveForSell(any(), any());
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("Phase 5.1: InsufficientFundsException ako availableBalance < totalReservation")
        void approveOrder_throwsWhenInsufficientFundsAtApprovalTime() {
            // Ispraznimo bankin racun tako da availableBalance < approximatePrice (755)
            testBankUsdAccount.setAvailableBalance(new BigDecimal("100.0000"));
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));

            assertThrows(rs.raf.banka2_bek.order.exception.InsufficientFundsException.class,
                    () -> orderService.approveOrder(1L));

            verify(fundReservationService, never()).reserveForBuy(any(), any());
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("Phase 5.1: PENDING SELL order rezervise kolicinu hartija")
        void approveOrder_pendingSell_reservesPortfolioQuantity() {
            pendingOrder.setDirection(OrderDirection.SELL);
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDto result = orderService.approveOrder(1L);

            assertEquals("APPROVED", result.getStatus());
            assertNotNull(pendingOrder.getApprovedAt());
            verify(fundReservationService).reserveForSell(eq(pendingOrder), any(Portfolio.class));
            verify(fundReservationService, never()).reserveForBuy(any(), any());
        }
    }
    @Nested
    @DisplayName("Odbijanje ordera")
    class DeclineOrder {

        private Order pendingOrder;
        private Employee supervisor;
        private Listing listing;

        @BeforeEach
        void setUp() {
            listing = new Listing();
            listing.setId(1L);
            listing.setTicker("AAPL");
            listing.setListingType(ListingType.STOCK);
            listing.setSettlementDate(null);

            supervisor = new Employee();
            supervisor.setId(10L);
            supervisor.setFirstName("Nina");
            supervisor.setLastName("Nikolic");
            supervisor.setEmail("nina@bank.com");

            pendingOrder = new Order();
            pendingOrder.setId(1L);
            pendingOrder.setStatus(OrderStatus.PENDING);
            pendingOrder.setListing(listing);
            pendingOrder.setUserId(5L);
            pendingOrder.setUserRole("EMPLOYEE");
            pendingOrder.setDirection(OrderDirection.BUY);
            pendingOrder.setOrderType(OrderType.MARKET);
            pendingOrder.setQuantity(5);
            pendingOrder.setContractSize(1);
            pendingOrder.setApproximatePrice(new BigDecimal("755.0000"));

            mockSecurityContext("nina@bank.com");
            lenient().when(employeeRepository.findById(10L)).thenReturn(Optional.of(supervisor));
        }

        @Test
        @DisplayName("PENDING order se uspesno odbija")
        void declineOrder_success() {
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDto result = orderService.declineOrder(1L);

            assertEquals("DECLINED", result.getStatus());
            assertEquals("Nina Nikolic", result.getApprovedBy());
            assertNotNull(result.getLastModification());
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("Order ne postoji → EntityNotFoundException")
        void declineOrder_orderNotFound() {
            when(orderRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> orderService.declineOrder(99L));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Order nije PENDING ni APPROVED → IllegalStateException")
        void declineOrder_orderNotPending() {
            pendingOrder.setStatus(OrderStatus.DONE);
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingOrder));

            assertThrows(IllegalStateException.class, () -> orderService.declineOrder(1L));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Order je vec DECLINED → IllegalStateException")
        void declineOrder_orderAlreadyDeclined() {
            pendingOrder.setStatus(OrderStatus.DECLINED);
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingOrder));

            assertThrows(IllegalStateException.class, () -> orderService.declineOrder(1L));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Phase 5.2: PENDING decline samo menja status, bez release-a")
        void declineOrder_pendingOrder_justChangesStatus_noRelease() {
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDto result = orderService.declineOrder(1L);

            assertEquals("DECLINED", result.getStatus());
            verify(fundReservationService, never()).releaseForBuy(any());
            verify(fundReservationService, never()).releaseForSell(any(), any());
        }

        @Test
        @DisplayName("Phase 5.2: APPROVED BUY decline oslobadja rezervaciju + agent usedLimit rollback")
        void declineOrder_approvedBuy_releasesFundReservation() {
            pendingOrder.setStatus(OrderStatus.APPROVED);
            pendingOrder.setReservedAccountId(testBankUsdAccount.getId());
            pendingOrder.setReservedAmount(new BigDecimal("755.0000"));

            ActuaryInfo actuary = new ActuaryInfo();
            actuary.setActuaryType(ActuaryType.AGENT);
            actuary.setUsedLimit(new BigDecimal("1000.0000"));

            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderStatusService.getAgentInfo(5L)).thenReturn(Optional.of(actuary));

            OrderDto result = orderService.declineOrder(1L);

            assertEquals("DECLINED", result.getStatus());
            verify(fundReservationService).releaseForBuy(pendingOrder);
            verify(fundReservationService, never()).releaseForSell(any(), any());
            // usedLimit se smanjio: 1000 - 755 = 245
            assertEquals(0, actuary.getUsedLimit().compareTo(new BigDecimal("245.0000")));
            verify(actuaryInfoRepository).save(actuary);
        }

        @Test
        @DisplayName("Phase 5.2: APPROVED SELL decline oslobadja rezervaciju hartija")
        void declineOrder_approvedSell_releasesPortfolioReservation() {
            pendingOrder.setStatus(OrderStatus.APPROVED);
            pendingOrder.setDirection(OrderDirection.SELL);

            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDto result = orderService.declineOrder(1L);

            assertEquals("DECLINED", result.getStatus());
            verify(fundReservationService).releaseForSell(eq(pendingOrder), any(Portfolio.class));
            verify(fundReservationService, never()).releaseForBuy(any());
        }
    }

    @Nested
    @DisplayName("Task 6 – getAllOrders")
    class GetAllOrdersTests {

        private Order makeOrder(Long id, OrderStatus status) {
            Order o = new Order();
            o.setId(id);
            o.setUserId(1L);
            o.setStatus(status);
            o.setUserRole("EMPLOYEE");
            o.setOrderType(OrderType.MARKET);
            o.setDirection(OrderDirection.BUY);
            o.setQuantity(10);
            o.setContractSize(1);
            o.setPricePerUnit(BigDecimal.valueOf(100));
            o.setDone(false);
            o.setRemainingPortions(10);
            o.setCreatedAt(java.time.LocalDateTime.now());
            o.setLastModification(java.time.LocalDateTime.now());
            o.setListing(testListing);
            return o;
        }

        @Test
        @DisplayName("Status ALL returns all orders")
        void getAllOrders_statusAll_returnsAllOrders() {
            PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
            when(orderRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(makeOrder(1L, OrderStatus.PENDING), makeOrder(2L, OrderStatus.APPROVED))));

            Page<OrderDto> result = orderService.getAllOrders("ALL", 0, 20);

            assertEquals(2, result.getTotalElements());
            verify(orderRepository).findAll(pageable);
            verify(orderRepository, never()).findByStatus(any(), any());
        }

        @Test
        @DisplayName("Status null returns all orders")
        void getAllOrders_statusNull_returnsAllOrders() {
            PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
            when(orderRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(makeOrder(1L, OrderStatus.DONE))));

            Page<OrderDto> result = orderService.getAllOrders(null, 0, 20);

            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("Filters by PENDING status")
        void getAllOrders_statusPending_filtersByStatus() {
            PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
            when(orderRepository.findByStatus(OrderStatus.PENDING, pageable))
                    .thenReturn(new PageImpl<>(List.of(makeOrder(1L, OrderStatus.PENDING))));

            Page<OrderDto> result = orderService.getAllOrders("PENDING", 0, 20);

            assertEquals(1, result.getTotalElements());
            assertEquals("PENDING", result.getContent().get(0).getStatus());
        }

        @Test
        @DisplayName("Invalid status throws IllegalArgumentException")
        void getAllOrders_invalidStatus_throwsException() {
            assertThrows(IllegalArgumentException.class,
                    () -> orderService.getAllOrders("INVALID", 0, 20));
        }
    }

    @Nested
    @DisplayName("Task 7 – getMyOrders")
    class GetMyOrdersTests {

        private Order makeOrder(Long id, Long userId) {
            Order o = new Order();
            o.setId(id);
            o.setUserId(userId);
            o.setStatus(OrderStatus.APPROVED);
            o.setUserRole("CLIENT");
            o.setOrderType(OrderType.MARKET);
            o.setDirection(OrderDirection.BUY);
            o.setQuantity(1);
            o.setContractSize(1);
            o.setPricePerUnit(BigDecimal.valueOf(100));
            o.setDone(false);
            o.setRemainingPortions(1);
            o.setCreatedAt(java.time.LocalDateTime.now());
            o.setLastModification(java.time.LocalDateTime.now());
            o.setListing(testListing);
            return o;
        }

        @Test
        @DisplayName("Client sees only their own orders")
        void getMyOrders_client_returnsOnlyHisOrders() {
            mockSecurityContext("client@test.com");
            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(orderRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(makeOrder(1L, 42L))));

            Page<OrderDto> result = orderService.getMyOrders(0, 20, null, null, null, null);

            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("Employee sees only their own orders")
        void getMyOrders_employee_returnsOnlyHisOrders() {
            mockSecurityContext("agent@test.com");
            when(clientRepository.findByEmail("agent@test.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("agent@test.com")).thenReturn(Optional.of(testEmployee));
            when(orderRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(makeOrder(5L, 99L))));

            Page<OrderDto> result = orderService.getMyOrders(0, 20, null, null, null, null);

            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("User not found throws EntityNotFoundException")
        void getMyOrders_userNotFound_throwsException() {
            mockSecurityContext("unknown@test.com");
            when(clientRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> orderService.getMyOrders(0, 20, null, null, null, null));
        }
    }

    @Nested
    @DisplayName("Task 8 – getOrderById")
    class GetOrderByIdTests {

        private Order makeOrder(Long id, Long userId) {
            Order o = new Order();
            o.setId(id);
            o.setUserId(userId);
            o.setStatus(OrderStatus.APPROVED);
            o.setUserRole("CLIENT");
            o.setOrderType(OrderType.MARKET);
            o.setDirection(OrderDirection.BUY);
            o.setQuantity(1);
            o.setContractSize(1);
            o.setPricePerUnit(BigDecimal.valueOf(100));
            o.setDone(false);
            o.setRemainingPortions(1);
            o.setCreatedAt(java.time.LocalDateTime.now());
            o.setLastModification(java.time.LocalDateTime.now());
            o.setListing(testListing);
            return o;
        }

        private void mockAdminContext(String email) {
            Authentication auth = mock(Authentication.class);
            when(auth.getName()).thenReturn(email);
            java.util.Collection<org.springframework.security.core.GrantedAuthority> authorities =
                    List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));
            doReturn(authorities).when(auth).getAuthorities();
            SecurityContext ctx = mock(SecurityContext.class);
            when(ctx.getAuthentication()).thenReturn(auth);
            SecurityContextHolder.setContext(ctx);
        }

        @Test
        @DisplayName("Supervisor can see any order")
        void getOrderById_supervisor_canSeeAnyOrder() {
            mockAdminContext("admin@test.com");
            when(clientRepository.findByEmail("admin@test.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(testEmployee));

            Order o = makeOrder(10L, 999L);
            when(orderRepository.findById(10L)).thenReturn(Optional.of(o));

            OrderDto result = orderService.getOrderById(10L);

            assertNotNull(result);
            assertEquals(10L, result.getId());
        }

        @Test
        @DisplayName("User can see their own order")
        void getOrderById_ownOrder_success() {
            mockSecurityContext("client@test.com");
            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));

            Order o = makeOrder(3L, 42L);
            when(orderRepository.findById(3L)).thenReturn(Optional.of(o));

            OrderDto result = orderService.getOrderById(3L);

            assertNotNull(result);
            assertEquals(3L, result.getId());
        }

        @Test
        @DisplayName("User cannot see another user's order — throws IllegalStateException (403)")
        void getOrderById_otherUsersOrder_throwsForbidden() {
            mockSecurityContext("client@test.com");
            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));

            Order o = makeOrder(3L, 999L);
            when(orderRepository.findById(3L)).thenReturn(Optional.of(o));

            assertThrows(IllegalStateException.class, () -> orderService.getOrderById(3L));
        }

        @Test
        @DisplayName("Order not found throws EntityNotFoundException")
        void getOrderById_notFound_throwsException() {
            mockSecurityContext("client@test.com");
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> orderService.getOrderById(999L));
        }
    }
}
