package rs.raf.banka2_bek.acconut.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import rs.raf.banka2_bek.account.dto.AccountResponseDto;
import rs.raf.banka2_bek.account.model.*;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.account.service.implementation.AccountServiceImplementation;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.employee.model.Employee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplementationTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private AccountServiceImplementation accountService;

    private Client testClient;
    private Employee testEmployee;
    private Currency testCurrency;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        testClient = Client.builder()
                .id(1L)
                .firstName("Marko")
                .lastName("Markovic")
                .email("marko@example.com")
                .build();

        testEmployee = Employee.builder()
                .id(1L)
                .firstName("Petar")
                .lastName("Petrovic")
                .build();

        testCurrency = new Currency();
        testCurrency.setId(1L);
        testCurrency.setCode("RSD");

        testAccount = Account.builder()
                .id(1L)
                .name("Tekuci racun")
                .accountNumber("222000112345678910")
                .accountType(AccountType.CHECKING)
                .accountSubtype(AccountSubtype.STANDARD)
                .status(AccountStatus.ACTIVE)
                .client(testClient)
                .employee(testEmployee)
                .currency(testCurrency)
                .balance(new BigDecimal("100000.0000"))
                .availableBalance(new BigDecimal("95000.0000"))
                .dailyLimit(new BigDecimal("250000.0000"))
                .monthlyLimit(new BigDecimal("1000000.0000"))
                .dailySpending(BigDecimal.ZERO)
                .monthlySpending(BigDecimal.ZERO)
                .maintenanceFee(new BigDecimal("255.0000"))
                .expirationDate(LocalDate.of(2030, 1, 1))
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── Helper: simulira ulogovanog korisnika ─────────────────────────────────

    private void mockAuthenticatedUser(String email) {
        UserDetails userDetails = User.builder()
                .username(email)
                .password("password")
                .authorities("ROLE_CLIENT")
                .build();

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GET /accounts/my
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getMyAccounts - vraca listu aktivnih racuna za ulogovanog klijenta")
    void getMyAccounts_success() {
        mockAuthenticatedUser("marko@example.com");

        when(clientRepository.findByEmail("marko@example.com"))
                .thenReturn(Optional.of(testClient));
        when(accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(
                testClient.getId(), AccountStatus.ACTIVE))
                .thenReturn(List.of(testAccount));

        List<AccountResponseDto> result = accountService.getMyAccounts();

        assertNotNull(result);
        assertEquals(1, result.size());

        AccountResponseDto dto = result.get(0);
        assertEquals(1L, dto.getId());
        assertEquals("Tekuci racun", dto.getName());
        assertEquals("222000112345678910", dto.getAccountNumber());
        assertEquals("CHECKING", dto.getAccountType());
        assertEquals("STANDARD", dto.getAccountSubType());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals("Marko Markovic", dto.getOwnerName());
        assertEquals(new BigDecimal("100000.0000"), dto.getBalance());
        assertEquals(new BigDecimal("95000.0000"), dto.getAvailableBalance());
        assertEquals(new BigDecimal("5000.0000"), dto.getReservedFunds());
        assertEquals("RSD", dto.getCurrencyCode());
        assertEquals("Petar Petrovic", dto.getCreatedByEmployee());

        verify(accountRepository).findByClientIdAndStatusOrderByAvailableBalanceDesc(
                testClient.getId(), AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("getMyAccounts - vraca prazan niz ako klijent nema aktivne racune")
    void getMyAccounts_noAccounts() {
        mockAuthenticatedUser("marko@example.com");

        when(clientRepository.findByEmail("marko@example.com"))
                .thenReturn(Optional.of(testClient));
        when(accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(
                testClient.getId(), AccountStatus.ACTIVE))
                .thenReturn(Collections.emptyList());

        List<AccountResponseDto> result = accountService.getMyAccounts();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getMyAccounts - baca izuzetak ako klijent ne postoji u bazi")
    void getMyAccounts_clientNotFound() {
        mockAuthenticatedUser("nepostojeci@example.com");

        when(clientRepository.findByEmail("nepostojeci@example.com"))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getMyAccounts()
        );

        assertTrue(exception.getMessage().contains("nepostojeci@example.com"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GET /accounts/{id}
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getAccountById - vraca detalje racuna za vlasnika")
    void getAccountById_success() {
        mockAuthenticatedUser("marko@example.com");

        when(clientRepository.findByEmail("marko@example.com"))
                .thenReturn(Optional.of(testClient));
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(testAccount));

        AccountResponseDto result = accountService.getAccountById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Tekuci racun", result.getName());
        assertEquals("Marko Markovic", result.getOwnerName());
        assertNull(result.getCompany()); // licni racun, nema firmu

        verify(accountRepository).findById(1L);
    }

    @Test
    @DisplayName("getAccountById - baca izuzetak ako racun ne postoji")
    void getAccountById_notFound() {
        // Ne treba mockAuthenticatedUser jer se izuzetak baci
        // pre nego sto se dodje do provere autentifikacije

        when(accountRepository.findById(999L))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getAccountById(999L)
        );

        assertTrue(exception.getMessage().contains("999"));
    }

    @Test
    @DisplayName("getAccountById - baca izuzetak ako korisnik nije vlasnik racuna")
    void getAccountById_accessDenied() {
        Client otherClient = Client.builder()
                .id(2L)
                .firstName("Jovan")
                .lastName("Jovanovic")
                .email("jovan@example.com")
                .build();

        Account otherAccount = Account.builder()
                .id(2L)
                .name("Tudji racun")
                .accountNumber("222000198765432100")
                .accountType(AccountType.CHECKING)
                .status(AccountStatus.ACTIVE)
                .client(otherClient)
                .employee(testEmployee)
                .currency(testCurrency)
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .dailyLimit(BigDecimal.ZERO)
                .monthlyLimit(BigDecimal.ZERO)
                .dailySpending(BigDecimal.ZERO)
                .monthlySpending(BigDecimal.ZERO)
                .build();

        mockAuthenticatedUser("marko@example.com");

        when(clientRepository.findByEmail("marko@example.com"))
                .thenReturn(Optional.of(testClient));
        when(accountRepository.findById(2L))
                .thenReturn(Optional.of(otherAccount));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> accountService.getAccountById(2L)
        );

        assertTrue(exception.getMessage().contains("do not have access"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  toResponse - poslovni racun sa firmom
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getAccountById - poslovni racun sadrzi podatke o firmi")
    void getAccountById_businessAccount_includesCompanyData() {
        Company testCompany = new Company();
        testCompany.setId(1L);
        testCompany.setName("Test DOO");
        testCompany.setRegistrationNumber("12345678");
        testCompany.setTaxNumber("123456789");
        testCompany.setActivityCode("62.01");
        testCompany.setAddress("Beograd, Srbija");

        Account businessAccount = Account.builder()
                .id(3L)
                .name("Poslovni racun")
                .accountNumber("222000112345678912")
                .accountType(AccountType.CHECKING)
                .accountSubtype(AccountSubtype.STANDARD)
                .status(AccountStatus.ACTIVE)
                .client(testClient)
                .company(testCompany)
                .employee(testEmployee)
                .currency(testCurrency)
                .balance(new BigDecimal("500000.0000"))
                .availableBalance(new BigDecimal("500000.0000"))
                .dailyLimit(new BigDecimal("1000000.0000"))
                .monthlyLimit(new BigDecimal("5000000.0000"))
                .dailySpending(BigDecimal.ZERO)
                .monthlySpending(BigDecimal.ZERO)
                .build();

        mockAuthenticatedUser("marko@example.com");

        when(clientRepository.findByEmail("marko@example.com"))
                .thenReturn(Optional.of(testClient));
        when(accountRepository.findById(3L))
                .thenReturn(Optional.of(businessAccount));

        AccountResponseDto result = accountService.getAccountById(3L);

        assertNotNull(result);
        assertNotNull(result.getCompany());
        assertEquals("Test DOO", result.getCompany().getName());
        assertEquals("12345678", result.getCompany().getRegistrationNumber());
        assertEquals("123456789", result.getCompany().getTaxNumber());
        assertEquals("62.01", result.getCompany().getActivityCode());
        assertEquals("Beograd, Srbija", result.getCompany().getAddress());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Autentifikacija - edge cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getMyAccounts - baca izuzetak ako korisnik nije autentifikovan")
    void getMyAccounts_notAuthenticated() {
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        assertThrows(IllegalStateException.class, () -> accountService.getMyAccounts());
    }
}