package rs.raf.banka2_bek.order.service.implementation;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.order.dto.CreateOrderDto;
import rs.raf.banka2_bek.order.dto.OrderDto;
import rs.raf.banka2_bek.order.mapper.OrderMapper;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.order.service.FundsVerificationService;
import rs.raf.banka2_bek.order.service.ListingPriceService;
import rs.raf.banka2_bek.order.service.OrderService;
import rs.raf.banka2_bek.order.service.OrderStatusService;
import rs.raf.banka2_bek.order.service.OrderValidationService;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
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
    private final FundsVerificationService fundsVerificationService;
    private final OrderStatusService orderStatusService;

    // Exchange close times in UTC (16:00 EST = 21:00 UTC, 17:00 EST = 22:00 UTC)
    private static final Map<String, LocalTime> EXCHANGE_CLOSE_TIMES_UTC = Map.of(
            "NYSE", LocalTime.of(21, 0),
            "NASDAQ", LocalTime.of(21, 0),
            "CME", LocalTime.of(22, 0)
    );

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

        // Step 5: Verify funds / securities
        fundsVerificationService.verify(dto, userContext.userId, approximatePrice, listing, orderType, direction);

        // Step 6: Determine status
        OrderStatus status = orderStatusService.determineStatus(userContext.userRole, userContext.userId, approximatePrice);
        String approvedBy = (status == OrderStatus.APPROVED) ? "No need for approval" : null;

        // Step 7: Compute afterHours
        boolean afterHours = computeAfterHours(listing);

        // Step 8: Build and save order
        Order order = OrderMapper.fromCreateDto(dto, listing);
        order.setUserId(userContext.userId);
        order.setUserRole(userContext.userRole);
        order.setPricePerUnit(pricePerUnit);
        order.setApproximatePrice(approximatePrice);
        order.setStatus(status);
        order.setApprovedBy(approvedBy);
        order.setAfterHours(afterHours);

        Order savedOrder = orderRepository.save(order);

        // Step 9: Update agent usedLimit if APPROVED
        if (status == OrderStatus.APPROVED && "EMPLOYEE".equals(userContext.userRole)) {
            Optional<ActuaryInfo> actuaryOpt = orderStatusService.getAgentInfo(userContext.userId);
            actuaryOpt.ifPresent(actuary -> {
                if (actuary.getActuaryType() == ActuaryType.AGENT) {
                    BigDecimal current = actuary.getUsedLimit() != null ? actuary.getUsedLimit() : BigDecimal.ZERO;
                    actuary.setUsedLimit(current.add(approximatePrice));
                    actuaryInfoRepository.save(actuary);
                }
            });
        }

        // Step 10: Execution handled by OrderScheduler cron job

        return toDtoWithUserName(savedOrder);
    }

    @Override
    @Transactional
    public OrderDto approveOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"+ orderId));

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
        order.setStatus(OrderStatus.APPROVED);
        order.setApprovedBy(supervisorName);
        order.setLastModification(LocalDateTime.now());

        Order saved = orderRepository.save(order);

        // Update agent usedLimit when supervisor approves
        if ("EMPLOYEE".equals(order.getUserRole())) {
            Optional<ActuaryInfo> actuaryOpt = orderStatusService.getAgentInfo(order.getUserId());
            actuaryOpt.ifPresent(actuary -> {
                if (actuary.getActuaryType() == ActuaryType.AGENT) {
                    BigDecimal current = actuary.getUsedLimit() != null ? actuary.getUsedLimit() : BigDecimal.ZERO;
                    BigDecimal orderPrice = order.getApproximatePrice() != null ? order.getApproximatePrice() : BigDecimal.ZERO;
                    actuary.setUsedLimit(current.add(orderPrice));
                    actuaryInfoRepository.save(actuary);
                }
            });
        }

        return toDtoWithUserName(saved);
    }

    @Override
    @Transactional
    public OrderDto declineOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found " + orderId));

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.APPROVED) {
            throw new IllegalStateException("Only PENDING or APPROVED orders can be declined/cancelled");
        }

        String supervisorName = getSupervisorName();

        order.setStatus(OrderStatus.DECLINED);
        order.setApprovedBy(supervisorName);
        order.setLastModification(LocalDateTime.now());

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
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

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
            return new UserContext(clientOpt.get().getId(), "CLIENT");
        }

        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        return new UserContext(employee.getId(), "EMPLOYEE");
    }

    private boolean computeAfterHours(Listing listing) {
        String exchange = listing.getExchangeAcronym();
        if (exchange == null) return false;

        LocalTime closeTime = EXCHANGE_CLOSE_TIMES_UTC.get(exchange.toUpperCase());
        if (closeTime == null) return false;  // FOREX and unknown exchanges are never afterHours

        LocalTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC).toLocalTime();
        LocalTime windowEnd = closeTime.plusHours(4);

        if (windowEnd.isAfter(closeTime)) {
            // Normal case: window does not cross midnight
            return !nowUtc.isBefore(closeTime) && nowUtc.isBefore(windowEnd);
        } else {
            // Window crosses midnight (e.g., close at 22:00, window ends at 02:00)
            return !nowUtc.isBefore(closeTime) || nowUtc.isBefore(windowEnd);
        }
    }
    private String getSupervisorName() {
        UserContext userContext = resolveCurrentUser();
        return employeeRepository.findById(userContext.userId())
                .map(e -> e.getFirstName() + " " + e.getLastName())
                .orElseThrow(() -> new IllegalStateException("Supervisor not found"));
    }

    private String resolveUserName(Long userId, String userRole) {
        if ("CLIENT".equals(userRole)) {
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

    private record UserContext(Long userId, String userRole) {}
}
