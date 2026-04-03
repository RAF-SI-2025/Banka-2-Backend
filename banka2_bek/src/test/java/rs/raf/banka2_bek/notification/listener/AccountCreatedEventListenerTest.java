package rs.raf.banka2_bek.notification.listener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.event.AccountCreatedEvent;
import rs.raf.banka2_bek.notification.service.MailNotificationService;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountCreatedEventListenerTest {

    @Mock
    private MailNotificationService mailNotificationService;

    @InjectMocks
    private AccountCreatedEventListener listener;

    @Test
    void onClientAccountCreatedEvent_callsSendAccountCreatedConfirmationMail() {
        AccountCreatedEvent event = new AccountCreatedEvent(
                "klijent@test.com", "Jovan", "2220001000000011", "Tekući"
        );

        listener.onClientAccountCreatedEvent(event);

        verify(mailNotificationService).sendAccountCreatedConfirmationMail(
                "klijent@test.com", "Jovan", "2220001000000011", "Tekući"
        );
    }
}
