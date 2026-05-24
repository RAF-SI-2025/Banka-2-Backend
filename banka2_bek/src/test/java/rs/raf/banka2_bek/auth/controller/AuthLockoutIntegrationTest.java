package rs.raf.banka2_bek.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.IntegrationTestCleanup;
import rs.raf.banka2_bek.auth.model.PasswordResetToken;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.PasswordResetTokenRepository;
import rs.raf.banka2_bek.auth.repository.UserRepository;

import javax.sql.DataSource;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthLockoutIntegrationTest {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void cleanDatabase() {
        IntegrationTestCleanup.truncateAllTables(dataSource);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpEntity<String> loginBody(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}",
                headers
        );
    }

    private void assertUnauthorizedLogin(String email, String password) {
        assertThatThrownBy(() -> restTemplate.postForEntity(
                url("/auth/login"), loginBody(email, password), String.class))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    private void assertLockedLoginResponse(String email, String password) {
        assertThatThrownBy(() -> restTemplate.postForEntity(
                url("/auth/login"), loginBody(email, password), String.class))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class)
                .satisfies(ex -> {
                    String body = ((HttpClientErrorException) ex).getResponseBodyAsString();
                    if (body != null && !body.isBlank()) {
                        assertThat(body).contains("Nalog je privremeno zakljucan");
                    }
                });

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getAccountLockedUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    void fiveFailedLogins_lockAccountAndRejectWhileLocked() {
        User user = new User();
        user.setEmail("lock@test.com");
        user.setPassword(passwordEncoder.encode("CorrectPass12"));
        user.setFirstName("Lock");
        user.setLastName("Test");
        user.setActive(true);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        for (int i = 0; i < 4; i++) {
            assertUnauthorizedLogin("lock@test.com", "wrong");
        }

        assertLockedLoginResponse("lock@test.com", "wrong");

        assertThatThrownBy(() -> restTemplate.postForEntity(
                url("/auth/login"), loginBody("lock@test.com", "CorrectPass12"), String.class))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    void passwordReset_unlocksAccountAndAllowsLogin() {
        User user = new User();
        user.setEmail("unlock@test.com");
        user.setPassword(passwordEncoder.encode("OldPass12"));
        user.setFirstName("Unlock");
        user.setLastName("Test");
        user.setActive(true);
        user.setFailedLoginAttempts(5);
        user.setAccountLockedUntil(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        PasswordResetToken token = new PasswordResetToken();
        token.setToken("unlock-token");
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        token.setUsed(false);
        passwordResetTokenRepository.save(token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resetResponse = restTemplate.postForEntity(
                url("/auth/password_reset/confirm"),
                new HttpEntity<>("{\"token\":\"unlock-token\",\"newPassword\":\"NewPass12\"}", headers),
                String.class
        );
        assertThat(resetResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        User unlocked = userRepository.findByEmail("unlock@test.com").orElseThrow();
        assertThat(unlocked.getFailedLoginAttempts()).isZero();
        assertThat(unlocked.getAccountLockedUntil()).isNull();

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                url("/auth/login"),
                loginBody("unlock@test.com", "NewPass12"),
                String.class
        );
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).contains("accessToken");
    }

    @Test
    void fullFlow_failedLoginsLockAccountThenPasswordResetAllowsLogin() {
        User user = new User();
        user.setEmail("e2e@test.com");
        user.setPassword(passwordEncoder.encode("OldPass12"));
        user.setFirstName("E2E");
        user.setLastName("Test");
        user.setActive(true);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        for (int i = 0; i < 4; i++) {
            assertUnauthorizedLogin("e2e@test.com", "wrong");
        }

        assertLockedLoginResponse("e2e@test.com", "wrong");

        assertThatThrownBy(() -> restTemplate.postForEntity(
                url("/auth/login"), loginBody("e2e@test.com", "OldPass12"), String.class))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);

        User locked = userRepository.findByEmail("e2e@test.com").orElseThrow();

        PasswordResetToken token = new PasswordResetToken();
        token.setToken("e2e-reset-token");
        token.setUser(locked);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        token.setUsed(false);
        passwordResetTokenRepository.save(token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resetResponse = restTemplate.postForEntity(
                url("/auth/password_reset/confirm"),
                new HttpEntity<>("{\"token\":\"e2e-reset-token\",\"newPassword\":\"NewPass12\"}", headers),
                String.class
        );
        assertThat(resetResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        User unlocked = userRepository.findByEmail("e2e@test.com").orElseThrow();
        assertThat(unlocked.getFailedLoginAttempts()).isZero();
        assertThat(unlocked.getAccountLockedUntil()).isNull();

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                url("/auth/login"),
                loginBody("e2e@test.com", "NewPass12"),
                String.class
        );
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).contains("accessToken");
    }
}
