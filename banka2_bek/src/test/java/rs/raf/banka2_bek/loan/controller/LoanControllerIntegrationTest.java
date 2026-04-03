package rs.raf.banka2_bek.loan.controller;

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
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
import rs.raf.banka2_bek.card.repository.CardRepository;
import rs.raf.banka2_bek.card.repository.CardRequestRepository;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.loan.repository.LoanInstallmentRepository;
import rs.raf.banka2_bek.transfers.repository.TransferRepository;
import rs.raf.banka2_bek.loan.repository.LoanRepository;
import rs.raf.banka2_bek.loan.repository.LoanRequestRepository;
import rs.raf.banka2_bek.notification.service.MailNotificationService;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LoanControllerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());

    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private ActivationTokenRepository activationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private LoanRepository loanRepository;
    @Autowired private LoanRequestRepository loanRequestRepository;
    @Autowired private LoanInstallmentRepository installmentRepository;
    @Autowired private TransferRepository transferRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private CardRequestRepository cardRequestRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ExchangeService exchangeService;
    @MockitoBean private MailNotificationService mailNotificationService;
    @MockitoBean private rs.raf.banka2_bek.otp.service.OtpService otpService;

    @BeforeEach
    void cleanDatabase() {
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override public boolean hasError(ClientHttpResponse response) throws IOException { return false; }
        });

        installmentRepository.deleteAll();
        loanRepository.deleteAll();
        loanRequestRepository.deleteAll();
        transferRepository.deleteAll();
        cardRequestRepository.deleteAll();
        cardRepository.deleteAll();
        accountRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        activationTokenRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();
        clientRepository.deleteAll();
        companyRepository.deleteAll();
        jdbcTemplate.update("delete from currencies");
    }

    // ===== Create loan request =====

    @Test
    void createLoanRequest_returnsCreatedWithPendingStatus() throws Exception {
        Client client = createClient("loan.create@test.com");
        Employee employee = createEmployee("emp.lcreate@test.com", "emp.lcreate");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        createAccount("210000000000000001", client, employee, eur, new BigDecimal("5000.00"));
        User user = createAuthUserForClient(client);

        String payload = """
                {
                  "loanType": "CASH",
                  "interestType": "FIXED",
                  "amount": 100000,
                  "currency": "EUR",
                  "loanPurpose": "Kupovina automobila",
                  "repaymentPeriod": 24,
                  "accountNumber": "210000000000000001",
                  "phoneNumber": "+381601234567",
                  "employmentStatus": "EMPLOYED",
                  "monthlyIncome": 150000,
                  "permanentEmployment": true,
                  "employmentPeriod": 36
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/loans"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("PENDING");
        assertThat(body.path("loanType").asText()).isEqualTo("CASH");
        assertThat(body.path("amount").decimalValue()).isEqualByComparingTo("100000");
        assertThat(body.path("currency").asText()).isEqualTo("EUR");
        assertThat(body.path("clientEmail").asText()).isEqualTo("loan.create@test.com");
        assertThat(body.path("repaymentPeriod").asInt()).isEqualTo(24);
        assertThat(loanRequestRepository.count()).isEqualTo(1);
    }

    @Test
    void createLoanRequest_currencyMismatch_returnsBadRequest() throws Exception {
        Client client = createClient("loan.mismatch@test.com");
        Employee employee = createEmployee("emp.lmismatch@test.com", "emp.lmismatch");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        ensureCurrency("USD", "US Dollar", "$", "US");
        createAccount("210000000000000002", client, employee, eur, new BigDecimal("5000.00"));
        User user = createAuthUserForClient(client);

        // Request USD loan on EUR account
        String payload = """
                {
                  "loanType": "CASH",
                  "interestType": "FIXED",
                  "amount": 50000,
                  "currency": "USD",
                  "loanPurpose": "Test",
                  "repaymentPeriod": 12,
                  "accountNumber": "210000000000000002"
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/loans"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode().value()).isIn(400, 500);
        assertThat(loanRequestRepository.count()).isEqualTo(0);
    }

    // ===== Approve loan request -> creates loan + installments =====

    @Test
    void approveLoanRequest_createsLoanAndInstallmentsAndDisburseFunds() throws Exception {
        Client client = createClient("loan.approve@test.com");
        Employee employee = createEmployee("emp.lapprove@test.com", "emp.lapprove");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        createAccount("220000000000000001", client, employee, eur, new BigDecimal("1000.00"));
        User clientUser = createAuthUserForClient(client);
        User adminUser = createAdminUser("admin.lapprove@test.com");

        // Bank account to disburse from
        Company bank = companyRepository.save(Company.builder()
                .name("Banka 2025 Tim 2").registrationNumber("22200022")
                .taxNumber("222000222").activityCode("64.19").address("Test")
                .build());
        createBankAccount("222000100000000120", bank, employee, eur, new BigDecimal("5000000.00"));

        // Create loan request as client
        String loanPayload = """
                {
                  "loanType": "CASH",
                  "interestType": "FIXED",
                  "amount": 200000,
                  "currency": "EUR",
                  "loanPurpose": "Test kredit",
                  "repaymentPeriod": 12,
                  "accountNumber": "220000000000000001"
                }
                """;

        ResponseEntity<String> createResp = restTemplate.postForEntity(
                url("/loans"),
                new HttpEntity<>(loanPayload, jsonHeaders(jwtService.generateAccessToken(clientUser))),
                String.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long requestId = objectMapper.readTree(createResp.getBody()).path("id").asLong();

        // Approve as admin
        ResponseEntity<String> approveResp = restTemplate.exchange(
                url("/loans/requests/" + requestId + "/approve"),
                HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders(jwtService.generateAccessToken(adminUser))),
                String.class
        );

        assertThat(approveResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode loanBody = objectMapper.readTree(approveResp.getBody());
        assertThat(loanBody.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(loanBody.path("loanNumber").asText()).startsWith("LN-");
        assertThat(loanBody.path("amount").decimalValue()).isEqualByComparingTo("200000");
        assertThat(loanBody.path("repaymentPeriod").asInt()).isEqualTo(12);
        assertThat(loanBody.path("nominalRate").decimalValue()).isGreaterThan(BigDecimal.ZERO);
        assertThat(loanBody.path("monthlyPayment").decimalValue()).isGreaterThan(BigDecimal.ZERO);

        // Verify loan created in DB
        assertThat(loanRepository.count()).isEqualTo(1);
        // Verify installments created (12 months)
        assertThat(installmentRepository.count()).isEqualTo(12);

        // Verify funds disbursed to client account
        Account accountAfter = accountRepository.findByAccountNumber("220000000000000001").orElseThrow();
        assertThat(accountAfter.getBalance()).isEqualByComparingTo("201000.0000"); // 1000 + 200000

        // Verify bank account debited
        Account bankAfter = accountRepository.findByAccountNumber("222000100000000120").orElseThrow();
        assertThat(bankAfter.getBalance()).isEqualByComparingTo("4800000.0000"); // 5000000 - 200000
    }

    @Test
    void approveLoanRequest_alreadyApproved_returnsBadRequest() throws Exception {
        Client client = createClient("loan.double@test.com");
        Employee employee = createEmployee("emp.ldouble@test.com", "emp.ldouble");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        createAccount("220000000000000002", client, employee, eur, new BigDecimal("1000.00"));
        User clientUser = createAuthUserForClient(client);
        User adminUser = createAdminUser("admin.ldouble@test.com");

        Company bank = companyRepository.save(Company.builder()
                .name("Banka 2025 Tim 2").registrationNumber("22200022")
                .taxNumber("222000222").activityCode("64.19").address("Test")
                .build());
        createBankAccount("222000100000000120", bank, employee, eur, new BigDecimal("5000000.00"));

        String loanPayload = """
                {
                  "loanType": "STUDENT",
                  "interestType": "FIXED",
                  "amount": 50000,
                  "currency": "EUR",
                  "repaymentPeriod": 6,
                  "accountNumber": "220000000000000002"
                }
                """;

        ResponseEntity<String> createResp = restTemplate.postForEntity(
                url("/loans"),
                new HttpEntity<>(loanPayload, jsonHeaders(jwtService.generateAccessToken(clientUser))),
                String.class
        );
        Long requestId = objectMapper.readTree(createResp.getBody()).path("id").asLong();

        String adminToken = jwtService.generateAccessToken(adminUser);

        // First approval
        ResponseEntity<String> first = restTemplate.exchange(
                url("/loans/requests/" + requestId + "/approve"), HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders(adminToken)), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second approval should fail
        ResponseEntity<String> second = restTemplate.exchange(
                url("/loans/requests/" + requestId + "/approve"), HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders(adminToken)), String.class);
        assertThat(second.getStatusCode().value()).isIn(400, 500);
    }

    // ===== Reject loan request =====

    @Test
    void rejectLoanRequest_setsStatusToRejected() throws Exception {
        Client client = createClient("loan.reject@test.com");
        Employee employee = createEmployee("emp.lreject@test.com", "emp.lreject");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        createAccount("230000000000000001", client, employee, eur, new BigDecimal("1000.00"));
        User clientUser = createAuthUserForClient(client);
        User adminUser = createAdminUser("admin.lreject@test.com");

        String loanPayload = """
                {
                  "loanType": "AUTO",
                  "interestType": "VARIABLE",
                  "amount": 500000,
                  "currency": "EUR",
                  "loanPurpose": "Auto kredit",
                  "repaymentPeriod": 36,
                  "accountNumber": "230000000000000001"
                }
                """;

        ResponseEntity<String> createResp = restTemplate.postForEntity(
                url("/loans"),
                new HttpEntity<>(loanPayload, jsonHeaders(jwtService.generateAccessToken(clientUser))),
                String.class
        );
        Long requestId = objectMapper.readTree(createResp.getBody()).path("id").asLong();

        ResponseEntity<String> rejectResp = restTemplate.exchange(
                url("/loans/requests/" + requestId + "/reject"),
                HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders(jwtService.generateAccessToken(adminUser))),
                String.class
        );

        assertThat(rejectResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(rejectResp.getBody());
        assertThat(body.path("status").asText()).isEqualTo("REJECTED");

        // No loan or installments should be created
        assertThat(loanRepository.count()).isEqualTo(0);
        assertThat(installmentRepository.count()).isEqualTo(0);
    }

    // ===== Get loans with filters =====

    @Test
    void getAllLoans_returnsPagedResultsWithFilters() throws Exception {
        Client client = createClient("loan.filter@test.com");
        Employee employee = createEmployee("emp.lfilter@test.com", "emp.lfilter");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        createAccount("240000000000000001", client, employee, eur, new BigDecimal("1000.00"));
        createAccount("240000000000000002", client, employee, eur, new BigDecimal("1000.00"));
        User clientUser = createAuthUserForClient(client);
        User adminUser = createAdminUser("admin.lfilter@test.com");

        Company bank = companyRepository.save(Company.builder()
                .name("Banka 2025 Tim 2").registrationNumber("22200022")
                .taxNumber("222000222").activityCode("64.19").address("Test")
                .build());
        createBankAccount("222000100000000120", bank, employee, eur, new BigDecimal("50000000.00"));

        String clientToken = jwtService.generateAccessToken(clientUser);
        String adminToken = jwtService.generateAccessToken(adminUser);

        // Create and approve 2 loans of different types
        Long req1 = createLoanRequest(clientToken, "CASH", 100000, 12, "240000000000000001");
        Long req2 = createLoanRequest(clientToken, "STUDENT", 50000, 24, "240000000000000002");

        restTemplate.exchange(url("/loans/requests/" + req1 + "/approve"), HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders(adminToken)), String.class);
        restTemplate.exchange(url("/loans/requests/" + req2 + "/approve"), HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders(adminToken)), String.class);

        // Filter by loanType=CASH
        ResponseEntity<String> response = restTemplate.exchange(
                url("/loans?loanType=CASH"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(adminToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode content = objectMapper.readTree(response.getBody()).path("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).path("loanType").asText()).isEqualTo("CASH");

        // Filter by status=ACTIVE (both should be active)
        ResponseEntity<String> response2 = restTemplate.exchange(
                url("/loans?status=ACTIVE"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(adminToken)),
                String.class
        );

        JsonNode content2 = objectMapper.readTree(response2.getBody()).path("content");
        assertThat(content2).hasSize(2);
    }

    @Test
    void getLoanRequests_filterByStatus_returnsFiltered() throws Exception {
        Client client = createClient("loan.reqfilter@test.com");
        Employee employee = createEmployee("emp.lreqf@test.com", "emp.lreqf");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        createAccount("250000000000000001", client, employee, eur, new BigDecimal("1000.00"));
        createAccount("250000000000000002", client, employee, eur, new BigDecimal("1000.00"));
        User clientUser = createAuthUserForClient(client);
        User adminUser = createAdminUser("admin.lreqf@test.com");

        String clientToken = jwtService.generateAccessToken(clientUser);
        String adminToken = jwtService.generateAccessToken(adminUser);

        createLoanRequest(clientToken, "CASH", 100000, 12, "250000000000000001");
        Long req2 = createLoanRequest(clientToken, "MORTGAGE", 5000000, 120, "250000000000000002");

        // Reject one
        restTemplate.exchange(url("/loans/requests/" + req2 + "/reject"), HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders(adminToken)), String.class);

        // Get only PENDING
        ResponseEntity<String> response = restTemplate.exchange(
                url("/loans/requests?status=PENDING"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(adminToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode content = objectMapper.readTree(response.getBody()).path("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void getMyLoans_returnsOnlyClientLoans() throws Exception {
        Client client = createClient("loan.my@test.com");
        Employee employee = createEmployee("emp.lmy@test.com", "emp.lmy");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        createAccount("260000000000000001", client, employee, eur, new BigDecimal("1000.00"));
        User clientUser = createAuthUserForClient(client);
        User adminUser = createAdminUser("admin.lmy@test.com");

        Company bank = companyRepository.save(Company.builder()
                .name("Banka 2025 Tim 2").registrationNumber("22200022")
                .taxNumber("222000222").activityCode("64.19").address("Test")
                .build());
        createBankAccount("222000100000000120", bank, employee, eur, new BigDecimal("50000000.00"));

        String clientToken = jwtService.generateAccessToken(clientUser);
        String adminToken = jwtService.generateAccessToken(adminUser);

        Long reqId = createLoanRequest(clientToken, "CASH", 100000, 12, "260000000000000001");
        restTemplate.exchange(url("/loans/requests/" + reqId + "/approve"), HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders(adminToken)), String.class);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/loans/my"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(clientToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode content = objectMapper.readTree(response.getBody()).path("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).path("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void getInstallments_returnsCorrectNumberOfInstallments() throws Exception {
        Client client = createClient("loan.inst@test.com");
        Employee employee = createEmployee("emp.linst@test.com", "emp.linst");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        createAccount("270000000000000001", client, employee, eur, new BigDecimal("1000.00"));
        User clientUser = createAuthUserForClient(client);
        User adminUser = createAdminUser("admin.linst@test.com");

        Company bank = companyRepository.save(Company.builder()
                .name("Banka 2025 Tim 2").registrationNumber("22200022")
                .taxNumber("222000222").activityCode("64.19").address("Test")
                .build());
        createBankAccount("222000100000000120", bank, employee, eur, new BigDecimal("50000000.00"));

        String clientToken = jwtService.generateAccessToken(clientUser);
        String adminToken = jwtService.generateAccessToken(adminUser);

        Long reqId = createLoanRequest(clientToken, "CASH", 60000, 6, "270000000000000001");
        ResponseEntity<String> approveResp = restTemplate.exchange(
                url("/loans/requests/" + reqId + "/approve"), HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders(adminToken)), String.class);

        Long loanId = objectMapper.readTree(approveResp.getBody()).path("id").asLong();

        ResponseEntity<String> response = restTemplate.exchange(
                url("/loans/" + loanId + "/installments"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(clientToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode installments = objectMapper.readTree(response.getBody());
        assertThat(installments.isArray()).isTrue();
        assertThat(installments).hasSize(6);
        // All installments should be unpaid
        for (JsonNode inst : installments) {
            assertThat(inst.path("paid").asBoolean()).isFalse();
            assertThat(inst.path("amount").decimalValue()).isGreaterThan(BigDecimal.ZERO);
        }
    }

    @Test
    void loans_rejectsUnauthenticated() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/loans"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(null)),
                String.class
        );
        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }

    // ===== Helpers =====

    private Long createLoanRequest(String token, String loanType, int amount, int period, String accountNumber) throws Exception {
        String payload = """
                {
                  "loanType": "%s",
                  "interestType": "FIXED",
                  "amount": %d,
                  "currency": "EUR",
                  "repaymentPeriod": %d,
                  "accountNumber": "%s"
                }
                """.formatted(loanType, amount, period, accountNumber);

        ResponseEntity<String> resp = restTemplate.postForEntity(
                url("/loans"),
                new HttpEntity<>(payload, jsonHeaders(token)),
                String.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(resp.getBody()).path("id").asLong();
    }

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

    private User createAdminUser(String email) {
        User user = new User();
        user.setFirstName("Admin"); user.setLastName("Test");
        user.setEmail(email); user.setPassword("x");
        user.setActive(true); user.setRole("ADMIN");
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
