package rs.raf.banka2_bek.stock.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.auth.service.JwtService;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ListingControllerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = createRestTemplate();

    @Autowired private ListingRepository listingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;

    private static RestTemplate createRestTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) { return false; }
            @Override
            public boolean hasError(HttpStatusCode statusCode) { return false; }
        });
        return rt;
    }

    @BeforeEach
    void setUp() {
        listingRepository.deleteAll();
        userRepository.deleteAll();
        seedListings();
    }

    private void seedListings() {
        listingRepository.save(listing("AAPL", "Apple Inc.", ListingType.STOCK));
        listingRepository.save(listing("MSFT", "Microsoft Corp.", ListingType.STOCK));
        listingRepository.save(listing("CLJ26", "Crude Oil Jun 2026", ListingType.FUTURES));
        listingRepository.save(listing("EUR/USD", "Euro/US Dollar", ListingType.FOREX));
    }

    private Listing listing(String ticker, String name, ListingType type) {
        Listing l = new Listing();
        l.setTicker(ticker);
        l.setName(name);
        l.setListingType(type);
        l.setPrice(BigDecimal.valueOf(150));
        l.setPriceChange(BigDecimal.valueOf(3));
        return l;
    }

    private String tokenForRole(String email, String role) {
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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    // --- Happy path ---

    @Test
    @DisplayName("GET /listings?type=STOCK vraca paginirane akcije")
    void getStockListings_returnsPagedResults() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings?type=STOCK", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("AAPL");
        assertThat(response.getBody()).contains("MSFT");
        assertThat(response.getBody()).doesNotContain("EUR/USD");
    }

    @Test
    @DisplayName("GET /listings?type=STOCK&search=apple vraca rezultate case-insensitive po imenu")
    void searchByName_caseInsensitive() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings?type=STOCK&search=apple", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("AAPL");
        assertThat(response.getBody()).doesNotContain("MSFT");
    }

    @Test
    @DisplayName("GET /listings?type=STOCK&search=MS vraca rezultate po ticker-u")
    void searchByTicker_partialMatch() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings?type=STOCK&search=MS", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("MSFT");
        assertThat(response.getBody()).doesNotContain("AAPL");
    }

    @Test
    @DisplayName("GET /listings?type=FOREX vraca forex za zaposlenog")
    void employee_canAccessForex() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings?type=FOREX", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("EUR/USD");
    }

    @Test
    @DisplayName("GET /listings?type=STOCK vraca akcije i za klijenta")
    void client_canAccessStock() {
        String token = tokenForRole("client@test.com", "CLIENT");
        ResponseEntity<String> response = get("/listings?type=STOCK", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("AAPL");
    }

    @Test
    @DisplayName("GET /listings?type=FUTURES vraca futures i za klijenta")
    void client_canAccessFutures() {
        String token = tokenForRole("client@test.com", "CLIENT");
        ResponseEntity<String> response = get("/listings?type=FUTURES", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("CLJ26");
    }

    // --- Negative cases ---

    @Test
    @DisplayName("GET /listings?type=FOREX vraca 403 za klijenta")
    void client_forexReturns403() {
        String token = tokenForRole("client@test.com", "CLIENT");
        ResponseEntity<String> response = get("/listings?type=FOREX", token);

        assertThat(response.getStatusCode()).isEqualTo(FORBIDDEN);
    }

    @Test
    @DisplayName("GET /listings?type=INVALID vraca 400")
    void invalidType_returns400() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings?type=INVALID", token);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /listings bez tokena vraca 401/403")
    void noToken_returnsUnauthorized() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/listings?type=STOCK"), String.class);

        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }
}
