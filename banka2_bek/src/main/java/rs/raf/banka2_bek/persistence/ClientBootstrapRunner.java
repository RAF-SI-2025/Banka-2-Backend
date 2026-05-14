package rs.raf.banka2_bek.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountCategory;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.model.AccountSubtype;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.account.util.AccountNumberUtils;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Bag prijavljen 10.05.2026: korisnici koji su se registrovali kroz
 * stari {@code /auth/register} endpoint nemaju odgovarajuci red u
 * {@code clients} tabeli, a ni jedan racun. Posledica: svaki @Transactional
 * servis koji koristi {@code UserResolver.resolveCurrent} (createOrder, OTC
 * accept, fund invest, ...) puca sa "Could not commit JPA transaction".
 *
 * Novi {@code AuthService.register} (od ove runde nadalje) kreira sav
 * potreban set, ali postojeci registrovani klijenti — Nikola Nikolic, ali i
 * svi koje bi tim mogao tokom KT3 demo-a registrovati pre ovog deploy-a —
 * ostaju zatecitelj problemi. Ovaj runner pri svakom startup-u prolazi kroz
 * {@code users} (role=CLIENT) i za svaki red koji nema odgovarajuci Client
 * zapis — kreira ga + 1 default RSD CHECKING/PERSONAL racun.
 *
 * Idempotent: ako Client vec postoji ili ima makar jedan racun, ne dodaje
 * duplikate. Bezbedno se moze ukloniti kasnije kad backlog usera bude
 * sredjen, ali ostaje koristan kao defense-in-depth.
 *
 * Mozes ga iskljuciti sa {@code banka2.bootstrap.client-migration.enabled=false}
 * ako neko strogo dijagnostikuje problem na svezem nalogu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "banka2.bootstrap.client-migration.enabled",
        havingValue = "true", matchIfMissing = true)
public class ClientBootstrapRunner {

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final CurrencyRepository currencyRepository;
    private final EmployeeRepository employeeRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateRegisteredClients() {
        List<User> clientUsers;
        try {
            clientUsers = userRepository.findAll().stream()
                    .filter(u -> "CLIENT".equalsIgnoreCase(u.getRole()))
                    .toList();
        } catch (Exception e) {
            log.warn("ClientBootstrap: skip migration — UserRepository nije dostupan ({})", e.getMessage());
            return;
        }
        if (clientUsers.isEmpty()) {
            return;
        }

        Optional<Currency> rsdOpt = currencyRepository.findByCode("RSD");
        if (rsdOpt.isEmpty()) {
            log.warn("ClientBootstrap: RSD valuta jos nije seedovana — odlazem migraciju.");
            return;
        }
        Currency rsd = rsdOpt.get();

        Optional<Employee> creatorOpt = employeeRepository.findAll().stream()
                .filter(e -> Boolean.TRUE.equals(e.getActive()))
                .findFirst();
        if (creatorOpt.isEmpty()) {
            log.warn("ClientBootstrap: nema aktivnog zaposlenog — odlazem kreiranje racuna.");
            return;
        }
        Employee creator = creatorOpt.get();

        int createdClients = 0;
        int createdAccounts = 0;
        for (User user : clientUsers) {
            try {
                Optional<Client> clientOpt = clientRepository.findByEmail(user.getEmail());
                Client client;
                if (clientOpt.isPresent()) {
                    client = clientOpt.get();
                } else {
                    client = clientRepository.save(buildClient(user));
                    createdClients++;
                    log.info("ClientBootstrap: kreiran Client zapis za registrovanog korisnika {}", user.getEmail());
                }

                if (clientHasAnyAccount(client)) {
                    continue;
                }
                Account account = buildDefaultAccount(client, rsd, creator);
                accountRepository.save(account);
                createdAccounts++;
                log.info("ClientBootstrap: kreiran default RSD racun #{} za klijenta {}",
                        account.getAccountNumber(), client.getEmail());
            } catch (Exception e) {
                log.warn("ClientBootstrap: greska pri obradi {} ({}). Preskacem.",
                        user.getEmail(), e.getMessage());
            }
        }

        if (createdClients > 0 || createdAccounts > 0) {
            log.info("ClientBootstrap: migration zavrsena — kreirano {} Client zapisa i {} default racuna.",
                    createdClients, createdAccounts);
        }
    }

    private Client buildClient(User user) {
        return Client.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone() != null ? user.getPhone() : "N/A")
                .address(user.getAddress())
                .gender(user.getGender())
                .dateOfBirth(user.getDateOfBirth() != null
                        ? Instant.ofEpochMilli(user.getDateOfBirth()).atZone(ZoneId.systemDefault()).toLocalDate()
                        : LocalDate.of(2000, 1, 1))
                .password(user.getPassword())
                .saltPassword(user.getSaltPassword() != null ? user.getSaltPassword() : "auto")
                .active(user.isActive())
                .build();
    }

    private boolean clientHasAnyAccount(Client client) {
        if (client.getAccounts() != null && !client.getAccounts().isEmpty()) {
            return true;
        }
        return client.getId() != null
                && !accountRepository.findByClientId(client.getId()).isEmpty();
    }

    private Account buildDefaultAccount(Client client, Currency rsd, Employee creator) {
        String accountNumber;
        do {
            accountNumber = AccountNumberUtils.generate(AccountType.CHECKING, AccountSubtype.PERSONAL, false);
        } while (accountRepository.existsByAccountNumber(accountNumber));

        return Account.builder()
                .accountNumber(accountNumber)
                .accountType(AccountType.CHECKING)
                .accountSubtype(AccountSubtype.PERSONAL)
                .currency(rsd)
                .client(client)
                .employee(creator)
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .reservedAmount(BigDecimal.ZERO)
                .accountCategory(AccountCategory.CLIENT)
                .dailyLimit(new BigDecimal("250000"))
                .monthlyLimit(new BigDecimal("1000000"))
                .maintenanceFee(new BigDecimal("255"))
                .status(AccountStatus.ACTIVE)
                .expirationDate(LocalDate.now().plusYears(5))
                .createdAt(LocalDateTime.now())
                .build();
    }
}
