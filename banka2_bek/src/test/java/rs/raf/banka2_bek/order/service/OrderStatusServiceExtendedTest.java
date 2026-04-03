package rs.raf.banka2_bek.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.order.model.OrderStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Extended tests for OrderStatusService covering additional edge cases:
 * - getAgentInfo returns present/empty
 * - Null dailyLimit treated as zero
 * - Very large prices
 * - Boundary: usedLimit + price exactly exceeds by 0.01
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderStatusService - Extended")
class OrderStatusServiceExtendedTest {

    @Mock private ActuaryInfoRepository actuaryInfoRepository;

    @InjectMocks
    private OrderStatusService service;

    private ActuaryInfo agentInfo(boolean needApproval, BigDecimal usedLimit, BigDecimal dailyLimit) {
        ActuaryInfo info = new ActuaryInfo();
        info.setActuaryType(ActuaryType.AGENT);
        info.setNeedApproval(needApproval);
        info.setUsedLimit(usedLimit);
        info.setDailyLimit(dailyLimit);
        return info;
    }

    // ─── getAgentInfo ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAgentInfo")
    class GetAgentInfo {

        @Test
        @DisplayName("returns ActuaryInfo when present")
        void returnsWhenPresent() {
            Employee emp = Employee.builder().id(10L).build();
            ActuaryInfo info = new ActuaryInfo();
            info.setEmployee(emp);
            info.setActuaryType(ActuaryType.AGENT);

            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(Optional.of(info));

            Optional<ActuaryInfo> result = service.getAgentInfo(10L);

            assertThat(result).isPresent();
            assertThat(result.get().getActuaryType()).isEqualTo(ActuaryType.AGENT);
        }

        @Test
        @DisplayName("returns empty when no ActuaryInfo exists")
        void returnsEmptyWhenAbsent() {
            when(actuaryInfoRepository.findByEmployeeId(99L)).thenReturn(Optional.empty());

            Optional<ActuaryInfo> result = service.getAgentInfo(99L);

            assertThat(result).isEmpty();
        }
    }

    // ─── Edge cases for determineStatus ─────────────────────────────────────────

    @Nested
    @DisplayName("determineStatus - edge cases")
    class DetermineStatusEdgeCases {

        @Test
        @DisplayName("AGENT with null dailyLimit treated as 0, any price -> PENDING")
        void agentNullDailyLimit_pendingForAnyPrice() {
            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(
                    Optional.of(agentInfo(false, BigDecimal.ZERO, null)));

            OrderStatus status = service.determineStatus("EMPLOYEE", 10L, new BigDecimal("1"));

            // usedLimit(0) + price(1) > dailyLimit(0) -> PENDING
            assertThat(status).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("AGENT with both null usedLimit and null dailyLimit, price=0 -> APPROVED")
        void agentBothNull_priceZero_approved() {
            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(
                    Optional.of(agentInfo(false, null, null)));

            // usedLimit(0) + price(0) = 0 which is NOT > dailyLimit(0) -> APPROVED
            OrderStatus status = service.determineStatus("EMPLOYEE", 10L, BigDecimal.ZERO);

            assertThat(status).isEqualTo(OrderStatus.APPROVED);
        }

        @Test
        @DisplayName("AGENT exceeds limit by 0.01 -> PENDING")
        void agentExceedsBySmallAmount_pending() {
            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(
                    Optional.of(agentInfo(false, new BigDecimal("9999.99"), new BigDecimal("10000.00"))));

            OrderStatus status = service.determineStatus("EMPLOYEE", 10L, new BigDecimal("0.02"));

            // 9999.99 + 0.02 = 10000.01 > 10000.00 -> PENDING
            assertThat(status).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("AGENT with very large price within limit -> APPROVED")
        void agentLargePrice_withinLimit() {
            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(
                    Optional.of(agentInfo(false, BigDecimal.ZERO, new BigDecimal("99999999"))));

            OrderStatus status = service.determineStatus("EMPLOYEE", 10L, new BigDecimal("50000000"));

            assertThat(status).isEqualTo(OrderStatus.APPROVED);
        }

        @Test
        @DisplayName("CLIENT role never checks ActuaryInfo repository")
        void clientNeverChecksRepository() {
            OrderStatus status = service.determineStatus("CLIENT", 999L, new BigDecimal("999999"));

            assertThat(status).isEqualTo(OrderStatus.APPROVED);
            verifyNoInteractions(actuaryInfoRepository);
        }

        @Test
        @DisplayName("unknown role is treated as non-CLIENT, checks ActuaryInfo")
        void unknownRole_checksActuaryInfo() {
            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(Optional.empty());

            // Not CLIENT, no ActuaryInfo -> APPROVED (treated as supervisor)
            OrderStatus status = service.determineStatus("UNKNOWN_ROLE", 10L, new BigDecimal("100"));

            assertThat(status).isEqualTo(OrderStatus.APPROVED);
        }
    }
}
