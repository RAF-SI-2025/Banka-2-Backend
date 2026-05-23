package rs.raf.banka2_bek.notification.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import rs.raf.banka2_bek.notification.event.InAppNotificationEvent;
import rs.raf.banka2_bek.notification.model.NotificationType;
import rs.raf.banka2_bek.notification.template.AccountCreatedConfirmationEmailTemplate;
import rs.raf.banka2_bek.notification.template.AccountLockedEmailTemplate;
import rs.raf.banka2_bek.notification.template.ActivationConfirmedEmailTemplate;
import rs.raf.banka2_bek.notification.template.ActivationEmailTemplate;
import rs.raf.banka2_bek.notification.template.OtpEmailTemplate;
import rs.raf.banka2_bek.notification.template.PasswordResetEmailTemplate;
import rs.raf.banka2_bek.notification.template.TransactionEmailTemplate;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Tests focused on sendInAppNotificationMail() — uses a real MimeMessage (not a mock)
// so the rendered HTML content can be inspected for correct greeting logic.
@ExtendWith(MockitoExtension.class)
class MailSenderServiceInAppNotificationTest {

    @Mock private PasswordResetEmailTemplate passwordResetEmailTemplate;
    @Mock private ActivationEmailTemplate activationEmailTemplate;
    @Mock private ActivationConfirmedEmailTemplate activationConfirmedEmailTemplate;
    @Mock private AccountCreatedConfirmationEmailTemplate accountCreatedConfirmationEmailTemplate;
    @Mock private OtpEmailTemplate otpEmailTemplate;
    @Mock private TransactionEmailTemplate transactionEmailTemplate;
    @Mock private AccountLockedEmailTemplate accountLockedEmailTemplate;

    private JavaMailSender mailSender;
    private MimeMessage realMessage;
    private MailSenderService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        realMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(realMessage);

        service = new MailSenderService(mailSender,
                passwordResetEmailTemplate, accountLockedEmailTemplate, activationEmailTemplate,
                activationConfirmedEmailTemplate, accountCreatedConfirmationEmailTemplate,
                otpEmailTemplate, transactionEmailTemplate,
                "from@test.com", "http://localhost", "/reset",
                "http://localhost", "/activate");
    }

    @Test
    void sendInAppNotificationMail_sendsEmail() {
        service.sendInAppNotificationMail(event("Marko"));

        verify(mailSender).send(realMessage);
    }

    @Test
    void sendInAppNotificationMail_usesPersonalizedGreetingWhenFirstNameIsPresent() throws Exception {
        service.sendInAppNotificationMail(event("Marko"));

        String html = (String) realMessage.getContent();
        assertThat(html).contains("Poštovani Marko,");
    }

    @Test
    void sendInAppNotificationMail_usesGenericGreetingWhenFirstNameIsNull() throws Exception {
        service.sendInAppNotificationMail(event(null));

        String html = (String) realMessage.getContent();
        assertThat(html).contains("Poštovani,");
        assertThat(html).doesNotContain("null");
    }

    @Test
    void sendInAppNotificationMail_usesGenericGreetingWhenFirstNameIsBlank() throws Exception {
        service.sendInAppNotificationMail(event("   "));

        String html = (String) realMessage.getContent();
        assertThat(html).contains("Poštovani,");
    }

    private InAppNotificationEvent event(String firstName) {
        return InAppNotificationEvent.builder()
                .recipientEmail("u@b.rs")
                .firstName(firstName)
                .notificationType(NotificationType.GENERAL)
                .title("Test naslov")
                .body("Telo poruke")
                .build();
    }
}
