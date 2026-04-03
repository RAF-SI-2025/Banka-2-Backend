package rs.raf.banka2_bek.notification.listener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.employee.event.EmployeeActivationConfirmationEvent;
import rs.raf.banka2_bek.notification.service.MailNotificationService;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmployeeActivationConfirmationListenerTest {

    @Mock
    private MailNotificationService mailNotificationService;

    @InjectMocks
    private EmployeeActivationConfirmationListener listener;

    @Test
    void onEmployeeActivationConfirmation_callsSendActivationConfirmationMailWithCorrectArgs() {
        EmployeeActivationConfirmationEvent event = new EmployeeActivationConfirmationEvent(
                this, "marko@banka.rs", "Marko"
        );

        listener.onEmployeeActivationConfirmationEvent(event);

        verify(mailNotificationService).sendActivationConfirmationMail("marko@banka.rs", "Marko");
    }
}
