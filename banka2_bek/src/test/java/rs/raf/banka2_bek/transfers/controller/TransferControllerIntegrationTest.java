package rs.raf.banka2_bek.transfers.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.PasswordResetTokenRepository;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.auth.service.JwtService;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.company.repository.CompanyRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.ActivationTokenRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.exchange.dto.CalculateExchangeResponseDto;
import rs.raf.banka2_bek.loan.repository.LoanInstallmentRepository;
import rs.raf.banka2_bek.loan.repository.LoanRepository;
import rs.raf.banka2_bek.loan.repository.LoanRequestRepository;
import rs.raf.banka2_bek.notification.service.MailNotificationService;
import rs.raf.banka2_bek.otp.service.OtpService;
import rs.raf.banka2_bek.card.repository.CardRepository;
import rs.raf.banka2_bek.card.repository.CardRequestRepository;
import rs.raf.banka2_bek.transfers.repository.TransferRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TransferControllerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private ActivationTokenRepository activationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransferRepository transferRepository;
    @Autowired private LoanInstallmentRepository loanInstallmentRepository;
    @Autowired private LoanRepository loanRepository;
    @Autowired private LoanRequestRepository loanRequestRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private CardRequestRepository cardRequestRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ExchangeService exchangeService;
    @MockitoBean private MailNotificationService mailNotificationService;
    @MockitoBean private OtpService otpService;

    @BeforeEach
    void cleanDatabase() {
        // OTP always passes
        when(otpService.verify(anyString(), anyString()))
                .thenReturn(Map.of("verified", true));

        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override public boolean hasError(ClientHttpResponse response) throws IOException { return false; }
        });

        loanInstallmentRepository.deleteAll();
        loanRepository.deleteAll();
        loanRequestRepository.deleteAll();
        cardRequestRepository.deleteAll();
        cardRepository.deleteAll();
        transferRepository.deleteAll();
        accountRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        activationTokenRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();
        clientRepository.deleteAll();
        companyRepository.deleteAll();
        jdbcTemplate.update("delete from currencies");
    }

    // ===== Internal transfer (same currency) =====

    @Test
    void internalTransfer_sameCurrency_completesAndUpdatesBalances() throws Exception {
        Client client = createClient("transfer.same@test.com");
        Employee employee = createEmployee("emp.tsame@test.com", "emp.tsame");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        User user = createAuthUserForClient(client);

        String fromNum = "110000000000000001";
        String toNum = "110000000000000002";
        createAccount(fromNum, client, employee, eur, new BigDecimal("1000.00"));
        createAccount(toNum, client, employee, eur, new BigDecimal("500.00"));

        String payload = """
                {
                  "fromAccountNumber": "%s",
                  "toAccountNumber": "%s",
                  "amount": 200.00,
                  "otpCode": "123456"
                }
                """.formatted(fromNum, toNum);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/transfers/internal"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(body.path("fromAccountNumber").asText()).isEqualTo(fromNum);
        assertThat(body.path("toAccountNumber").asText()).isEqualTo(toNum);
        assertThat(body.path("amount").decimalValue()).isEqualByComparingTo("200.00");

        Account fromAfter = accountRepository.findByAccountNumber(fromNum).orElseThrow();
        Account toAfter = accountRepository.findByAccountNumber(toNum).orElseThrow();
        assertThat(fromAfter.getBalance()).isEqualByComparingTo("800.0000");
        assertThat(toAfter.getBalance()).isEqualByComparingTo("700.0000");
        assertThat(transferRepository.count()).isEqualTo(1);
    }

    // ===== FX transfer (cross-currency) =====

    @Test
    void fxTransfer_crossCurrency_completesWithExchangeRate() throws Exception {
        Client client = createClient("transfer.fx@test.com");
        Employee employee = createEmployee("emp.tfx@test.com", "emp.tfx");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        Currency usd = ensureCurrency("USD", "US Dollar", "$", "US");
        User user = createAuthUserForClient(client);

        // Bank accounts for FX
        Company bank = companyRepository.save(Company.builder()
                .name("Banka 2025 Tim 2").registrationNumber("22200022")
                .taxNumber("222000222").activityCode("64.19").address("Test")
                .build());
        createBankAccount("222000100000000120", bank, employee, eur, new BigDecimal("5000000.00"));
        createBankAccount("222000100000000140", bank, employee, usd, new BigDecimal("5000000.00"));

        String fromNum = "120000000000000001";
        String toNum = "120000000000000002";
        createAccount(fromNum, client, employee, eur, new BigDecimal("2000.00"));
        createAccount(toNum, client, employee, usd, new BigDecimal("500.00"));

        when(exchangeService.calculateCross(300.0, "EUR", "USD"))
                .thenReturn(new CalculateExchangeResponseDto(324.0, 1.08, "EUR", "USD"));

        String payload = """
                {
                  "fromAccountNumber": "%s",
                  "toAccountNumber": "%s",
                  "amount": 300.00,
                  "otpCode": "123456"
                }
                """.formatted(fromNum, toNum);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/transfers/fx"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(body.path("fromCurrency").asText()).isEqualTo("EUR");
        assertThat(body.path("toCurrency").asText()).isEqualTo("USD");

        Account fromAfter = accountRepository.findByAccountNumber(fromNum).orElseThrow();
        Account toAfter = accountRepository.findByAccountNumber(toNum).orElseThrow();

        // 0.5% commission: 300 * 0.005 = 1.50, total debit = 301.50
        assertThat(fromAfter.getBalance()).isEqualByComparingTo("1698.5000");
        // Receiver gets 324 USD
        assertThat(toAfter.getBalance()).isEqualByComparingTo("824.0000");
        assertThat(transferRepository.count()).isEqualTo(1);
    }

    @Test
    void internalTransfer_crossCurrency_autoDetectsFx() throws Exception {
        Client client = createClient("transfer.autofx@test.com");
        Employee employee = createEmployee("emp.autofx@test.com", "emp.autofx");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        Currency usd = ensureCurrency("USD", "US Dollar", "$", "US");
        User user = createAuthUserForClient(client);

        Company bank = companyRepository.save(Company.builder()
                .name("Banka 2025 Tim 2").registrationNumber("22200022")
                .taxNumber("222000222").activityCode("64.19").address("Test")
                .build());
        createBankAccount("222000100000000120", bank, employee, eur, new BigDecimal("5000000.00"));
        createBankAccount("222000100000000140", bank, employee, usd, new BigDecimal("5000000.00"));

        String fromNum = "130000000000000001";
        String toNum = "130000000000000002";
        createAccount(fromNum, client, employee, eur, new BigDecimal("1000.00"));
        createAccount(toNum, client, employee, usd, new BigDecimal("100.00"));

        when(exchangeService.calculateCross(100.0, "EUR", "USD"))
                .thenReturn(new CalculateExchangeResponseDto(108.0, 1.08, "EUR", "USD"));

        // Use /transfers/internal with cross-currency accounts
        String payload = """
                {
                  "fromAccountNumber": "%s",
                  "toAccountNumber": "%s",
                  "amount": 100.00,
                  "otpCode": "123456"
                }
                """.formatted(fromNum, toNum);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/transfers/internal"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("COMPLETED");
        // Auto-detected FX, so commission should be present
        assertThat(body.path("commission").decimalValue()).isGreaterThan(BigDecimal.ZERO);
    }

    // ===== Insufficient funds =====

    @Test
    void internalTransfer_insufficientFunds_returnsBadRequest() throws Exception {
        Client client = createClient("transfer.low@test.com");
        Employee employee = createEmployee("emp.tlow@test.com", "emp.tlow");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        User user = createAuthUserForClient(client);

        String fromNum = "140000000000000001";
        String toNum = "140000000000000002";
        createAccount(fromNum, client, employee, eur, new BigDecimal("50.00"));
        createAccount(toNum, client, employee, eur, new BigDecimal("500.00"));

        String payload = """
                {
                  "fromAccountNumber": "%s",
                  "toAccountNumber": "%s",
                  "amount": 100.00,
                  "otpCode": "123456"
                }
                """.formatted(fromNum, toNum);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/transfers/internal"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Account fromAfter = accountRepository.findByAccountNumber(fromNum).orElseThrow();
        assertThat(fromAfter.getBalance()).isEqualByComparingTo("50.0000");
        assertThat(transferRepository.count()).isEqualTo(0);
    }

    // ===== OTP verification =====

    @Test
    void internalTransfer_invalidOtp_returnsForbidden() throws Exception {
        // Override OTP mock to reject
        when(otpService.verify(anyString(), anyString()))
                .thenReturn(Map.of("verified", false));

        Client client = createClient("transfer.otp@test.com");
        Employee employee = createEmployee("emp.totp@test.com", "emp.totp");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        User user = createAuthUserForClient(client);

        String fromNum = "150000000000000001";
        String toNum = "150000000000000002";
        createAccount(fromNum, client, employee, eur, new BigDecimal("1000.00"));
        createAccount(toNum, client, employee, eur, new BigDecimal("500.00"));

        String payload = """
                {
                  "fromAccountNumber": "%s",
                  "toAccountNumber": "%s",
                  "amount": 100.00,
                  "otpCode": "wrong"
                }
                """.formatted(fromNum, toNum);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/transfers/internal"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(transferRepository.count()).isEqualTo(0);
    }

    @Test
    void internalTransfer_sameAccount_returnsBadRequest() throws Exception {
        Client client = createClient("transfer.same.acc@test.com");
        Employee employee = createEmployee("emp.tsa@test.com", "emp.tsa");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        User user = createAuthUserForClient(client);

        String accNum = "160000000000000001";
        createAccount(accNum, client, employee, eur, new BigDecimal("1000.00"));

        String payload = """
                {
                  "fromAccountNumber": "%s",
                  "toAccountNumber": "%s",
                  "amount": 100.00,
                  "otpCode": "123456"
                }
                """.formatted(accNum, accNum);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/transfers/internal"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void transfer_rejectsUnauthenticated() {
        String payload = """
                {
                  "fromAccountNumber": "111111111111111111",
                  "toAccountNumber": "222222222222222222",
                  "amount": 100.00,
                  "otpCode": "123456"
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/transfers/internal"),
                new HttpEntity<>(payload, jsonHeaders(null)),
                String.class
        );

        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    void getTransfers_returnsTransfersForAuthenticatedClient() throws Exception {
        Client client = createClient("transfer.list@test.com");
        Employee employee = createEmployee("emp.tlist@test.com", "emp.tlist");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        User user = createAuthUserForClient(client);
        String token = jwtService.generateAccessToken(user);

        String fromNum = "170000000000000001";
        String toNum = "170000000000000002";
        createAccount(fromNum, client, employee, eur, new BigDecimal("5000.00"));
        createAccount(toNum, client, employee, eur, new BigDecimal("100.00"));

        // Make two transfers
        for (int i = 0; i < 2; i++) {
            String payload = """
                    {
                      "fromAccountNumber": "%s",
                      "toAccountNumber": "%s",
                      "amount": 50.00,
                      "otpCode": "123456"
                    }
                    """.formatted(fromNum, toNum);
            restTemplate.postForEntity(url("/transfers/internal"),
                    new HttpEntity<>(payload, jsonHeaders(token)), String.class);
        }

        ResponseEntity<String> response = restTemplate.exchange(
                url("/transfers"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.isArray()).isTrue();
        assertThat(body).hasSize(2);
    }

    // ===== Helpers =====

    private HttpHeaders jsonHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) headers.setBearerAuth(bearerToken);
        return headers;
    }

    private String url(String path) { return "http://localhost:" + port + path; }

    private Client createClient(String email) {
        Client c = new Client();
        c.setFirstName("Test"); c.setLastName("User");
        c.setDateOfBirth(LocalDate.of(1995, 1, 1));
        c.setGender("M"); c.setEmail(email);
        c.setPhone("+381600000001"); c.setAddress("Test Address");
        c.setPassword("x"); c.setSaltPassword("salt"); c.setActive(true);
        return clientRepository.save(c);
    }

    private User createAuthUserForClient(Client client) {
        User user = new User();
        user.setFirstName(client.getFirstName());
        user.setLastName(client.getLastName());
        user.setEmail(client.getEmail());
        user.setPassword(client.getPassword());
        user.setActive(true);
        user.setRole("CLIENT");
        return userRepository.save(user);
    }

    private Employee createEmployee(String email, String username) {
        return employeeRepository.save(Employee.builder()
                .firstName("Emp").lastName("Test")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("M").email(email).phone("+381600000000")
                .address("Test").username(username).password("x").saltPassword("salt")
                .position("QA").department("IT").active(true).permissions(Set.of("VIEW_STOCKS"))
                .build());
    }

    private Currency ensureCurrency(String code, String name, String symbol, String country) {
        List<Long> ids = jdbcTemplate.query(
                "select id from currencies where code = ?", (rs, rowNum) -> rs.getLong(1), code);
        Long id;
        if (ids.isEmpty()) {
            jdbcTemplate.update("insert into currencies(code, name, symbol, country, description, active) values (?, ?, ?, ?, ?, ?)",
                    code, name, symbol, country, "test", true);
            id = jdbcTemplate.queryForObject("select id from currencies where code = ?", Long.class, code);
        } else {
            id = ids.get(0);
        }
        return entityManager.getReference(Currency.class, id);
    }

    private Account createAccount(String accountNumber, Client owner, Employee employee, Currency currency, BigDecimal balance) {
        return accountRepository.save(Account.builder()
                .accountNumber(accountNumber).accountType(AccountType.CHECKING)
                .currency(currency).client(owner).employee(employee)
                .status(AccountStatus.ACTIVE).balance(balance).availableBalance(balance)
                .dailyLimit(new BigDecimal("50000.00")).monthlyLimit(new BigDecimal("200000.00"))
                .dailySpending(BigDecimal.ZERO).monthlySpending(BigDecimal.ZERO)
                .build());
    }

    private Account createBankAccount(String accountNumber, Company company, Employee employee, Currency currency, BigDecimal balance) {
        return accountRepository.save(Account.builder()
                .accountNumber(accountNumber).accountType(AccountType.BUSINESS)
                .currency(currency).company(company).employee(employee)
                .status(AccountStatus.ACTIVE).balance(balance).availableBalance(balance)
                .dailyLimit(new BigDecimal("999999999.00")).monthlyLimit(new BigDecimal("999999999.00"))
                .dailySpending(BigDecimal.ZERO).monthlySpending(BigDecimal.ZERO)
                .build());
    }
}
