package rs.raf.banka2_bek.loan.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatNoException;

@SpringBootTest
@ActiveProfiles("test")
class VariableRateSchedulerIntegrationTest {

    @Autowired
    private VariableRateScheduler variableRateScheduler;

    @Test
    @DisplayName("adjustVariableRates runs without error when no loans exist in DB")
    void adjustVariableRatesRunsWithEmptyDb() {
        assertThatNoException().isThrownBy(() -> variableRateScheduler.adjustVariableRates());
    }
}
