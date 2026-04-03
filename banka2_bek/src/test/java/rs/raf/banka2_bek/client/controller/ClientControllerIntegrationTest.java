package rs.raf.banka2_bek.client.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.PasswordResetTokenRepository;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.auth.service.JwtService;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.repository.ActivationTokenRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.notification.service.MailNotificationService;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ClientControllerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private ActivationTokenRepository activationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ExchangeService exchangeService;
    @MockitoBean private MailNotificationService mailNotificationService;
    @MockitoBean private rs.raf.banka2_bek.otp.service.OtpService otpService;

    @BeforeEach
    void cleanDatabase() {
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override public boolean hasError(ClientHttpResponse response) throws IOException { return false; }
        });

        passwordResetTokenRepository.deleteAll();
        activationTokenRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();
        clientRepository.deleteAll();
    }

    // ===== Create client =====

    @Test
    void createClient_returnsCreatedAndPersists() throws Exception {
        User admin = createAdminUser("admin.cc@test.com");
        String token = jwtService.generateAccessToken(admin);

        String payload = """
                {
                  "firstName": "Marko",
                  "lastName": "Petrovic",
                  "dateOfBirth": "1990-05-15",
                  "gender": "M",
                  "email": "marko.petrovic@test.com",
                  "phone": "+381601234567",
                  "address": "Beograd, Knez Mihailova 1",
                  "password": "Test12345"
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/clients"),
                new HttpEntity<>(payload, jsonHeaders(token)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("firstName").asText()).isEqualTo("Marko");
        assertThat(body.path("lastName").asText()).isEqualTo("Petrovic");
        assertThat(body.path("email").asText()).isEqualTo("marko.petrovic@test.com");
        assertThat(body.path("active").asBoolean()).isTrue();
        assertThat(body.path("id").asLong()).isPositive();

        // Verify persisted in DB
        assertThat(clientRepository.findByEmail("marko.petrovic@test.com")).isPresent();
        // Verify User entry created for login
        assertThat(userRepository.findByEmail("marko.petrovic@test.com")).isPresent();
    }

    @Test
    void createClient_withMinimalFields_returnsCreated() throws Exception {
        User admin = createAdminUser("admin.min@test.com");
        String token = jwtService.generateAccessToken(admin);

        String payload = """
                {
                  "firstName": "Ana",
                  "lastName": "Jovic",
                  "email": "ana.jovic@test.com"
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/clients"),
                new HttpEntity<>(payload, jsonHeaders(token)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("firstName").asText()).isEqualTo("Ana");
    }

    // ===== Email uniqueness =====

    @Test
    void createClient_duplicateEmail_returnsBadRequest() throws Exception {
        User admin = createAdminUser("admin.dup@test.com");
        String token = jwtService.generateAccessToken(admin);

        String payload = """
                {
                  "firstName": "Nikola",
                  "lastName": "Jovanovic",
                  "email": "nikola@test.com"
                }
                """;

        ResponseEntity<String> first = restTemplate.postForEntity(
                url("/clients"), new HttpEntity<>(payload, jsonHeaders(token)), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Same email again
        ResponseEntity<String> second = restTemplate.postForEntity(
                url("/clients"), new HttpEntity<>(payload, jsonHeaders(token)), String.class);

        assertThat(second.getStatusCode().value()).isIn(400, 409, 500);
        assertThat(clientRepository.count()).isEqualTo(1);
    }

    @Test
    void createClient_missingRequiredFields_returnsBadRequest() throws Exception {
        User admin = createAdminUser("admin.val@test.com");
        String token = jwtService.generateAccessToken(admin);

        // Missing firstName and email
        String payload = """
                {
                  "lastName": "Test"
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/clients"), new HttpEntity<>(payload, jsonHeaders(token)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ===== Get clients with filters =====

    @Test
    void getClients_returnsPagedResultsWithFilters() throws Exception {
        User admin = createAdminUser("admin.filter@test.com");
        String token = jwtService.generateAccessToken(admin);

        // Create 3 clients
        createClientViaApi(token, "Ivan", "Markovic", "ivan.markovic@test.com");
        createClientViaApi(token, "Ivana", "Nikolic", "ivana.nikolic@test.com");
        createClientViaApi(token, "Petar", "Petrovic", "petar.petrovic@test.com");

        // Filter by firstName=Ivan (should match "Ivan" and "Ivana")
        ResponseEntity<String> response = restTemplate.exchange(
                url("/clients?firstName=Ivan"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode content = root.path("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content).hasSize(2);

        // Filter by lastName=Petrovic
        ResponseEntity<String> response2 = restTemplate.exchange(
                url("/clients?lastName=Petrovic"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                String.class
        );

        JsonNode content2 = objectMapper.readTree(response2.getBody()).path("content");
        assertThat(content2).hasSize(1);
        assertThat(content2.get(0).path("lastName").asText()).isEqualTo("Petrovic");
    }

    @Test
    void getClients_filterByEmail_returnsMatch() throws Exception {
        User admin = createAdminUser("admin.email@test.com");
        String token = jwtService.generateAccessToken(admin);

        createClientViaApi(token, "Zoran", "Todorovic", "zoran@test.com");
        createClientViaApi(token, "Jelena", "Stojanovic", "jelena@test.com");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/clients?email=zoran"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                String.class
        );

        JsonNode content = objectMapper.readTree(response.getBody()).path("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).path("email").asText()).isEqualTo("zoran@test.com");
    }

    @Test
    void getClients_pagination_works() throws Exception {
        User admin = createAdminUser("admin.page@test.com");
        String token = jwtService.generateAccessToken(admin);

        for (int i = 0; i < 5; i++) {
            createClientViaApi(token, "User" + i, "Last" + i, "user" + i + "@test.com");
        }

        ResponseEntity<String> response = restTemplate.exchange(
                url("/clients?page=0&limit=2"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                String.class
        );

        JsonNode root = objectMapper.readTree(response.getBody());
        assertThat(root.path("content")).hasSize(2);
        assertThat(root.path("totalElements").asInt()).isEqualTo(5);
        assertThat(root.path("totalPages").asInt()).isEqualTo(3);
    }

    // ===== Update client =====

    @Test
    void updateClient_updatesFieldsAndSyncsUser() throws Exception {
        User admin = createAdminUser("admin.upd@test.com");
        String token = jwtService.generateAccessToken(admin);

        ResponseEntity<String> createResp = createClientViaApi(token, "Stara", "Vrednost", "update.me@test.com");
        Long clientId = objectMapper.readTree(createResp.getBody()).path("id").asLong();

        String updatePayload = """
                {
                  "firstName": "Nova",
                  "lastName": "Vrednost",
                  "phone": "+381699999999",
                  "address": "Novi Sad, Bulevar Oslobodjenja 5"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/clients/" + clientId),
                HttpMethod.PUT,
                new HttpEntity<>(updatePayload, jsonHeaders(token)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("firstName").asText()).isEqualTo("Nova");
        assertThat(body.path("phone").asText()).isEqualTo("+381699999999");
        assertThat(body.path("address").asText()).isEqualTo("Novi Sad, Bulevar Oslobodjenja 5");

        // Verify user table also synced
        User user = userRepository.findByEmail("update.me@test.com").orElseThrow();
        assertThat(user.getFirstName()).isEqualTo("Nova");
        assertThat(user.getPhone()).isEqualTo("+381699999999");
    }

    @Test
    void updateClient_partialUpdate_onlyChangesProvidedFields() throws Exception {
        User admin = createAdminUser("admin.partial@test.com");
        String token = jwtService.generateAccessToken(admin);

        ResponseEntity<String> createResp = createClientViaApi(token, "Dragan", "Milosevic", "dragan@test.com");
        Long clientId = objectMapper.readTree(createResp.getBody()).path("id").asLong();

        // Only update address
        String updatePayload = """
                { "address": "Nis, Generala Milojka Lesjanina 12" }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/clients/" + clientId),
                HttpMethod.PUT,
                new HttpEntity<>(updatePayload, jsonHeaders(token)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("firstName").asText()).isEqualTo("Dragan"); // unchanged
        assertThat(body.path("address").asText()).isEqualTo("Nis, Generala Milojka Lesjanina 12");
    }

    @Test
    void getClientById_returnsCorrectClient() throws Exception {
        User admin = createAdminUser("admin.getid@test.com");
        String token = jwtService.generateAccessToken(admin);

        ResponseEntity<String> createResp = createClientViaApi(token, "Lazar", "Djordjevic", "lazar@test.com");
        Long clientId = objectMapper.readTree(createResp.getBody()).path("id").asLong();

        ResponseEntity<String> response = restTemplate.exchange(
                url("/clients/" + clientId),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("firstName").asText()).isEqualTo("Lazar");
        assertThat(body.path("email").asText()).isEqualTo("lazar@test.com");
    }

    @Test
    void getClients_rejectsUnauthenticated() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/clients"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(null)),
                String.class
        );
        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }

    // ===== Helpers =====

    private ResponseEntity<String> createClientViaApi(String token, String firstName, String lastName, String email) {
        String payload = """
                {
                  "firstName": "%s",
                  "lastName": "%s",
                  "email": "%s"
                }
                """.formatted(firstName, lastName, email);
        ResponseEntity<String> resp = restTemplate.postForEntity(
                url("/clients"), new HttpEntity<>(payload, jsonHeaders(token)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp;
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

    private HttpHeaders jsonHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) headers.setBearerAuth(bearerToken);
        return headers;
    }

    private String url(String path) { return "http://localhost:" + port + path; }
}
