package rs.raf.banka2_bek.loan.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import rs.raf.banka2_bek.notification.service.MailNotificationService;

import static org.assertj.core.api.Assertions.assertThatNoException;

@SpringBootTest
@ActiveProfiles("test")
class LoanInstallmentSchedulerIntegrationTest {

    @Autowired
    private LoanInstallmentScheduler loanInstallmentScheduler;

    @MockitoBean
    private MailNotificationService mailNotificationService;

    @Test
    @DisplayName("processInstallments runs without error when no installments are due")
    void processInstallmentsRunsWithEmptyDb() {
        assertThatNoException().isThrownBy(() -> loanInstallmentScheduler.processInstallments());
    }
}
