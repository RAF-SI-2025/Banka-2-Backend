package rs.raf.banka2_bek.payment.controller;

import org.springframework.context.annotation.Import;
import rs.raf.banka2_bek.TestObjectMapperConfig;
import jakarta.persistence.EntityManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.PasswordResetTokenRepository;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.auth.service.JwtService;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.ActivationTokenRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.company.repository.CompanyRepository;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.exchange.dto.CalculateExchangeResponseDto;
import rs.raf.banka2_bek.exchange.dto.ExchangeRateDto;
import rs.raf.banka2_bek.payment.repository.PaymentAccountRepository;
import rs.raf.banka2_bek.payment.repository.PaymentRepository;
import rs.raf.banka2_bek.transaction.repository.TransactionRepository;
import rs.raf.banka2_bek.IntegrationTestCleanup;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;


@Import(TestObjectMapperConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentControllerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ActivationTokenRepository activationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PaymentAccountRepository paymentAccountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CompanyRepository companyRepository;

    @MockitoBean
    private ExchangeService exchangeService;

    @MockitoBean
    private rs.raf.banka2_bek.otp.service.OtpService otpService;

    @BeforeEach
    void cleanDatabase() {
        when(exchangeService.getAllRates()).thenReturn(fixedRates());
        when(otpService.verify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Map.of("verified", true));

        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });

        IntegrationTestCleanup.truncateAllTables(dataSource);
    }

    @Test
    void createPayment_sameCurrency_returnsCreatedAndPersistsSettlement() {
        Client sender = createClient("sender.same@test.com");
        Client receiver = createClient("receiver.same@test.com");
        Employee employee = createEmployee("employee.same@test.com", "employee.same");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");

        String fromNumber = "111111111111111111";
        String toNumber = "222222222222222222";

        createAccount(fromNumber, sender, employee, eur, new BigDecimal("1000.00"));
        createAccount(toNumber, receiver, employee, eur, new BigDecimal("500.00"));

        String payload = """
                {
                  "fromAccount": "%s",
                  "toAccount": "%s",
                  "amount": 100.00,
                  "paymentCode": "289",
                  "referenceNumber": "REF-1",
                  "description": "Test same-currency payment",
                  "otpCode": "123456"
                }
                """.formatted(fromNumber, toNumber);

        User senderUser = createAuthUserForClient(sender);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/payments"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(senderUser))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"status\":\"COMPLETED\"");
        assertThat(response.getBody()).contains("\"direction\":\"OUTGOING\"");

        Account fromAfter = paymentAccountRepository.findByAccountNumber(fromNumber).orElseThrow();
        Account toAfter = paymentAccountRepository.findByAccountNumber(toNumber).orElseThrow();

        assertThat(fromAfter.getBalance()).isEqualByComparingTo("900.00000");
        assertThat(fromAfter.getAvailableBalance()).isEqualByComparingTo("900.00000");
        assertThat(fromAfter.getDailySpending()).isEqualByComparingTo("100.00");
        assertThat(fromAfter.getMonthlySpending()).isEqualByComparingTo("100.00");

        assertThat(toAfter.getBalance()).isEqualByComparingTo("600.00000");
        assertThat(toAfter.getAvailableBalance()).isEqualByComparingTo("600.00000");

        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void createPayment_crossCurrency_returnsCreatedAndAppliesFeeAndFxRate() {
        Client sender = createClient("sender.fx@test.com");
        Client receiver = createClient("receiver.fx@test.com");
        Employee employee = createEmployee("employee.fx@test.com", "employee.fx");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        Currency usd = ensureCurrency("USD", "US Dollar", "$", "US");

        // Create bank company and bank accounts for EUR and USD
        Company bankCompany = companyRepository.save(Company.builder()
                .name("Banka 2025 Tim 2")
                .registrationNumber("22200022")
                .taxNumber("222000222")
                .activityCode("64.19")
                .address("Test Address")
                .build());

        createBankAccount("222000100000000120", bankCompany, employee, eur, new BigDecimal("5000000.00"));
        createBankAccount("222000100000000140", bankCompany, employee, usd, new BigDecimal("5000000.00"));

        // Mock cross-currency exchange: 100 EUR -> 108 USD
        when(exchangeService.calculateCross(100.0, "EUR", "USD"))
                .thenReturn(new CalculateExchangeResponseDto(108.0, 1.08, "EUR", "USD"));

        String fromNumber = "333333333333333333";
        String toNumber = "444444444444444444";

        createAccount(fromNumber, sender, employee, eur, new BigDecimal("1000.00"));
        createAccount(toNumber, receiver, employee, usd, new BigDecimal("500.00"));

        String payload = """
                {
                  "fromAccount": "%s",
                  "toAccount": "%s",
                  "amount": 100.00,
                  "paymentCode": "289",
                  "referenceNumber": "REF-FX",
                  "description": "Test cross-currency payment",
                  "otpCode": "123456"
                }
                """.formatted(fromNumber, toNumber);

        User senderUser = createAuthUserForClient(sender);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/payments"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(senderUser))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"status\":\"COMPLETED\"");
        assertThat(response.getBody()).contains("\"direction\":\"OUTGOING\"");

        Account fromAfter = paymentAccountRepository.findByAccountNumber(fromNumber).orElseThrow();
        Account toAfter = paymentAccountRepository.findByAccountNumber(toNumber).orElseThrow();

        // 0.5% fee on 100.00 => total debit 100.50
        assertThat(fromAfter.getBalance()).isEqualByComparingTo("899.50000");
        assertThat(fromAfter.getAvailableBalance()).isEqualByComparingTo("899.50000");

        // Receiver gets 108 USD (from exchange mock)
        assertThat(toAfter.getBalance()).isEqualByComparingTo("608.00000");
        assertThat(toAfter.getAvailableBalance()).isEqualByComparingTo("608.00000");

        assertThat(paymentRepository.count()).isEqualTo(1);
        // T2-013 (14.05.2026): cross-currency sad pise 3-fazni lanac (6 transaction redova).
        assertThat(transactionRepository.count()).isEqualTo(6);
    }

    @Test
    void createPayment_rejectsWhenUnauthenticated() {
        String payload = """
                {
                  "fromAccount": "111111111111111111",
                  "toAccount": "222222222222222222",
                  "amount": 100.00,
                  "paymentCode": "289",
                  "description": "No auth"
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/payments"),
                new HttpEntity<>(payload, jsonHeaders(null)),
                String.class
        );

        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    void createPayment_returnsBadRequestWhenPayloadIsInvalid() {
        Client sender = createClient("sender.invalid@test.com");
        Employee employee = createEmployee("employee.invalid@test.com", "employee.invalid");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");

        createAccount("555555555555555555", sender, employee, eur, new BigDecimal("1000.00"));

        // Missing required description
        String payload = """
                {
                  "fromAccount": "555555555555555555",
                  "toAccount": "666666666666666666",
                  "amount": 100.00,
                  "paymentCode": "289"
                }
                """;

        User senderUser = createAuthUserForClient(sender);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/payments"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(senderUser))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Description is required");
    }

    @Test
    void createPayment_returnsBadRequestWhenInsufficientFunds() {
        Client sender = createClient("sender.low@test.com");
        Client receiver = createClient("receiver.low@test.com");
        Employee employee = createEmployee("employee.low@test.com", "employee.low");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");

        String fromNumber = "777777777777777777";
        String toNumber = "888888888888888888";

        createAccount(fromNumber, sender, employee, eur, new BigDecimal("20.00"));
        createAccount(toNumber, receiver, employee, eur, new BigDecimal("500.00"));

        String payload = """
                {
                  "fromAccount": "%s",
                  "toAccount": "%s",
                  "amount": 100.00,
                  "paymentCode": "289",
                  "referenceNumber": "REF-LOW",
                  "description": "Insufficient funds"
                }
                """.formatted(fromNumber, toNumber);

        User senderUser = createAuthUserForClient(sender);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/payments"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(senderUser))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Account fromAfter = paymentAccountRepository.findByAccountNumber(fromNumber).orElseThrow();
        Account toAfter = paymentAccountRepository.findByAccountNumber(toNumber).orElseThrow();

        assertThat(fromAfter.getBalance()).isEqualByComparingTo("20.00");
        assertThat(toAfter.getBalance()).isEqualByComparingTo("500.00");
        assertThat(paymentRepository.count()).isEqualTo(0);
        assertThat(transactionRepository.count()).isEqualTo(0);
    }

    @Test
    void createPayment_returnsBadRequestWhenDestinationAccountDoesNotExist() {
        Client sender = createClient("sender.missing.to@test.com");
        Employee employee = createEmployee("employee.missing.to@test.com", "employee.missing.to");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");

        String fromNumber = "909090909090909090";
        String missingToNumber = "919191919191919191";

        createAccount(fromNumber, sender, employee, eur, new BigDecimal("1000.00"));

        String payload = """
                {
                  "fromAccount": "%s",
                  "toAccount": "%s",
                  "amount": 100.00,
                  "paymentCode": "289",
                  "referenceNumber": "REF-NOT-FOUND",
                  "description": "Destination account missing"
                }
                """.formatted(fromNumber, missingToNumber);

        User senderUser = createAuthUserForClient(sender);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/payments"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(senderUser))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Account fromAfter = paymentAccountRepository.findByAccountNumber(fromNumber).orElseThrow();

        assertThat(fromAfter.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(fromAfter.getAvailableBalance()).isEqualByComparingTo("1000.00");
        assertThat(paymentRepository.count()).isEqualTo(0);
        assertThat(transactionRepository.count()).isEqualTo(0);
    }

    @Test
    void getPayments_returnsOnlyAuthenticatedClientPayments_andAppliesFilters() throws Exception {
        Client sender = createClient("sender.list@test.com");
        Client receiver = createClient("receiver.list@test.com");
        Client outsider = createClient("outsider.list@test.com");
        Employee employee = createEmployee("employee.list@test.com", "employee.list");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");

        String senderFrom = "101010101010101010";
        String senderTo = "202020202020202020";
        String outsiderFrom = "303030303030303030";
        String outsiderTo = "404040404040404040";

        createAccount(senderFrom, sender, employee, eur, new BigDecimal("2000.00"));
        createAccount(senderTo, receiver, employee, eur, new BigDecimal("500.00"));
        createAccount(outsiderFrom, outsider, employee, eur, new BigDecimal("2000.00"));
        createAccount(outsiderTo, receiver, employee, eur, new BigDecimal("500.00"));

        User senderUser = createAuthUserForClient(sender);
        User outsiderUser = createAuthUserForClient(outsider);

        String senderToken = jwtService.generateAccessToken(senderUser);
        String outsiderToken = jwtService.generateAccessToken(outsiderUser);

        postPayment(senderFrom, senderTo, new BigDecimal("100.00"), senderToken, "REF-LIST-1");
        postPayment(senderFrom, senderTo, new BigDecimal("300.00"), senderToken, "REF-LIST-2");
        postPayment(outsiderFrom, outsiderTo, new BigDecimal("250.00"), outsiderToken, "REF-LIST-3");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/payments?minAmount=250&status=COMPLETED"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(senderToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode content = root.path("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content).hasSize(1);
        assertThat(content.get(0).path("fromAccount").asText()).isEqualTo(senderFrom);
        assertThat(content.get(0).path("amount").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(content.get(0).path("direction").asText()).isEqualTo("OUTGOING");
        assertThat(content.get(0).path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void getPaymentHistory_returnsTransactionsAndAppliesTypeAndAmountFilters() throws Exception {
        Client sender = createClient("sender.history@test.com");
        Client receiver = createClient("receiver.history@test.com");
        Employee employee = createEmployee("employee.history@test.com", "employee.history");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");

        String fromAccount = "505050505050505050";
        String toAccount = "606060606060606060";

        createAccount(fromAccount, sender, employee, eur, new BigDecimal("3000.00"));
        createAccount(toAccount, receiver, employee, eur, new BigDecimal("100.00"));

        User senderUser = createAuthUserForClient(sender);

        String token = jwtService.generateAccessToken(senderUser);

        postPayment(fromAccount, toAccount, new BigDecimal("80.00"), token, "REF-HIST-1");
        postPayment(fromAccount, toAccount, new BigDecimal("220.00"), token, "REF-HIST-2");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/payments/history?type=PAYMENT&maxAmount=100"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode content = root.path("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content).hasSize(1);
        assertThat(content.get(0).path("type").asText()).isEqualTo("PAYMENT");
        assertThat(content.get(0).path("direction").asText()).isEqualTo("OUTGOING");
        assertThat(content.get(0).path("debit").decimalValue()).isEqualByComparingTo("80.00");
    }

    @Test
    void getEndpoints_rejectWhenUnauthenticated() {
        ResponseEntity<String> paymentsResponse = restTemplate.exchange(
                url("/payments"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(null)),
                String.class
        );

        ResponseEntity<String> historyResponse = restTemplate.exchange(
                url("/payments/history"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(null)),
                String.class
        );

        assertThat(paymentsResponse.getStatusCode().value()).isIn(401, 403);
        assertThat(historyResponse.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    void getPaymentReceipt_returnsPdfForOwnedTransaction() throws Exception {
        Client sender = createClient("sender.receipt@test.com");
        Client receiver = createClient("receiver.receipt@test.com");
        Employee employee = createEmployee("employee.receipt@test.com", "employee.receipt");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");

        String fromAccount = "717171717171717171";
        String toAccount = "818181818181818181";

        createAccount(fromAccount, sender, employee, eur, new BigDecimal("1000.00"));
        createAccount(toAccount, receiver, employee, eur, new BigDecimal("500.00"));

        User senderUser = createAuthUserForClient(sender);
        String senderToken = jwtService.generateAccessToken(senderUser);

        postPayment(fromAccount, toAccount, new BigDecimal("150.00"), senderToken, "REF-RECEIPT-1");

        ResponseEntity<String> historyResponse = restTemplate.exchange(
                url("/payments/history?type=PAYMENT"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(senderToken)),
                String.class
        );

        JsonNode historyContent = objectMapper.readTree(historyResponse.getBody()).path("content");
        Long transactionId = historyContent.get(0).path("id").asLong();

        ResponseEntity<byte[]> receiptResponse = restTemplate.exchange(
                url("/payments/" + transactionId + "/receipt"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(senderToken)),
                byte[].class
        );

        assertThat(receiptResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(receiptResponse.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(receiptResponse.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("transaction-receipt-" + transactionId + ".pdf");
        assertThat(receiptResponse.getBody()).isNotNull();
        assertThat(receiptResponse.getBody().length).isGreaterThan(100);
        assertThat(new String(receiptResponse.getBody(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void getPaymentReceipt_rejectsWhenTransactionBelongsToAnotherClient() throws Exception {
        Client sender = createClient("sender.receipt.denied@test.com");
        Client receiver = createClient("receiver.receipt.denied@test.com");
        Client outsider = createClient("outsider.receipt.denied@test.com");
        Employee employee = createEmployee("employee.receipt.denied@test.com", "employee.receipt.denied");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");

        String fromAccount = "919191919191919192";
        String toAccount = "929292929292929292";

        createAccount(fromAccount, sender, employee, eur, new BigDecimal("1000.00"));
        createAccount(toAccount, receiver, employee, eur, new BigDecimal("500.00"));

        User senderUser = createAuthUserForClient(sender);
        User outsiderUser = createAuthUserForClient(outsider);

        String senderToken = jwtService.generateAccessToken(senderUser);
        String outsiderToken = jwtService.generateAccessToken(outsiderUser);

        postPayment(fromAccount, toAccount, new BigDecimal("90.00"), senderToken, "REF-RECEIPT-2");

        ResponseEntity<String> historyResponse = restTemplate.exchange(
                url("/payments/history?type=PAYMENT"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(senderToken)),
                String.class
        );

        JsonNode historyContent = objectMapper.readTree(historyResponse.getBody()).path("content");
        Long senderTransactionId = historyContent.get(0).path("id").asLong();

        ResponseEntity<String> deniedResponse = restTemplate.exchange(
                url("/payments/" + senderTransactionId + "/receipt"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(outsiderToken)),
                String.class
        );

        assertThat(deniedResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<String> postPayment(String fromAccount,
                                               String toAccount,
                                               BigDecimal amount,
                                               String token,
                                               String referenceNumber) {
        String payload = """
                {
                  "fromAccount": "%s",
                  "toAccount": "%s",
                  "amount": %s,
                  "paymentCode": "289",
                  "referenceNumber": "%s",
                  "description": "Generated payment",
                  "otpCode": "123456"
                }
                """.formatted(fromAccount, toAccount, amount.toPlainString(), referenceNumber);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/payments"),
                new HttpEntity<>(payload, jsonHeaders(token)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response;
    }

    private HttpHeaders jsonHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Client createClient(String email) {
        Client user = new Client();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setDateOfBirth(LocalDate.of(1995, 1, 1)); // required
        user.setGender("M");                            // optional, but fine to set
        user.setEmail(email);
        user.setPhone("+381600000001");                 // required
        user.setAddress("Test Address");
        user.setPassword("x");
        user.setSaltPassword("salt");                   // required
        user.setActive(true);
        return clientRepository.save(user);
    }

    private User createAuthUserForClient(Client client) {
        User user = new User();
        user.setFirstName(client.getFirstName());
        user.setLastName(client.getLastName());
        user.setEmail(client.getEmail());
        user.setPassword(client.getPassword());
        user.setActive(Boolean.TRUE.equals(client.getActive()));
        user.setRole("CLIENT");
        return userRepository.save(user);
    }

    private Employee createEmployee(String email, String username) {
        Employee employee = Employee.builder()
                .firstName("Emp")
                .lastName("Test")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("M")
                .email(email)
                .phone("+381600000000")
                .address("Test")
                .username(username)
                .password("x")
                .saltPassword("salt")
                .position("QA")
                .department("IT")
                .active(true)
                .permissions(Set.of("VIEW_STOCKS"))
                .build();
        return employeeRepository.save(employee);
    }

    private Currency ensureCurrency(String code, String name, String symbol, String country) {
        List<Long> ids = jdbcTemplate.query(
                "select id from currencies where code = ?",
                (rs, rowNum) -> rs.getLong(1),
                code
        );

        Long id;
        if (ids.isEmpty()) {
            jdbcTemplate.update(
                    "insert into currencies(code, name, symbol, country, description, active) values (?, ?, ?, ?, ?, ?)",
                    code,
                    name,
                    symbol,
                    country,
                    "test",
                    true
            );
            id = jdbcTemplate.queryForObject("select id from currencies where code = ?", Long.class, code);
        } else {
            id = ids.get(0);
        }

        return entityManager.getReference(Currency.class, id);
    }

    private Account createAccount(String accountNumber, Client owner, Employee employee, Currency currency, BigDecimal balance) {
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .accountType(AccountType.CHECKING)
                .currency(currency)
                .client(owner)
                .employee(employee)
                .status(AccountStatus.ACTIVE)
                .balance(balance)
                .availableBalance(balance)
                .dailyLimit(new BigDecimal("5000.00"))
                .monthlyLimit(new BigDecimal("20000.00"))
                .dailySpending(BigDecimal.ZERO)
                .monthlySpending(BigDecimal.ZERO)
                .build();
        return paymentAccountRepository.save(account);
    }

    private Account createBankAccount(String accountNumber, Company company, Employee employee, Currency currency, BigDecimal balance) {
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .accountType(AccountType.BUSINESS)
                .currency(currency)
                .company(company)
                .employee(employee)
                .status(AccountStatus.ACTIVE)
                .balance(balance)
                .availableBalance(balance)
                .dailyLimit(new BigDecimal("999999999.00"))
                .monthlyLimit(new BigDecimal("999999999.00"))
                .dailySpending(BigDecimal.ZERO)
                .monthlySpending(BigDecimal.ZERO)
                .build();
        return paymentAccountRepository.save(account);
    }

    private BigDecimal getFxRate(String from, String to) {
        String f = from.toUpperCase();
        String t = to.toUpperCase();
        if (f.equals(t)) {
            return BigDecimal.ONE;
        }

        Map<String, BigDecimal> rsdToCurrency = new HashMap<>();
        for (ExchangeRateDto rate : exchangeService.getAllRates()) {
            if (rate.getCurrency() != null) {
                rsdToCurrency.put(rate.getCurrency().toUpperCase(), BigDecimal.valueOf(rate.getRate()));
            }
        }

        BigDecimal rsdToFrom = rsdToCurrency.get(f);
        BigDecimal rsdToTo = rsdToCurrency.get(t);

        if (rsdToFrom == null || rsdToTo == null || rsdToFrom.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Unsupported currency pair in test: " + from + "/" + to);
        }

        return rsdToTo.divide(rsdToFrom, 10, RoundingMode.HALF_UP);
    }

    private List<ExchangeRateDto> fixedRates() {
        return List.of(
                new ExchangeRateDto("RSD", 1.0),
                new ExchangeRateDto("EUR", 0.008532423208191127),
                new ExchangeRateDto("USD", 0.009216589861751152),
                new ExchangeRateDto("CHF", 0.008143322475570033),
                new ExchangeRateDto("GBP", 0.00727802037845706),
                new ExchangeRateDto("JPY", 1.36986301369863),
                new ExchangeRateDto("CAD", 0.012484394506866417),
                new ExchangeRateDto("AUD", 0.013966480446927373)
        );
    }
}
