package rs.raf.banka2_bek.account.event;

import lombok.Getter;

@Getter
public class AccountCreatedEvent {

    private final String email;
    private final String firstName;
    private final String accountNumber;
    private final String accountType;

    public AccountCreatedEvent(String email, String firstName, String accountNumber, String accountType) {
        this.email = email;
        this.firstName = firstName;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
    }
}
