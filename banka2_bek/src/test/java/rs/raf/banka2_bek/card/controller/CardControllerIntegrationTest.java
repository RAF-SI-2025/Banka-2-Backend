package rs.raf.banka2_bek.card.controller;

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
import rs.raf.banka2_bek.card.model.Card;
import rs.raf.banka2_bek.card.model.CardStatus;
import rs.raf.banka2_bek.card.repository.CardRepository;
import rs.raf.banka2_bek.card.repository.CardRequestRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.company.repository.CompanyRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.loan.repository.LoanInstallmentRepository;
import rs.raf.banka2_bek.loan.repository.LoanRepository;
import rs.raf.banka2_bek.loan.repository.LoanRequestRepository;
import rs.raf.banka2_bek.transfers.repository.TransferRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.ActivationTokenRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.notification.service.MailNotificationService;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CardControllerIntegrationTest {

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
    @Autowired private CardRepository cardRepository;
    @Autowired private CardRequestRepository cardRequestRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private LoanInstallmentRepository loanInstallmentRepository;
    @Autowired private LoanRepository loanRepository;
    @Autowired private LoanRequestRepository loanRequestRepository;
    @Autowired private TransferRepository transferRepository;
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

        loanInstallmentRepository.deleteAll();
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

    // ===== Create card tests =====

    @Test
    void createCard_returnsCreatedAndPersistsCard() throws Exception {
        Client client = createClient("card.create@test.com");
        Employee employee = createEmployee("emp.card@test.com", "emp.card");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        Account account = createAccount("100000000000000001", client, employee, eur, new BigDecimal("5000.00"));
        User user = createAuthUserForClient(client);

        String payload = """
                {
                  "accountId": %d,
                  "cardLimit": 50000,
                  "cardType": "VISA"
                }
                """.formatted(account.getId());

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/cards"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(body.path("cardType").asText()).isEqualTo("VISA");
        assertThat(body.path("accountId").asLong()).isEqualTo(account.getId());
        assertThat(body.path("cardNumber").asText()).isNotEmpty();
        assertThat(body.path("cvv").asText()).isNotEmpty(); // full CVV on create
        assertThat(cardRepository.count()).isEqualTo(1);
    }

    @Test
    void createCard_withMasterCard_returnsCreated() throws Exception {
        Client client = createClient("card.mc@test.com");
        Employee employee = createEmployee("emp.mc@test.com", "emp.mc");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        Account account = createAccount("100000000000000002", client, employee, eur, new BigDecimal("5000.00"));
        User user = createAuthUserForClient(client);

        String payload = """
                {
                  "accountId": %d,
                  "cardType": "MASTERCARD"
                }
                """.formatted(account.getId());

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/cards"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("cardType").asText()).isEqualTo("MASTERCARD");
    }

    // ===== Card limit enforcement: max 2 personal, 1 business =====

    @Test
    void createCard_personalAccount_rejectsThirdCard() throws Exception {
        Client client = createClient("card.limit2@test.com");
        Employee employee = createEmployee("emp.limit2@test.com", "emp.limit2");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        Account account = createAccount("200000000000000001", client, employee, eur, new BigDecimal("5000.00"));
        User user = createAuthUserForClient(client);
        String token = jwtService.generateAccessToken(user);

        // Create 2 cards successfully
        for (int i = 0; i < 2; i++) {
            String payload = """
                    { "accountId": %d, "cardType": "VISA" }
                    """.formatted(account.getId());
            ResponseEntity<String> res = restTemplate.postForEntity(
                    url("/cards"),
                    new HttpEntity<>(payload, jsonHeaders(token)),
                    String.class
            );
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        assertThat(cardRepository.count()).isEqualTo(2);

        // Third card should fail
        String payload = """
                { "accountId": %d, "cardType": "VISA" }
                """.formatted(account.getId());
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/cards"),
                new HttpEntity<>(payload, jsonHeaders(token)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Dostignut maksimalan broj kartica");
        assertThat(cardRepository.count()).isEqualTo(2);
    }

    @Test
    void createCard_forNonOwnedAccount_returnsBadRequest() throws Exception {
        Client owner = createClient("card.owner@test.com");
        Client other = createClient("card.other@test.com");
        Employee employee = createEmployee("emp.biz@test.com", "emp.biz");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");

        Account account = createAccount("300000000000000001", owner, employee, eur, new BigDecimal("50000.00"));
        User otherUser = createAuthUserForClient(other);
        String otherToken = jwtService.generateAccessToken(otherUser);

        String payload = """
                { "accountId": %d, "cardType": "VISA" }
                """.formatted(account.getId());
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/cards"), new HttpEntity<>(payload, jsonHeaders(otherToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Nemate pristup ovom racunu");
        assertThat(cardRepository.count()).isEqualTo(0);
    }

    // ===== Block / Unblock / Deactivate =====

    @Test
    void blockCard_returnsBlockedStatus() throws Exception {
        Client client = createClient("card.block@test.com");
        Employee employee = createEmployee("emp.block@test.com", "emp.block");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        Account account = createAccount("400000000000000001", client, employee, eur, new BigDecimal("5000.00"));
        User user = createAuthUserForClient(client);
        String token = jwtService.generateAccessToken(user);

        Long cardId = createCardViaApi(account.getId(), token);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/cards/" + cardId + "/block"),
                HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders(token)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("BLOCKED");

        Card card = cardRepository.findById(cardId).orElseThrow();
        assertThat(card.getStatus()).isEqualTo(CardStatus.BLOCKED);
    }

    @Test
    void blockCard_alreadyBlocked_returnsBadRequest() throws Exception {
        Client client = createClient("card.block2@test.com");
        Employee employee = createEmployee("emp.block2@test.com", "emp.block2");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        Account account = createAccount("400000000000000002", client, employee, eur, new BigDecimal("5000.00"));
        User user = createAuthUserForClient(client);
        String token = jwtService.generateAccessToken(user);

        Long cardId = createCardViaApi(account.getId(), token);

        // Block first time
        restTemplate.exchange(url("/cards/" + cardId + "/block"), HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders(token)), String.class);

        // Block again
        ResponseEntity<String> response = restTemplate.exchange(
                url("/cards/" + cardId + "/block"), HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders(token)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("vec blokirana");
    }

    @Test
    void unblockCard_returnsActiveStatus() throws Exception {
        Client client = createClient("card.unblock@test.com");
        Employee employee = createEmployee("emp.unblock@test.com", "emp.unblock");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        Account account = createAccount("400000000000000003", client, employee, eur, new BigDecimal("5000.00"));
        User clientUser = createAuthUserForClient(client);
        String clientToken = jwtService.generateAccessToken(clientUser);
        User admin = createAdminUser("admin.unblock@test.com");
        String adminToken = jwtService.generateAccessToken(admin);

        Long cardId = createCardViaApi(account.getId(), clientToken);

        // Block as client, then unblock as admin/employee
        restTemplate.exchange(url("/cards/" + cardId + "/block"), HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders(clientToken)), String.class);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/cards/" + cardId + "/unblock"), HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders(adminToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void deactivateCard_returnsPermanentlyDeactivated() throws Exception {
        Client client = createClient("card.deact@test.com");
        Employee employee = createEmployee("emp.deact@test.com", "emp.deact");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        Account account = createAccount("400000000000000004", client, employee, eur, new BigDecimal("5000.00"));
        User clientUser = createAuthUserForClient(client);
        String clientToken = jwtService.generateAccessToken(clientUser);
        User admin = createAdminUser("admin.deact@test.com");
        String adminToken = jwtService.generateAccessToken(admin);

        Long cardId = createCardViaApi(account.getId(), clientToken);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/cards/" + cardId + "/deactivate"), HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders(adminToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("DEACTIVATED");

        // Cannot block a deactivated card
        ResponseEntity<String> blockResp = restTemplate.exchange(
                url("/cards/" + cardId + "/block"), HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders(clientToken)), String.class);
        assertThat(blockResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ===== Get cards by account =====

    @Test
    void getCardsByAccount_returnsOnlyCardsForThatAccount() throws Exception {
        Client client = createClient("card.list@test.com");
        Employee employee = createEmployee("emp.list@test.com", "emp.list");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        Account account1 = createAccount("500000000000000001", client, employee, eur, new BigDecimal("5000.00"));
        Account account2 = createAccount("500000000000000002", client, employee, eur, new BigDecimal("5000.00"));
        User user = createAuthUserForClient(client);
        String token = jwtService.generateAccessToken(user);

        createCardViaApi(account1.getId(), token);
        createCardViaApi(account1.getId(), token);
        createCardViaApi(account2.getId(), token);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/cards/account/" + account1.getId()),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.isArray()).isTrue();
        assertThat(body).hasSize(2);
    }

    @Test
    void getCards_rejectsUnauthenticated() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/cards"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(null)),
                String.class
        );
        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }

    // ===== Deactivated card does not count toward limit =====

    @Test
    void createCard_afterDeactivation_allowsNewCard() throws Exception {
        Client client = createClient("card.reuse@test.com");
        Employee employee = createEmployee("emp.reuse@test.com", "emp.reuse");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        Account account = createAccount("600000000000000001", client, employee, eur, new BigDecimal("5000.00"));
        User clientUser = createAuthUserForClient(client);
        String clientToken = jwtService.generateAccessToken(clientUser);
        User admin = createAdminUser("admin.reuse@test.com");
        String adminToken = jwtService.generateAccessToken(admin);

        Long card1 = createCardViaApi(account.getId(), clientToken);
        createCardViaApi(account.getId(), clientToken);

        // Deactivate one card (requires ADMIN role)
        restTemplate.exchange(url("/cards/" + card1 + "/deactivate"), HttpMethod.PATCH,
                new HttpEntity<>(jsonHeaders(adminToken)), String.class);

        // Now should be able to create a new card (only 1 active remains)
        String payload = """
                { "accountId": %d, "cardType": "VISA" }
                """.formatted(account.getId());
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/cards"), new HttpEntity<>(payload, jsonHeaders(clientToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(cardRepository.count()).isEqualTo(3); // 1 deactivated + 2 active
    }

    // ===== Helpers =====

    private Long createCardViaApi(Long accountId, String token) throws Exception {
        String payload = """
                { "accountId": %d, "cardType": "VISA" }
                """.formatted(accountId);
        ResponseEntity<String> res = restTemplate.postForEntity(
                url("/cards"), new HttpEntity<>(payload, jsonHeaders(token)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(res.getBody()).path("id").asLong();
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
        c.setFirstName("Test");
        c.setLastName("User");
        c.setDateOfBirth(LocalDate.of(1995, 1, 1));
        c.setGender("M");
        c.setEmail(email);
        c.setPhone("+381600000001");
        c.setAddress("Test Address");
        c.setPassword("x");
        c.setSaltPassword("salt");
        c.setActive(true);
        return clientRepository.save(c);
    }

    private User createAdminUser(String email) {
        User user = new User();
        user.setFirstName("Admin");
        user.setLastName("Test");
        user.setEmail(email);
        user.setPassword("x");
        user.setActive(true);
        user.setRole("ADMIN");
        return userRepository.save(user);
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
                .dailyLimit(new BigDecimal("5000.00")).monthlyLimit(new BigDecimal("20000.00"))
                .dailySpending(BigDecimal.ZERO).monthlySpending(BigDecimal.ZERO)
                .build());
    }

}
