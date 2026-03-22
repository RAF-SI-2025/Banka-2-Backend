package rs.raf.banka2_bek.actuary.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ActuaryControllerIntegrationTest {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private ActuaryInfoRepository actuaryInfoRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Employee agentMarko;
    private Employee agentJelena;
    private Employee agentAna;
    private Employee supervisorNina;
    private String authToken;

    @BeforeEach
    void setUp() {
        actuaryInfoRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();

        // Admin user za autentifikaciju — loguje se preko /auth/login
        User adminUser = new User();
        adminUser.setEmail("admin@banka.rs");
        adminUser.setPassword(passwordEncoder.encode("Admin12345"));
        adminUser.setFirstName("Admin");
        adminUser.setLastName("Test");
        adminUser.setActive(true);
        adminUser.setRole("ADMIN");
        userRepository.save(adminUser);

        // Login da dobijemo JWT token
        authToken = login("admin@banka.rs", "Admin12345");

        // Kreiramo zaposlene
        agentMarko = employeeRepository.save(Employee.builder()
                .firstName("Marko").lastName("Markovic")
                .email("marko.markovic@banka.rs")
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .gender("M").phone("+38163100200").address("Beograd")
                .username("marko.markovic")
                .password(passwordEncoder.encode("pass" + "salt"))
                .saltPassword("salt")
                .position("Menadzer").department("IT").active(true)
                .permissions(Set.of("AGENT"))
                .build());

        agentJelena = employeeRepository.save(Employee.builder()
                .firstName("Jelena").lastName("Jovanovic")
                .email("jelena.jovanovic@banka.rs")
                .dateOfBirth(LocalDate.of(1992, 8, 22))
                .gender("F").phone("+38164200300").address("Novi Sad")
                .username("jelena.jovanovic")
                .password(passwordEncoder.encode("pass" + "salt"))
                .saltPassword("salt")
                .position("Analiticar").department("Finance").active(true)
                .permissions(Set.of("AGENT"))
                .build());

        agentAna = employeeRepository.save(Employee.builder()
                .firstName("Ana").lastName("Markovic")
                .email("ana.markovic@banka.rs")
                .dateOfBirth(LocalDate.of(1988, 3, 10))
                .gender("F").phone("+38165300400").address("Kragujevac")
                .username("ana.markovic")
                .password(passwordEncoder.encode("pass" + "salt"))
                .saltPassword("salt")
                .position("Menadzer").department("Operations").active(true)
                .permissions(Set.of("AGENT"))
                .build());

        supervisorNina = employeeRepository.save(Employee.builder()
                .firstName("Nina").lastName("Nikolic")
                .email("nina.nikolic@banka.rs")
                .dateOfBirth(LocalDate.of(1985, 11, 3))
                .gender("F").phone("+38166400500").address("Beograd")
                .username("nina.nikolic")
                .password(passwordEncoder.encode("pass" + "salt"))
                .saltPassword("salt")
                .position("Direktor").department("Management").active(true)
                .permissions(Set.of("SUPERVISOR"))
                .build());

        // Actuary info zapisi
        ActuaryInfo infoMarko = new ActuaryInfo();
        infoMarko.setEmployee(agentMarko);
        infoMarko.setActuaryType(ActuaryType.AGENT);
        infoMarko.setDailyLimit(new BigDecimal("100000.00"));
        infoMarko.setUsedLimit(new BigDecimal("15000.00"));
        infoMarko.setNeedApproval(false);
        actuaryInfoRepository.save(infoMarko);

        ActuaryInfo infoJelena = new ActuaryInfo();
        infoJelena.setEmployee(agentJelena);
        infoJelena.setActuaryType(ActuaryType.AGENT);
        infoJelena.setDailyLimit(new BigDecimal("50000.00"));
        infoJelena.setUsedLimit(BigDecimal.ZERO);
        infoJelena.setNeedApproval(true);
        actuaryInfoRepository.save(infoJelena);

        ActuaryInfo infoAna = new ActuaryInfo();
        infoAna.setEmployee(agentAna);
        infoAna.setActuaryType(ActuaryType.AGENT);
        infoAna.setDailyLimit(new BigDecimal("75000.00"));
        infoAna.setUsedLimit(new BigDecimal("5000.00"));
        infoAna.setNeedApproval(false);
        actuaryInfoRepository.save(infoAna);

        ActuaryInfo infoNina = new ActuaryInfo();
        infoNina.setEmployee(supervisorNina);
        infoNina.setActuaryType(ActuaryType.SUPERVISOR);
        infoNina.setDailyLimit(null);
        infoNina.setUsedLimit(null);
        infoNina.setNeedApproval(false);
        actuaryInfoRepository.save(infoNina);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String login(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/auth/login"),
                new HttpEntity<>(body, headers),
                String.class
        );

        String responseBody = response.getBody();
        int tokenStart = responseBody.indexOf("accessToken\":\"") + 14;
        int tokenEnd = responseBody.indexOf("\"", tokenStart);
        return responseBody.substring(tokenStart, tokenEnd);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(authToken);
        return headers;
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /actuaries/agents
    // ══════════════════════════════════════════════════════════════════

    @Test
    void getAgentsReturnsAllAgentsWithoutSupervisors() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/agents"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Marko Markovic");
        assertThat(response.getBody()).contains("Jelena Jovanovic");
        assertThat(response.getBody()).contains("Ana Markovic");
        assertThat(response.getBody()).doesNotContain("Nina Nikolic");
    }

    @Test
    void getAgentsFiltersByEmailCaseInsensitive() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/agents?email=MARKO.MARKOVIC"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Marko Markovic");
        assertThat(response.getBody()).doesNotContain("Jelena Jovanovic");
        assertThat(response.getBody()).doesNotContain("Ana Markovic");
    }

    @Test
    void getAgentsFiltersByFirstNamePartialMatch() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/agents?firstName=jel"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Jelena Jovanovic");
        assertThat(response.getBody()).doesNotContain("Marko Markovic");
    }

    @Test
    void getAgentsFiltersByLastNameReturnsBothMarkovics() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/agents?lastName=markovic"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Marko Markovic");
        assertThat(response.getBody()).contains("Ana Markovic");
        assertThat(response.getBody()).doesNotContain("Jelena Jovanovic");
    }

    @Test
    void getAgentsFiltersByPosition() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/agents?position=menadzer"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Marko Markovic");
        assertThat(response.getBody()).contains("Ana Markovic");
        assertThat(response.getBody()).doesNotContain("Jelena Jovanovic");
    }

    @Test
    void getAgentsNoMatchReturnsEmptyList() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/agents?email=nepostojeci@email.com"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("[]");
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /actuaries/{employeeId}
    // ══════════════════════════════════════════════════════════════════

    @Test
    void getActuaryInfoReturnsAgentData() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/" + agentMarko.getId()),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Marko Markovic");
        assertThat(response.getBody()).contains("\"actuaryType\":\"AGENT\"");
        assertThat(response.getBody()).contains("100000.00");
        assertThat(response.getBody()).contains("15000.00");
    }

    @Test
    void getActuaryInfoReturnsSupervisorData() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/" + supervisorNina.getId()),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Nina Nikolic");
        assertThat(response.getBody()).contains("\"actuaryType\":\"SUPERVISOR\"");
    }

    @Test
    void getActuaryInfoReturns404ForNonExistentEmployee() {
        assertThatThrownBy(() -> restTemplate.exchange(
                url("/actuaries/999999"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        )).isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void getActuaryInfoShowsNeedApprovalCorrectly() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/" + agentJelena.getId()),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Jelena Jovanovic");
        assertThat(response.getBody()).contains("\"needApproval\":true");
        assertThat(response.getBody()).contains("50000.00");
    }
}