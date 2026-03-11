package rs.raf.banka2_bek.notification.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.employee.event.EmployeeAccountCreatedEvent;
import rs.raf.banka2_bek.notification.service.MailNotificationService;

@Component
public class EmployeeAccountCreatedListener {

    private final MailNotificationService mailNotificationService;

    public EmployeeAccountCreatedListener(MailNotificationService mailNotificationService) {
        this.mailNotificationService = mailNotificationService;
    }

    @Async
    @EventListener
    public void onEmployeeAccountCreated(EmployeeAccountCreatedEvent event) {
        mailNotificationService.sendActivationMail(
                event.getEmail(),
                event.getFirstName(),
                event.getActivationToken()
        );
    }
}
