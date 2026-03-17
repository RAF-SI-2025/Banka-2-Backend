package rs.raf.banka2_bek.payment.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.auth.service.JwtService;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.payment.repository.PaymentRecipientRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentRecipientControllerIntegrationTest {

    private static final String CLIENT_EMAIL = "paymentclient@test.com";

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return rt;
    }

    @Autowired
    private PaymentRecipientRepository paymentRecipientRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        paymentRecipientRepository.deleteAll();
        clientRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * Creates a User (auth) and a Client with the same email, then returns JWT for the client.
     * Payment-recipients API resolves client by email from token, so both must exist.
     */
    private String createClientToken() {
        User user = new User();
        user.setFirstName("Payment");
        user.setLastName("Client");
        user.setEmail(CLIENT_EMAIL);
        user.setPassword("encoded");
        user.setActive(true);
        user.setRole("CLIENT");
        userRepository.save(user);

        Client client = Client.builder()
                .firstName("Payment")
                .lastName("Client")
                .email(CLIENT_EMAIL)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .phone("+381611111111")
                .password("encoded")
                .saltPassword("salt")
                .active(true)
                .build();
        clientRepository.save(client);

        return jwtService.generateAccessToken(user);
    }

    @Test
    void getPaymentRecipients_asClient_returnsOkAndEmptyList() {
        String token = createClientToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/payment-recipients"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("\"content\":[]");
        assertThat(response.getBody()).contains("\"totalElements\":0");
    }

    @Test
    void postPaymentRecipient_asClient_returnsCreated() {
        String token = createClientToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        String payload = """
            {
              "name": "Test Recipient",
              "accountNumber": "123456789012345678"
            }
            """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/payment-recipients"),
                new HttpEntity<>(payload, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).contains("\"name\":\"Test Recipient\"");
        assertThat(response.getBody()).contains("\"accountNumber\":\"123456789012345678\"");
        assertThat(response.getBody()).contains("\"id\":");
    }

    @Test
    void getPaymentRecipients_afterCreate_returnsListWithOne() {
        String token = createClientToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        String payload = "{\"name\":\"Recipient One\",\"accountNumber\":\"111111111111111111\"}";
        restTemplate.postForEntity(url("/payment-recipients"), new HttpEntity<>(payload, headers), String.class);

        ResponseEntity<String> getResponse = restTemplate.exchange(
                url("/payment-recipients"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(OK);
        assertThat(getResponse.getBody()).contains("Recipient One");
        assertThat(getResponse.getBody()).contains("111111111111111111");
    }

    @Test
    void putPaymentRecipient_asClient_updatesAndReturnsOk() {
        String token = createClientToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        String createPayload = "{\"name\":\"Original\",\"accountNumber\":\"123456789012345678\"}";
        ResponseEntity<String> createResponse = restTemplate.postForEntity(
                url("/payment-recipients"), new HttpEntity<>(createPayload, headers), String.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(CREATED);
        long id = extractId(createResponse.getBody());

        String updatePayload = "{\"name\":\"Updated Name\",\"accountNumber\":\"987654321098765432\"}";
        ResponseEntity<String> updateResponse = restTemplate.exchange(
                url("/payment-recipients/" + id),
                HttpMethod.PUT,
                new HttpEntity<>(updatePayload, headers),
                String.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(OK);
        assertThat(updateResponse.getBody()).contains("\"name\":\"Updated Name\"");
        assertThat(updateResponse.getBody()).contains("\"accountNumber\":\"987654321098765432\"");
    }

    @Test
    void deletePaymentRecipient_asClient_returnsNoContent() {
        String token = createClientToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        String createPayload = "{\"name\":\"To Delete\",\"accountNumber\":\"123456789012345678\"}";
        ResponseEntity<String> createResponse = restTemplate.postForEntity(
                url("/payment-recipients"), new HttpEntity<>(createPayload, headers), String.class);
        long id = extractId(createResponse.getBody());

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                url("/payment-recipients/" + id),
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(NO_CONTENT);

        ResponseEntity<String> getResponse = restTemplate.exchange(
                url("/payment-recipients"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );
        assertThat(getResponse.getBody()).contains("\"content\":[]");
        assertThat(getResponse.getBody()).contains("\"totalElements\":0");
    }

    @Test
    void paymentRecipients_withoutAuth_returnsUnauthorized() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/payment-recipients"), String.class);
        // Spring Security may return 401 Unauthorized or 403 Forbidden when no credentials are sent
        assertThat(response.getStatusCode()).isIn(UNAUTHORIZED, FORBIDDEN);
    }

    @Test
    void paymentRecipients_asAdmin_returnsForbidden() {
        User admin = new User();
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setEmail("admin@test.com");
        admin.setPassword("x");
        admin.setActive(true);
        admin.setRole("ADMIN");
        userRepository.save(admin);
        String token = jwtService.generateAccessToken(admin);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = restTemplate.exchange(
                url("/payment-recipients"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(FORBIDDEN);
    }

    @Test
    void postPaymentRecipient_invalidAccountNumber_returnsBadRequest() {
        String token = createClientToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        String payload = "{\"name\":\"Test\",\"accountNumber\":\"short\"}";

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/payment-recipients"),
                new HttpEntity<>(payload, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    private long extractId(String jsonBody) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            return root.get("id").asLong();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract id from: " + jsonBody, e);
        }
    }
}
