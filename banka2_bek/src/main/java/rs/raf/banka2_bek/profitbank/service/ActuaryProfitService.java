package rs.raf.banka2_bek.profitbank.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.auth.util.UserRole;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.profitbank.dto.ProfitBankDtos.ActuaryProfitDto;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.util.ListingCurrencyResolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * P6 — Spec Celina 4 (Nova) §4393-4505 (Stranica: Profit aktuara).
 *
 * Za svakog aktuara (supervizor + agent) racuna ukupan profit u RSD:
 *   - Per-listing: SELL value - BUY cost (samo done orderi)
 *   - Konvertuj u RSD po srednjem kursu (bez komisije)
 *   - Sumiraj kroz sve listinge
 *
 * Cache: Caffeine sa TTL 5 min (vidi {@link ProfitBankCacheConfig}).
 * Iteracija po svim DONE orderima + per-order FX konverzija je O(n) u
 * broju ordera; posle 1000+ ordera u bazi sirov racun traje ~1-2s. Cache
 * smanjuje na ~5ms na ponovljene pozive sa istim ulazom.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActuaryProfitService {

    private final OrderRepository orderRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrencyConversionService currencyConversionService;

    @Cacheable(value = ProfitBankCacheConfig.ACTUARY_PROFIT_CACHE, sync = true)
    public List<ActuaryProfitDto> listAllActuariesProfit() {
        // 1) Skupi sve DONE ordere koje su inicirali zaposleni (userRole=EMPLOYEE).
        List<Order> doneEmployeeOrders = orderRepository.findByIsDoneTrue().stream()
                .filter(o -> UserRole.isEmployee(o.getUserRole()))
                .toList();

        // 2) Per-aktuar: per-listing { sell, buy }
        Map<Long, Map<Long, BigDecimal>> sellByActuarPerListing = new HashMap<>();
        Map<Long, Map<Long, BigDecimal>> buyByActuarPerListing = new HashMap<>();
        Map<Long, Map<Long, String>> currencyByActuarPerListing = new HashMap<>();
        Map<Long, Integer> ordersDoneCount = new HashMap<>();

        for (Order order : doneEmployeeOrders) {
            if (order.getListing() == null) continue;
            Long actuarId = order.getUserId();
            Long listingId = order.getListing().getId();
            BigDecimal value = nullSafe(order.getPricePerUnit())
                    .multiply(BigDecimal.valueOf(order.getQuantity()))
                    .multiply(BigDecimal.valueOf(order.getContractSize()));

            currencyByActuarPerListing
                    .computeIfAbsent(actuarId, k -> new HashMap<>())
                    .putIfAbsent(listingId, resolveOrderCurrency(order.getListing()));

            if (order.getDirection() == OrderDirection.SELL) {
                sellByActuarPerListing
                        .computeIfAbsent(actuarId, k -> new HashMap<>())
                        .merge(listingId, value, BigDecimal::add);
            } else {
                buyByActuarPerListing
                        .computeIfAbsent(actuarId, k -> new HashMap<>())
                        .merge(listingId, value, BigDecimal::add);
            }
            ordersDoneCount.merge(actuarId, 1, Integer::sum);
        }

        // 3) Per-aktuar: sum profit u RSD
        Set<Long> allActuarIds = new HashSet<>();
        allActuarIds.addAll(sellByActuarPerListing.keySet());
        allActuarIds.addAll(buyByActuarPerListing.keySet());

        return allActuarIds.stream()
                .map(actuarId -> buildActuaryProfit(
                        actuarId,
                        sellByActuarPerListing.getOrDefault(actuarId, Map.of()),
                        buyByActuarPerListing.getOrDefault(actuarId, Map.of()),
                        currencyByActuarPerListing.getOrDefault(actuarId, Map.of()),
                        ordersDoneCount.getOrDefault(actuarId, 0)))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ActuaryProfitDto::getTotalProfitRsd).reversed())
                .toList();
    }

    private ActuaryProfitDto buildActuaryProfit(
            Long actuarId,
            Map<Long, BigDecimal> sellByListing,
            Map<Long, BigDecimal> buyByListing,
            Map<Long, String> currencyByListing,
            int ordersDone) {
        Employee emp = employeeRepository.findById(actuarId).orElse(null);
        if (emp == null) {
            return null;
        }

        BigDecimal totalProfitRsd = BigDecimal.ZERO;
        Set<Long> allListings = new HashSet<>(sellByListing.keySet());
        allListings.addAll(buyByListing.keySet());
        for (Long listingId : allListings) {
            BigDecimal sell = sellByListing.getOrDefault(listingId, BigDecimal.ZERO);
            BigDecimal buy = buyByListing.getOrDefault(listingId, BigDecimal.ZERO);
            BigDecimal assetProfit = sell.subtract(buy);
            String ccy = currencyByListing.getOrDefault(listingId, "RSD");
            totalProfitRsd = totalProfitRsd.add(convertToRsd(assetProfit, ccy));
        }

        Set<String> perms = emp.getPermissions() != null ? emp.getPermissions() : Set.of();
        // Admin je uvek supervisor (po Celini 3); ako nema ni jedno ni drugo, AGENT.
        String position = (perms.contains("SUPERVISOR") || perms.contains("ADMIN"))
                ? "SUPERVISOR" : "AGENT";

        return new ActuaryProfitDto(
                actuarId,
                emp.getFirstName() + " " + emp.getLastName(),
                position,
                totalProfitRsd.setScale(2, RoundingMode.HALF_UP),
                ordersDone);
    }

    private BigDecimal convertToRsd(BigDecimal amount, String fromCurrency) {
        if (amount == null || amount.signum() == 0) return BigDecimal.ZERO;
        if (fromCurrency == null || "RSD".equalsIgnoreCase(fromCurrency)) return amount;
        try {
            return currencyConversionService.convert(amount, fromCurrency, "RSD");
        } catch (RuntimeException e) {
            log.warn("Konverzija {} -> RSD nije uspela ({}); koristim raw amount", fromCurrency, e.getMessage());
            return amount;
        }
    }

    private String resolveOrderCurrency(Listing listing) {
        return ListingCurrencyResolver.resolveSafe(listing, "RSD");
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
