package rs.raf.banka2_bek.account.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatNoException;

@SpringBootTest
@ActiveProfiles("test")
class AccountSpendingResetSchedulerIntegrationTest {

    @Autowired
    private AccountSpendingResetScheduler scheduler;

    @Test
    @DisplayName("resetDailySpending runs without error when no accounts exist")
    @Transactional
    void resetDailySpending_noAccounts() {
        assertThatNoException().isThrownBy(() -> scheduler.resetDailySpending());
    }

    @Test
    @DisplayName("resetMonthlySpending runs without error when no accounts exist")
    @Transactional
    void resetMonthlySpending_noAccounts() {
        assertThatNoException().isThrownBy(() -> scheduler.resetMonthlySpending());
    }
}
