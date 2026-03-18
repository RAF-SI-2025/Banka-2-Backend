package rs.raf.banka2_bek.account.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.account.dto.AccountNumberUtils;
import rs.raf.banka2_bek.account.dto.CreateAccountDto;
import rs.raf.banka2_bek.account.event.AccountCreatedEvent;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.company.model.AuthorizedPerson;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.company.repository.CompanyRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final CurrencyRepository currencyRepository;
    private final EmployeeRepository employeeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Account createAccount(CreateAccountDto dto) {

        Currency currency = currencyRepository.findById(dto.getCurrencyId())
                .orElseThrow(() -> new RuntimeException("Currency not found"));


        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Employee not found"));


        User user = null;
        if (dto.getClientId() != null) {
            // PRAVA IMPLEMENTACIJA (kada proradi UserRepository):
             user = userRepository.findById(dto.getClientId())
                     .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen u tabeli users"));

//            // PRIVREMENA IMPLEMENTACIJA (dok testiraš ili čekaš bazu):
//            user = new User();
//            user.setId(dto.getClientId());
//            user.setFirstName("Marko");
//            user.setLastName("Marković");
//            user.setEmail("marko.klijent@gmail.com");
//            user.setRole("CLIENT"); // Bitno zbog ispravnosti podataka
        }


        Company company = null;
        if (dto.getCompany() != null) {
            // 1. Kreiramo objekat kompanije iz DTO-a
            company = Company.builder()
                    .name(dto.getCompany().getName())
                    .registrationNumber(dto.getCompany().getRegistrationNumber())
                    .taxNumber(dto.getCompany().getTaxId())
                    .activityCode(dto.getCompany().getActivityCode())
                    .address(dto.getCompany().getAddress())
                    .authorizedPersons(new ArrayList<>()) // Inicijalizujemo listu da ne bude null
                    .build();

            // 2. Ako je poslat clientId, taj User postaje ovlašćeno lice (vlasnik) firme
            if (dto.getClientId() != null) {
                // Tražimo postojećeg korisnika u tabeli 'users'
                User owner = userRepository.findById(dto.getClientId())
                        .orElseThrow(() -> new RuntimeException("Korisnik (vlasnik firme) nije pronađen u bazi"));

                // Kreiramo vezu u tabeli 'authorized_persons'
                AuthorizedPerson authPerson = AuthorizedPerson.builder()
                        .user(owner)  // Ovo je sada User entitet
                        .company(company)
                        .createdAt(LocalDateTime.now())
                        .build();

                // Dodajemo u listu kompanije (Hibernate će ovo sačuvati zbog CascadeType.ALL)
                company.getAuthorizedPersons().add(authPerson);
            }

            // 3. Čuvamo kompaniju u bazu pre nego što je dodelimo računu
            company = companyRepository.save(company);
        }


        String typeDigits = determineTypeDigits(dto);
        String accNumber = AccountNumberUtils.generate(typeDigits, "111", "0001");


        Account account = Account.builder()
                .accountNumber(accNumber)
                .accountType(dto.getAccountType())
                .accountSubtype(dto.getAccountSubtype())
                .currency(currency)
                .company(company)
                .user(company != null ? null : user) // AKO JE FIRMA, USER MORA BITI NULL
                .employee(employee)
                .balance(dto.getInitialBalance())
                .availableBalance(dto.getInitialBalance())
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .expirationDate(LocalDate.now().plusYears(5))
                .build();


        Account savedAccount = accountRepository.save(account);


        eventPublisher.publishEvent(new AccountCreatedEvent(this, savedAccount));

        return savedAccount;
    }
    private String determineTypeDigits(CreateAccountDto dto) {
        // 1. Ako je račun DEVIZNI (FOREIGN)
        if (dto.getAccountType() == AccountType.FOREIGN) {
            // Specifikacija: Devizni Lični je 21, Poslovni je 22
            return (dto.getCompany() != null) ? "22" : "21";
        }

        // 2. Ako je račun TEKUĆI
        if (dto.getAccountType() == AccountType.CHECKING) {
            // Ako je popunjena firma, to je poslovni tekući (12)
            if (dto.getCompany() != null) {
                return "12";
            }

            // Ako je lični, gledamo podvrste
            if (dto.getAccountSubtype() != null) {
                return switch (dto.getAccountSubtype()) {
                    case PERSONAL -> "11";
                    case SAVINGS  -> "13";
                    case PENSION  -> "14";
                    case YOUTH    -> "15";
                    // Dodaj ostale po potrebi (npr. STUDENT -> 16, UNEMPLOYED -> 17)
                    default       -> "10";
                };
            }
            return "10"; // Default za tekući ako ništa nije izabrano
        }

        // 3. Sigurnosni default
        return "00";
    }
}

