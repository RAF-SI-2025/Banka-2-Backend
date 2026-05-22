package rs.raf.banka2_bek.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.berza.service.ExchangeManagementService;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.investmentfund.repository.InvestmentFundRepository;
import rs.raf.banka2_bek.notification.service.NotificationService;
import rs.raf.banka2_bek.order.dto.OrderDto;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.order.service.implementation.OrderServiceImpl;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("OrderServiceImpl — getMyOrders filtering (B4)")
class GetMyOrdersFilteringTest {

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
    @Mock private InvestmentFundRepository investmentFundRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Client client;
    private Authentication auth;
    private SecurityContext secCtx;

    @BeforeEach
    void setUp() {
        client = new Client();
        client.setId(42L);
        client.setEmail("client@test.com");

        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("client@test.com");

        secCtx = mock(SecurityContext.class);
        when(secCtx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(secCtx);

        when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
    }

    @Test
    @DisplayName("no filters — returns page via Specification")
    void getMyOrders_noFilters_usesSpecification() {
        Page<OrderDto> result = orderService.getMyOrders(0, 20, null, null, null, null);

        assertNotNull(result);
        verify(orderRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("status=PENDING — passes OrderStatus.PENDING to specification")
    void getMyOrders_withStatusPending_callsRepository() {
        Page<OrderDto> result = orderService.getMyOrders(0, 20, "PENDING", null, null, null);

        assertNotNull(result);
        verify(orderRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("status=ALL — treated as no status filter")
    void getMyOrders_statusAll_treatedAsNoFilter() {
        orderService.getMyOrders(0, 20, "ALL", null, null, null);

        verify(orderRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("unknown status string — ignored gracefully")
    void getMyOrders_unknownStatus_ignoredGracefully() {
        assertDoesNotThrow(() -> orderService.getMyOrders(0, 10, "GARBAGE", null, null, null));
    }

    @Test
    @DisplayName("dateFrom and dateTo are forwarded to specification")
    void getMyOrders_withDateRange_callsRepository() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 6, 30);

        orderService.getMyOrders(0, 20, null, from, to, null);

        verify(orderRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("listingType=STOCK is forwarded to specification")
    void getMyOrders_withListingTypeStock_callsRepository() {
        orderService.getMyOrders(0, 20, null, null, null, "STOCK");

        verify(orderRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("unknown listingType is ignored gracefully")
    void getMyOrders_unknownListingType_ignoredGracefully() {
        assertDoesNotThrow(() -> orderService.getMyOrders(0, 10, null, null, null, "OPTION"));
    }

    @Test
    @DisplayName("resolves employee when client not found")
    void getMyOrders_resolvesByEmployee_whenClientAbsent() {
        Employee emp = new Employee();
        emp.setId(77L);
        emp.setEmail("emp@test.com");

        when(auth.getName()).thenReturn("emp@test.com");
        when(clientRepository.findByEmail("emp@test.com")).thenReturn(Optional.empty());
        when(employeeRepository.findByEmail("emp@test.com")).thenReturn(Optional.of(emp));

        orderService.getMyOrders(0, 20, null, null, null, null);

        verify(orderRepository).findAll(any(Specification.class), any(Pageable.class));
    }
}
