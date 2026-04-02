package rs.raf.banka2_bek.notification.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.account.event.AccountCreatedEvent;
import rs.raf.banka2_bek.notification.service.MailNotificationService;

@Component
public class AccountCreatedEventListener {

    private final MailNotificationService mailNotificationService;

    public AccountCreatedEventListener(MailNotificationService mailNotificationService) {
        this.mailNotificationService = mailNotificationService;
    }

    @Async
    @EventListener
    public void onClientAccountCreatedEvent(AccountCreatedEvent event) {
        mailNotificationService.sendAccountCreatedConfirmationMail(
                event.getEmail(),
                event.getFirstName(),
                event.getAccountNumber(),
                event.getAccountType()
        );
    }

}