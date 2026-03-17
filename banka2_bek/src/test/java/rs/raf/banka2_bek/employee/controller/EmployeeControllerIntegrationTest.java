package rs.raf.banka2_bek.employee.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.PasswordResetTokenRepository;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.auth.service.JwtService;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.employee.repository.ActivationTokenRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EmployeeControllerIntegrationTest {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ActivationTokenRepository activationTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void cleanDatabase() {
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(HttpStatusCode statusCode) {
                return false;
            }
        });
        passwordResetTokenRepository.deleteAll();
        activationTokenRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String createTokenWithRole(String email, String role) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setPassword("x");
        user.setActive(true);
        user.setRole(role);
        userRepository.save(user);
        return jwtService.generateAccessToken(user);
    }

    @Test
    void createEmployeeAsAdminReturnsInactive() throws Exception {
        String token = createTokenWithRole("admin@test.com", "ADMIN");
        String payload = """
            {
              "firstName": "Petar",
              "lastName": "Petrovic",
              "dateOfBirth": "%s",
              "gender": "M",
              "email": "petar@test.com",
              "phone": "+38160111222",
              "address": "Test",
              "username": "petar90",
              "position": "QA",
              "department": "IT",
              "permissions": ["VIEW_STOCKS"]
            }
            """.formatted(LocalDate.of(1990, 5, 20));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            ResponseEntity<String> response = restTemplate.postForEntity(
                url("/employees"),
                new HttpEntity<>(payload, headers),
                String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(CREATED);
            assertThat(response.getBody()).contains("\"active\":false");
    }

    @Test
    void nonAdminCannotAccessEmployees() throws Exception {
            String token = createTokenWithRole("client@test.com", "CLIENT");
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<String> response = restTemplate.exchange(
                url("/employees"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(FORBIDDEN);
    }
}
