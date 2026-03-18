package rs.raf.banka2_bek.account.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.company.model.AuthorizedPerson;

@Getter
public class AccountCreatedEvent extends ApplicationEvent {

    private final String email;
    private final String firstName;
    private final String accountNumber;
    private final String accountType; // npr. "Tekući" ili "Devizni"

    public AccountCreatedEvent(Object source, Account account) {
        super(source);

        if (account.getUser() != null) {
            // Lični račun - direktno klijent
            this.email = account.getUser().getEmail();
            this.firstName = account.getUser().getFirstName();
        }
        else if (account.getCompany() != null && !account.getCompany().getAuthorizedPersons().isEmpty()) {
            // Poslovni račun - uzimamo prvo ovlašćeno lice iz firme
            AuthorizedPerson firstPerson = account.getCompany().getAuthorizedPersons().get(0);
            this.email = firstPerson.getUser().getEmail();
            this.firstName = firstPerson.getUser().getFirstName();
        }
        else {
            // Fallback ako nema ni klijenta ni ovlašćenih lica
            this.email = "info@banka.rs";
            this.firstName = "Poštovani";
        }
        this.accountNumber = account.getAccountNumber();
        this.accountType = account.getAccountType().toString();
    }
}