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
import rs.raf.banka2_bek.order.model.OrderStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderStatusService")
class OrderStatusServiceTest {

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

    private ActuaryInfo supervisorInfo() {
        ActuaryInfo info = new ActuaryInfo();
        info.setActuaryType(ActuaryType.SUPERVISOR);
        info.setNeedApproval(false);
        return info;
    }

    @Nested
    @DisplayName("CLIENT")
    class ClientRole {

        @Test
        @DisplayName("CLIENT uvek dobija APPROVED")
        void clientIsAlwaysApproved() {
            OrderStatus status = service.determineStatus("CLIENT", 1L, new BigDecimal("1000"));
            assertEquals(OrderStatus.APPROVED, status);
            verifyNoInteractions(actuaryInfoRepository);
        }
    }

    @Nested
    @DisplayName("EMPLOYEE — SUPERVISOR")
    class SupervisorRole {

        @Test
        @DisplayName("SUPERVISOR uvek dobija APPROVED")
        void supervisorIsAlwaysApproved() {
            when(actuaryInfoRepository.findByEmployeeId(2L)).thenReturn(Optional.of(supervisorInfo()));

            OrderStatus status = service.determineStatus("EMPLOYEE", 2L, new BigDecimal("999999"));
            assertEquals(OrderStatus.APPROVED, status);
        }

        @Test
        @DisplayName("EMPLOYEE bez ActuaryInfo → tretira se kao SUPERVISOR, APPROVED")
        void employeeWithoutActuaryInfoIsApproved() {
            when(actuaryInfoRepository.findByEmployeeId(3L)).thenReturn(Optional.empty());

            OrderStatus status = service.determineStatus("EMPLOYEE", 3L, new BigDecimal("1000"));
            assertEquals(OrderStatus.APPROVED, status);
        }
    }

    @Nested
    @DisplayName("EMPLOYEE — AGENT")
    class AgentRole {

        @Test
        @DisplayName("AGENT sa needApproval=true → PENDING")
        void agentNeedApprovalTrue() {
            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(
                    Optional.of(agentInfo(true, BigDecimal.ZERO, new BigDecimal("10000"))));

            OrderStatus status = service.determineStatus("EMPLOYEE", 10L, new BigDecimal("1000"));
            assertEquals(OrderStatus.PENDING, status);
        }

        @Test
        @DisplayName("AGENT sa needApproval=false, usedLimit + price > dailyLimit → PENDING")
        void agentOverDailyLimit() {
            // usedLimit=8000, approximatePrice=3000, dailyLimit=10000 → 11000 > 10000
            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(
                    Optional.of(agentInfo(false, new BigDecimal("8000"), new BigDecimal("10000"))));

            OrderStatus status = service.determineStatus("EMPLOYEE", 10L, new BigDecimal("3000"));
            assertEquals(OrderStatus.PENDING, status);
        }

        @Test
        @DisplayName("AGENT sa needApproval=false, usedLimit + price == dailyLimit → APPROVED")
        void agentExactlyAtDailyLimit() {
            // usedLimit=7000, approximatePrice=3000, dailyLimit=10000 → 10000 == 10000 (nije prekoračeno)
            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(
                    Optional.of(agentInfo(false, new BigDecimal("7000"), new BigDecimal("10000"))));

            OrderStatus status = service.determineStatus("EMPLOYEE", 10L, new BigDecimal("3000"));
            assertEquals(OrderStatus.APPROVED, status);
        }

        @Test
        @DisplayName("AGENT sa needApproval=false, usedLimit + price < dailyLimit → APPROVED")
        void agentUnderDailyLimit() {
            // usedLimit=1000, approximatePrice=2000, dailyLimit=10000 → 3000 < 10000
            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(
                    Optional.of(agentInfo(false, new BigDecimal("1000"), new BigDecimal("10000"))));

            OrderStatus status = service.determineStatus("EMPLOYEE", 10L, new BigDecimal("2000"));
            assertEquals(OrderStatus.APPROVED, status);
        }

        @Test
        @DisplayName("AGENT sa null usedLimit → tretira se kao 0")
        void agentWithNullUsedLimit() {
            // usedLimit=null→0, approximatePrice=5000, dailyLimit=10000 → APPROVED
            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(
                    Optional.of(agentInfo(false, null, new BigDecimal("10000"))));

            OrderStatus status = service.determineStatus("EMPLOYEE", 10L, new BigDecimal("5000"));
            assertEquals(OrderStatus.APPROVED, status);
        }

        @Test
        @DisplayName("AGENT sa needApproval=true ignorise limit — uvek PENDING")
        void agentNeedApprovalIgnoresLimit() {
            // Čak i ako je limit OK, needApproval=true → PENDING
            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(
                    Optional.of(agentInfo(true, BigDecimal.ZERO, new BigDecimal("100000"))));

            OrderStatus status = service.determineStatus("EMPLOYEE", 10L, new BigDecimal("1"));
            assertEquals(OrderStatus.PENDING, status);
        }
    }
}
