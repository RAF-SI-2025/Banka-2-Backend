package rs.raf.banka2_bek.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pokriva put ClientBootstrapRunner-a za bag prijavljen 10.05.2026 — registrovani
 * klijenti bez Client zapisa puknu sa "Could not commit JPA transaction" pri
 * BUY/OTC accept/fund invest. Migration se okida na ApplicationReadyEvent
 * (prozor pre nego sto bilo koji request stigne).
 */
@ExtendWith(MockitoExtension.class)
class ClientBootstrapRunnerTest {

    @Mock private UserRepository userRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private EmployeeRepository employeeRepository;

    @InjectMocks
    private ClientBootstrapRunner runner;

    private User clientUser;
    private Employee creator;
    private Currency rsd;

    @BeforeEach
    void setUp() {
        clientUser = new User();
        clientUser.setId(50L);
        clientUser.setFirstName("Nikola");
        clientUser.setLastName("Nikolic");
        clientUser.setEmail("nikola.nikolic@test.rs");
        clientUser.setPassword("$2b$10$encoded");
        clientUser.setSaltPassword("salt");
        clientUser.setActive(true);
        clientUser.setRole("CLIENT");
        clientUser.setPhone("+381601112222");
        clientUser.setAddress("Beograd");
        clientUser.setGender("M");

        creator = Employee.builder()
                .id(1L)
                .firstName("Marko")
                .lastName("Petrovic")
                .email("marko@banka.rs")
                .password("p")
                .saltPassword("s")
                .active(true)
                .dateOfBirth(LocalDate.of(1985, 5, 20))
                .gender("M")
                .phone("+381601111111")
                .address("Beograd")
                .username("marko")
                .position("Direktor")
                .department("Uprava")
                .permissions(Set.of("ADMIN"))
                .build();

        rsd = new Currency();
        rsd.setCode("RSD");
        rsd.setName("Serbian Dinar");
        rsd.setSymbol("RSD");
        rsd.setCountry("Serbia");
        rsd.setActive(true);
    }

    @Test
    void doesNothingWhenNoUsersExist() {
        when(userRepository.findAll()).thenReturn(List.of());

        runner.migrateRegisteredClients();

        verify(clientRepository, never()).save(any(Client.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void skipsWhenRsdMissing() {
        when(userRepository.findAll()).thenReturn(List.of(clientUser));
        when(currencyRepository.findByCode("RSD")).thenReturn(Optional.empty());

        runner.migrateRegisteredClients();

        verify(clientRepository, never()).save(any(Client.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void skipsWhenNoActiveEmployee() {
        when(userRepository.findAll()).thenReturn(List.of(clientUser));
        when(currencyRepository.findByCode("RSD")).thenReturn(Optional.of(rsd));
        when(employeeRepository.findAll()).thenReturn(List.of());

        runner.migrateRegisteredClients();

        verify(clientRepository, never()).save(any(Client.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createsClientAndAccountForRegisteredUserWithoutClientRow() {
        when(userRepository.findAll()).thenReturn(List.of(clientUser));
        when(currencyRepository.findByCode("RSD")).thenReturn(Optional.of(rsd));
        when(employeeRepository.findAll()).thenReturn(List.of(creator));
        when(clientRepository.findByEmail(clientUser.getEmail())).thenReturn(Optional.empty());
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            c.setId(99L);
            return c;
        });
        when(accountRepository.findByClientId(99L)).thenReturn(List.of());
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.migrateRegisteredClients();

        ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(clientCaptor.capture());
        Client savedClient = clientCaptor.getValue();
        assertThat(savedClient.getEmail()).isEqualTo(clientUser.getEmail());
        assertThat(savedClient.getFirstName()).isEqualTo("Nikola");
        assertThat(savedClient.getLastName()).isEqualTo("Nikolic");

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getCurrency().getCode()).isEqualTo("RSD");
        assertThat(savedAccount.getEmployee()).isEqualTo(creator);
        assertThat(savedAccount.getAccountNumber()).hasSize(18);
    }

    @Test
    void skipsAccountWhenClientAlreadyHasOne() {
        Client existingClient = Client.builder()
                .id(99L)
                .email(clientUser.getEmail())
                .firstName("Nikola")
                .lastName("Nikolic")
                .password("p")
                .saltPassword("s")
                .active(true)
                .build();

        Account existingAccount = Account.builder()
                .id(1L)
                .accountNumber("222000100000000010")
                .build();

        when(userRepository.findAll()).thenReturn(List.of(clientUser));
        when(currencyRepository.findByCode("RSD")).thenReturn(Optional.of(rsd));
        when(employeeRepository.findAll()).thenReturn(List.of(creator));
        when(clientRepository.findByEmail(clientUser.getEmail())).thenReturn(Optional.of(existingClient));
        when(accountRepository.findByClientId(99L)).thenReturn(List.of(existingAccount));

        runner.migrateRegisteredClients();

        verify(clientRepository, never()).save(any(Client.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void ignoresNonClientUsers() {
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@banka.rs");
        adminUser.setRole("ADMIN");
        adminUser.setActive(true);

        when(userRepository.findAll()).thenReturn(List.of(adminUser));

        runner.migrateRegisteredClients();

        verify(clientRepository, never()).save(any(Client.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void swallowsExceptionPerUserWithoutAbortingOthers() {
        User secondUser = new User();
        secondUser.setId(51L);
        secondUser.setFirstName("Marko");
        secondUser.setLastName("Markovic");
        secondUser.setEmail("marko.markovic@test.rs");
        secondUser.setPassword("$2b$10$encoded");
        secondUser.setActive(true);
        secondUser.setRole("CLIENT");
        secondUser.setGender("M");

        when(userRepository.findAll()).thenReturn(List.of(clientUser, secondUser));
        when(currencyRepository.findByCode("RSD")).thenReturn(Optional.of(rsd));
        when(employeeRepository.findAll()).thenReturn(List.of(creator));
        when(clientRepository.findByEmail(clientUser.getEmail()))
                .thenThrow(new RuntimeException("Bus error u DB pri findByEmail-u za prvog usera"));
        when(clientRepository.findByEmail(secondUser.getEmail())).thenReturn(Optional.empty());
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });
        when(accountRepository.findByClientId(100L)).thenReturn(List.of());
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.migrateRegisteredClients();

        // Drugi user prosao normalno uprkos prvom failure-u
        verify(clientRepository).save(any(Client.class));
        verify(accountRepository).save(any(Account.class));
    }
}
