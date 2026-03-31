package rs.raf.banka2_bek.actuary.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import rs.raf.banka2_bek.actuary.dto.ActuaryInfoDto;
import rs.raf.banka2_bek.actuary.dto.UpdateActuaryLimitDto;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.actuary.service.ActuaryService;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.ActivationTokenRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ActuaryControllerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private ActuaryService actuaryService;

    @Autowired
    private ActuaryInfoRepository actuaryInfoRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ActivationTokenRepository activationTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final RestTemplate restTemplate = createRestTemplate();

    private Employee agentMarko;
    private Employee agentJelena;
    private Employee supervisorNina;

    private String adminToken;
    private String supervisorToken;
    private String agentToken;

    private static RestTemplate createRestTemplate() {
        RestTemplate rt = new RestTemplate(new JdkClientHttpRequestFactory());
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return rt;
    }

    @BeforeEach
    void setUp() {
        // CHANGE: ostavljen clearContext iz jedne grane da se svaki test startuje "čisto"
        SecurityContextHolder.clearContext();

        cleanDatabase();
        seedEmployees();
        seedActuaryInfo();
        seedUsersAndTokens();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void cleanDatabase() {
        // CHANGE: zadržano centralizovano čišćenje baza iz oba pristupa
        actuaryInfoRepository.deleteAll();
        activationTokenRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void seedEmployees() {
        agentMarko = employeeRepository.save(Employee.builder()
                .firstName("Marko")
                .lastName("Markovic")
                .email("marko.markovic@banka.rs")
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .gender("M")
                .phone("+38163100200")
                .address("Beograd")
                .username("marko.actuary")
                .password("pass")
                .saltPassword("salt")
                .position("Menadzer")
                .department("IT")
                .active(true)
                .permissions(Set.of("AGENT"))
                .build());

        agentJelena = employeeRepository.save(Employee.builder()
                .firstName("Jelena")
                .lastName("Jovanovic")
                .email("jelena.jovanovic@banka.rs")
                .dateOfBirth(LocalDate.of(1992, 8, 22))
                .gender("F")
                .phone("+38164200300")
                .address("Novi Sad")
                .username("jelena.actuary")
                .password("pass")
                .saltPassword("salt")
                .position("Analiticar")
                .department("Finance")
                .active(true)
                .permissions(Set.of("AGENT"))
                .build());

        supervisorNina = employeeRepository.save(Employee.builder()
                .firstName("Nina")
                .lastName("Nikolic")
                .email("nina.nikolic@banka.rs")
                .dateOfBirth(LocalDate.of(1985, 11, 3))
                .gender("F")
                .phone("+38166400500")
                .address("Beograd")
                .username("nina.supervisor")
                .password("pass")
                .saltPassword("salt")
                .position("Direktor")
                .department("Management")
                .active(true)
                .permissions(Set.of("SUPERVISOR"))
                .build());
    }

    private void seedActuaryInfo() {
        // CHANGE: obrisano duplo kreiranje ActuaryInfo zapisa iz merge konflikta
        // CHANGE: uklonjen agentAna jer nije postojao u klasi i rušio kompilaciju
        actuaryInfoRepository.save(createActuaryInfo(
                agentMarko,
                ActuaryType.AGENT,
                new BigDecimal("100000.00"),
                new BigDecimal("15000.00"),
                false
        ));

        actuaryInfoRepository.save(createActuaryInfo(
                agentJelena,
                ActuaryType.AGENT,
                new BigDecimal("50000.00"),
                new BigDecimal("999.99"),
                true
        ));

        actuaryInfoRepository.save(createActuaryInfo(
                supervisorNina,
                ActuaryType.SUPERVISOR,
                null,
                null,
                false
        ));
    }

    private void seedUsersAndTokens() {
        // CHANGE: obrisano duplo kreiranje admin korisnika iz merge-a
        // CHANGE: svi korisnici se sada kreiraju na jednom mestu radi konzistentnosti
        createUser("admin@banka.rs", "Admin12345", "ADMIN", "Admin", "Test");
        createUser("nina.nikolic@banka.rs", "Supervisor123", "EMPLOYEE", "Nina", "Nikolic");
        createUser("marko.markovic@banka.rs", "Agent123", "EMPLOYEE", "Marko", "Markovic");

        // CHANGE: ostavljena token funkcionalnost drugog autora, ali kompaktno spakovana
        adminToken = login("admin@banka.rs", "Admin12345");
        supervisorToken = login("nina.nikolic@banka.rs", "Supervisor123");
        agentToken = login("marko.markovic@banka.rs", "Agent123");
    }

    private void authenticateAsAdmin() {
        // CHANGE: dodate authority vrednosti da security context bude realniji za service testove
        UserDetails principal = org.springframework.security.core.userdetails.User.withUsername("admin@banka.rs")
                .password("ignored")
                .authorities("ROLE_ADMIN", "ADMIN")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                )
        );
    }

    private void authenticateAsSupervisor() {
        UserDetails principal = org.springframework.security.core.userdetails.User.withUsername("nina.nikolic@banka.rs")
                .password("ignored")
                .authorities("ROLE_EMPLOYEE")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                )
        );
    }

    private ActuaryInfo createActuaryInfo(Employee employee,
                                          ActuaryType type,
                                          BigDecimal dailyLimit,
                                          BigDecimal usedLimit,
                                          boolean needApproval) {
        ActuaryInfo info = new ActuaryInfo();
        info.setEmployee(employee);
        info.setActuaryType(type);
        info.setDailyLimit(dailyLimit);
        info.setUsedLimit(usedLimit);
        info.setNeedApproval(needApproval);
        return info;
    }

    private void createUser(String email, String rawPassword, String role, String firstName, String lastName) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setActive(true);
        user.setRole(role);
        userRepository.save(user);
    }

    private String url(String path) {
        // CHANGE: dodata helper metoda koja je falila u merged verziji
        return "http://localhost:" + port + path;
    }

    private HttpHeaders authHeaders() {
        return authHeaders(adminToken);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private String login(String email, String password) {
        // CHANGE: dodata helper metoda koja je falila u merged verziji
        // Ako vam je login endpoint drugačiji, promeni SAMO ovaj path.
        String loginUrl = url("/auth/login");

        Map<String, String> payload = Map.of(
                "email", email,
                "password", password
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(loginUrl, payload, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        Object accessToken = response.getBody().get("accessToken");
        if (accessToken == null) {
            accessToken = response.getBody().get("token");
        }

        assertThat(accessToken)
                .as("Login response mora da sadrži accessToken ili token")
                .isNotNull();

        return accessToken.toString();
    }

    // ============================================================
    // SERVICE TESTOVI
    // ============================================================

    @Test
    @DisplayName("resetAllUsedLimits resetuje samo agente, supervizor ostaje neizmenjen")
    void resetAllUsedLimitsResetsOnlyAgents() {
        actuaryService.resetAllUsedLimits();

        ActuaryInfo refreshedMarko = actuaryInfoRepository.findByEmployeeId(agentMarko.getId()).orElseThrow();
        ActuaryInfo refreshedJelena = actuaryInfoRepository.findByEmployeeId(agentJelena.getId()).orElseThrow();
        ActuaryInfo refreshedNina = actuaryInfoRepository.findByEmployeeId(supervisorNina.getId()).orElseThrow();

        assertEquals(0, refreshedMarko.getUsedLimit().compareTo(BigDecimal.ZERO));
        assertEquals(0, refreshedJelena.getUsedLimit().compareTo(BigDecimal.ZERO));
        assertNull(refreshedNina.getUsedLimit());
        assertEquals(ActuaryType.SUPERVISOR, refreshedNina.getActuaryType());
    }

    @Test
    @DisplayName("updateAgentLimit menja samo trazena polja i cuva ih u bazi")
    void updateAgentLimitPersistsChanges() {
        authenticateAsSupervisor();

        UpdateActuaryLimitDto dto = new UpdateActuaryLimitDto();
        dto.setDailyLimit(new BigDecimal("250000.00"));
        dto.setNeedApproval(true);

        ActuaryInfoDto result = actuaryService.updateAgentLimit(agentMarko.getId(), dto);

        assertEquals(new BigDecimal("250000.00"), result.getDailyLimit());
        assertTrue(result.isNeedApproval());
        assertEquals(new BigDecimal("15000.00"), result.getUsedLimit());

        ActuaryInfo refreshed = actuaryInfoRepository.findByEmployeeId(agentMarko.getId()).orElseThrow();
        assertEquals(new BigDecimal("250000.00"), refreshed.getDailyLimit());
        assertTrue(refreshed.isNeedApproval());
        assertEquals(new BigDecimal("15000.00"), refreshed.getUsedLimit());
    }

    @Test
    @DisplayName("resetUsedLimit rucno resetuje samo target agenta")
    void resetUsedLimitPersistsZero() {
        authenticateAsAdmin();

        ActuaryInfoDto result = actuaryService.resetUsedLimit(agentMarko.getId());

        assertEquals(0, result.getUsedLimit().compareTo(BigDecimal.ZERO));

        ActuaryInfo refreshedMarko = actuaryInfoRepository.findByEmployeeId(agentMarko.getId()).orElseThrow();
        ActuaryInfo refreshedJelena = actuaryInfoRepository.findByEmployeeId(agentJelena.getId()).orElseThrow();

        assertEquals(0, refreshedMarko.getUsedLimit().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("999.99"), refreshedJelena.getUsedLimit());
    }

    // ============================================================
    // HTTP / CONTROLLER INTEGRATION TESTOVI
    // ============================================================

    @Test
    void updateAgentLimitAsSupervisorReturns200AndPersistsChanges() {
        String payload = "{\"dailyLimit\":65000.00,\"needApproval\":false}";

        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/" + agentJelena.getId() + "/limit"),
                HttpMethod.PATCH,
                new HttpEntity<>(payload, authHeaders(supervisorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"employeeId\":" + agentJelena.getId());
        assertThat(response.getBody()).contains("65000.00");
        assertThat(response.getBody()).contains("\"needApproval\":false");

        ActuaryInfo persisted = actuaryInfoRepository.findByEmployeeId(agentJelena.getId()).orElseThrow();
        assertThat(persisted.getDailyLimit()).isEqualByComparingTo("65000.00");
        assertThat(persisted.isNeedApproval()).isFalse();
    }

    @Test
    void updateAgentLimitDailyLimitOnlyReturns200AndPreservesNeedApproval() {
        String payload = "{\"dailyLimit\":91000.00}";

        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/" + agentJelena.getId() + "/limit"),
                HttpMethod.PATCH,
                new HttpEntity<>(payload, authHeaders(supervisorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"employeeId\":" + agentJelena.getId());
        assertThat(response.getBody()).contains("91000.00");
        assertThat(response.getBody()).contains("\"needApproval\":true");

        ActuaryInfo persisted = actuaryInfoRepository.findByEmployeeId(agentJelena.getId()).orElseThrow();
        assertThat(persisted.getDailyLimit()).isEqualByComparingTo("91000.00");
        assertThat(persisted.isNeedApproval()).isTrue();
    }

    @Test
    void updateAgentLimitNeedApprovalOnlyReturns200AndPreservesDailyLimit() {
        String payload = "{\"needApproval\":false}";

        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/" + agentJelena.getId() + "/limit"),
                HttpMethod.PATCH,
                new HttpEntity<>(payload, authHeaders(supervisorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"employeeId\":" + agentJelena.getId());
        assertThat(response.getBody()).contains("50000.00");
        assertThat(response.getBody()).contains("\"needApproval\":false");

        ActuaryInfo persisted = actuaryInfoRepository.findByEmployeeId(agentJelena.getId()).orElseThrow();
        assertThat(persisted.getDailyLimit()).isEqualByComparingTo("50000.00");
        assertThat(persisted.isNeedApproval()).isFalse();
    }

    @Test
    void updateAgentLimitAsAgentReturns403() {
        String payload = "{\"dailyLimit\":61000.00}";

        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/" + agentJelena.getId() + "/limit"),
                HttpMethod.PATCH,
                new HttpEntity<>(payload, authHeaders(agentToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateAgentLimitWithoutAuthReturns403() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/" + agentJelena.getId() + "/limit"),
                HttpMethod.PATCH,
                new HttpEntity<>("{\"dailyLimit\":70000}", headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateAgentLimitReturns404WhenTargetDoesNotExist() {
        String payload = "{\"needApproval\":true}";

        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/999999/limit"),
                HttpMethod.PATCH,
                new HttpEntity<>(payload, authHeaders(supervisorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateAgentLimitReturnsDomainErrorWhenTargetIsSupervisor() {
        Employee otherSupervisor = employeeRepository.save(Employee.builder()
                .firstName("Sara")
                .lastName("Savic")
                .email("sara.savic@banka.rs")
                .dateOfBirth(LocalDate.of(1987, 7, 7))
                .gender("F")
                .phone("+38166111222")
                .address("Nis")
                .username("sara.savic")
                // CHANGE: standardizovan password zapis da bude isti stil kroz klasu
                .password("pass")
                .saltPassword("salt")
                .position("Direktor")
                .department("Management")
                .active(true)
                .permissions(Set.of("SUPERVISOR"))
                .build());


        actuaryInfoRepository.save(createActuaryInfo(
                otherSupervisor,
                ActuaryType.SUPERVISOR,
                null,
                null,
                false
        ));

        String payload = "{\"dailyLimit\":80000.00}";

        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/" + otherSupervisor.getId() + "/limit"),
                HttpMethod.PATCH,
                new HttpEntity<>(payload, authHeaders(supervisorToken)),
                String.class
        );

        assertThat(response.getStatusCode() == HttpStatus.BAD_REQUEST
                || response.getStatusCode() == HttpStatus.CONFLICT).isTrue();
    }
}