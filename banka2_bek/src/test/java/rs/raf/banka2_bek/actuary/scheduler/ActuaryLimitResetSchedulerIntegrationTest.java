package rs.raf.banka2_bek.actuary.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ActuaryLimitResetSchedulerIntegrationTest {

    @Autowired
    private ActuaryLimitResetScheduler scheduler;

    @Autowired
    private ActuaryInfoRepository actuaryInfoRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @BeforeEach
    void clean() {
        actuaryInfoRepository.deleteAll();
    }

    private Employee savedEmployee(String email) {
        Employee e = new Employee();
        e.setFirstName("Test");
        e.setLastName("Agent");
        e.setDateOfBirth(LocalDate.of(1990, 1, 1));
        e.setGender("M");
        e.setEmail(email);
        e.setPhone("0601234567");
        e.setAddress("Adresa 1");
        e.setUsername(email.split("@")[0]);
        e.setPassword("hashedpassword");
        e.setSaltPassword("salt123");
        e.setPosition("Agent");
        e.setDepartment("Trading");
        e.setActive(true);
        return employeeRepository.save(e);
    }

    private ActuaryInfo savedActuaryInfo(Employee employee, BigDecimal usedLimit) {
        ActuaryInfo info = new ActuaryInfo();
        info.setEmployee(employee);
        info.setActuaryType(ActuaryType.AGENT);
        info.setDailyLimit(BigDecimal.valueOf(10000));
        info.setUsedLimit(usedLimit);
        info.setNeedApproval(false);
        return actuaryInfoRepository.save(info);
    }

    @Test
    @DisplayName("resets usedLimit to 0 for all actuaries")
    void resetsUsedLimitToZero() {
        Employee e1 = savedEmployee("agent1@banka.rs");
        Employee e2 = savedEmployee("agent2@banka.rs");
        savedActuaryInfo(e1, BigDecimal.valueOf(5000));
        savedActuaryInfo(e2, BigDecimal.valueOf(8000));

        scheduler.resetDailyLimits();

        List<ActuaryInfo> all = actuaryInfoRepository.findAll();
        assertThat(all).hasSize(2);
        for (ActuaryInfo info : all) {
            assertThat(info.getUsedLimit()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    @DisplayName("runs without error when no actuaries exist")
    void noActuaries() {
        scheduler.resetDailyLimits();

        assertThat(actuaryInfoRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("does not change dailyLimit, only usedLimit")
    void doesNotChangeDailyLimit() {
        Employee e = savedEmployee("agent3@banka.rs");
        ActuaryInfo info = savedActuaryInfo(e, BigDecimal.valueOf(3000));
        BigDecimal originalDailyLimit = info.getDailyLimit();

        scheduler.resetDailyLimits();

        ActuaryInfo updated = actuaryInfoRepository.findById(info.getId()).orElseThrow();
        assertThat(updated.getDailyLimit()).isEqualByComparingTo(originalDailyLimit);
        assertThat(updated.getUsedLimit()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
