package rs.raf.banka2_bek.order.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
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
import rs.raf.banka2_bek.order.service.implementation.OrderServiceImpl;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private OrderServiceImpl orderService;

    private Listing testListing;
    private Client testClient;
    private Employee testEmployee;

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
        @DisplayName("FundsVerification baca grešku → order se ne čuva")
        void fundsVerificationFailurePropagates() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("client@test.com");

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));
            doThrow(new IllegalArgumentException("Insufficient funds"))
                    .when(fundsVerificationService).verify(any(), any(), any(), any(), any(), any());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> orderService.createOrder(dto));
            assertEquals("Insufficient funds", ex.getMessage());
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
}
