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

        // Step 10: Hook — orderExecutionService.scheduleExecution(savedOrder) — not yet implemented

        return OrderMapper.toDto(savedOrder);
    }

    @Override
    public OrderDto approveOrder(Long orderId) {
        // TODO: Implementirati odobravanje ordera
        // 1. Naci order po ID-ju, proveriti da je PENDING
        // 2. Proveriti da settlement date nije prosao (za futures/opcije)
        //    - Ako jeste, automatski DECLINE
        // 3. Postaviti status = APPROVED
        // 4. Postaviti approvedBy = ime ulogovanog supervizora
        // 5. Postaviti lastModification = now()
        // 6. Sacuvati
        // 7. Pokrenuti izvrsavanje (asinhrono) - buduci sprint
        throw new UnsupportedOperationException("TODO: Implementirati approveOrder");
    }

    @Override
    public OrderDto declineOrder(Long orderId) {
        // TODO: Implementirati odbijanje ordera
        // 1. Naci order po ID-ju, proveriti da je PENDING
        // 2. Postaviti status = DECLINED
        // 3. Postaviti approvedBy = ime ulogovanog supervizora
        // 4. Postaviti lastModification = now()
        // 5. Sacuvati
        throw new UnsupportedOperationException("TODO: Implementirati declineOrder");
    }

    @Override
    public Page<OrderDto> getAllOrders(String status, int page, int size) {
        // TODO: Implementirati
        // 1. Ako je status "ALL" ili null, dohvatiti sve ordere
        // 2. Inace, filtrirati po statusu (PENDING, APPROVED, DECLINED, DONE)
        // 3. Sortirati po createdAt DESC
        // 4. Mapirati u OrderDto
        throw new UnsupportedOperationException("TODO: Implementirati getAllOrders");
    }

    @Override
    public Page<OrderDto> getMyOrders(int page, int size) {
        // TODO: Implementirati
        // 1. Dohvatiti email iz SecurityContext
        // 2. Naci userId na osnovu emaila
        // 3. Dohvatiti ordere za tog korisnika
        // 4. Mapirati u OrderDto
        throw new UnsupportedOperationException("TODO: Implementirati getMyOrders");
    }

    @Override
    public OrderDto getOrderById(Long orderId) {
        // TODO: Implementirati
        // 1. Naci order po ID-ju
        // 2. Proveriti da korisnik ima pristup (svoj order ili supervizor)
        // 3. Mapirati u OrderDto
        throw new UnsupportedOperationException("TODO: Implementirati getOrderById");
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

    private record UserContext(Long userId, String userRole) {}
}
