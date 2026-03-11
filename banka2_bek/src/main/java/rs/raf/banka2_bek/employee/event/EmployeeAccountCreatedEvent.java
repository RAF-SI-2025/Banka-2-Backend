package rs.raf.banka2_bek.employee.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class EmployeeAccountCreatedEvent extends ApplicationEvent {

    private final String email;
    private final String firstName;
    private final String activationToken;

    public EmployeeAccountCreatedEvent(Object source, String email, String firstName, String activationToken) {
        super(source);
        this.email = email;
        this.firstName = firstName;
        this.activationToken = activationToken;
    }
}
