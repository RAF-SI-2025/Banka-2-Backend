package rs.raf.banka2_bek.account.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import rs.raf.banka2_bek.account.dto.CreateAccountDto;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.model.AccountSubtype;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.currency.model.Currency;
//import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;

    @InjectMocks
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        // Simulacija ulogovanog zaposlenog
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("test@employee.com");
    }

    @Test
    void createAccount_Success_PersonalChecking() {
        // Given
        CreateAccountDto dto = new CreateAccountDto();
        dto.setAccountType(AccountType.CHECKING);
        dto.setAccountSubtype(AccountSubtype.PERSONAL);
        dto.setCurrencyId(1L);
        dto.setInitialBalance(BigDecimal.valueOf(1000));

        Currency currency = new Currency();
        Employee employee = new Employee();
        employee.setEmail("test@employee.com");

        when(currencyRepository.findById(1L)).thenReturn(Optional.of(currency));
        when(employeeRepository.findByEmail(anyString())).thenReturn(Optional.of(employee));
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArguments()[0]);

        // When
        Account savedAccount = accountService.createAccount(dto);

        // Then
        assertNotNull(savedAccount);
        assertTrue(savedAccount.getAccountNumber().endsWith("11")); // Lični Checking kraj sa 11
        verify(eventPublisher, times(1)).publishEvent(any());
        verify(accountRepository, times(1)).save(any());
    }
}