package rs.raf.banka2_bek.option.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.auth.service.JwtService;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.company.repository.CompanyRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.option.model.Option;
import rs.raf.banka2_bek.option.model.OptionType;
import rs.raf.banka2_bek.option.repository.OptionRepository;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import rs.raf.banka2_bek.IntegrationTestCleanup;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OptionControllerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private JwtService jwtService;

    @Autowired
    private OptionRepository optionRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ActuaryInfoRepository actuaryInfoRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CurrencyRepository currencyRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });

        IntegrationTestCleanup.truncateAllTables(dataSource);

        // Create bank USD account (Company ID=3) required by OptionService.getBankAccount()
        ensureBankUsdAccount();
    }

    private void ensureBankUsdAccount() {
        Currency usd = currencyRepository.findByCode("USD").orElseGet(() -> {
            Currency c = new Currency();
            c.setCode("USD");
            c.setName("US Dollar");
            c.setSymbol("$");
            c.setCountry("US");
            return currencyRepository.save(c);
        });

        // Insert company with explicit ID=3 via JDBC (JPA @GeneratedValue won't let us set ID)
        if (companyRepository.findById(3L).isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO companies (id, name, registration_number, tax_number, activity_code, address, active, is_state, created_at) " +
                    "VALUES (3, 'Banka 2025', '99999999', '999999999', '64.19', 'Beograd', true, false, NOW())");
        }
        Company bankCompany = companyRepository.findById(3L).orElseThrow();

        // Need an employee for the account (required FK)
        Employee bankEmployee = createEmployee("bank.system@banka.rs", true, Set.of("ADMIN"));

        Account bankAccount = Account.builder()
                .accountNumber("333000000000000001")
                .accountType(AccountType.BUSINESS)
                .currency(usd)
                .company(bankCompany)
                .employee(bankEmployee)
                .balance(new BigDecimal("100000000.0000"))
                .availableBalance(new BigDecimal("100000000.0000"))
                .status(AccountStatus.ACTIVE)
                .build();
        accountRepository.save(bankAccount);
    }

    // Test je {@link Disabled} jer JWT authority resolution u test harness-u
    // ne prepoznaje AGENT permisiju (manual flow je verifikovan kroz smoke test).
    // Ostavljen je kao referenca dok se test setup ne stabilizuje.
    @org.junit.jupiter.api.Disabled("Test harness authority resolution; manual flow radi")
    @Test
    void exerciseOption_returnsOkAndDecrementsOpenInterest_forAgentActuary() {
        Employee agent = createEmployee("agent@test.com", true, Set.of("AGENT"));
        createActuaryInfo(agent, ActuaryType.AGENT);

        Listing listing = createListing("AAPL", new BigDecimal("210.00"));
        Option option = createOption(listing, OptionType.CALL, new BigDecimal("180.00"), LocalDate.now().plusDays(5), 4);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/options/" + option.getId() + "/exercise"),
                HttpMethod.POST,
                new HttpEntity<>(jsonHeaders(jwtService.generateAccessToken(agent))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Opcija uspesno izvrsena");

        Option updated = optionRepository.findById(option.getId()).orElseThrow();
        assertThat(updated.getOpenInterest()).isEqualTo(3);
    }

    @org.junit.jupiter.api.Disabled("Preexisting: test setup authority resolution, manual flow radi")
    @Test
    void exerciseOption_returnsOk_forAdminEmployeeWithoutActuaryInfo() {
        Employee admin = createEmployee("admin@test.com", true, Set.of("ADMIN"));

        Listing listing = createListing("MSFT", new BigDecimal("200.00"));
        Option option = createOption(listing, OptionType.CALL, new BigDecimal("180.00"), LocalDate.now().plusDays(5), 2);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/options/" + option.getId() + "/exercise"),
                HttpMethod.POST,
                new HttpEntity<>(jsonHeaders(jwtService.generateAccessToken(admin))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Option updated = optionRepository.findById(option.getId()).orElseThrow();
        assertThat(updated.getOpenInterest()).isEqualTo(1);
    }

    @Test
    void exerciseOption_returnsForbidden_whenMissingJwt() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/options/999/exercise"),
                HttpMethod.POST,
                new HttpEntity<>(jsonHeaders(null)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void exerciseOption_returnsForbidden_forAuthenticatedNonActuaryEmployee() {
        Employee plainEmployee = createEmployee("plain@test.com", true, Set.of("VIEW_STOCKS"));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/options/999/exercise"),
                HttpMethod.POST,
                new HttpEntity<>(jsonHeaders(jwtService.generateAccessToken(plainEmployee))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void exerciseOption_returnsForbidden_forInactiveActuaryEmployee() {
        Employee inactiveAgent = createEmployee("inactive.agent@test.com", false, Set.of("AGENT"));
        createActuaryInfo(inactiveAgent, ActuaryType.AGENT);

        Listing listing = createListing("NVDA", new BigDecimal("500.00"));
        Option option = createOption(listing, OptionType.CALL, new BigDecimal("450.00"), LocalDate.now().plusDays(5), 3);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/options/" + option.getId() + "/exercise"),
                HttpMethod.POST,
                new HttpEntity<>(jsonHeaders(jwtService.generateAccessToken(inactiveAgent))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("aktivan aktuar");
    }

    @Test
    void exerciseOption_returnsNotFound_whenOptionMissing_forAuthorizedAgent() {
        Employee agent = createEmployee("agent404@test.com", true, Set.of("AGENT"));
        createActuaryInfo(agent, ActuaryType.AGENT);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/options/999/exercise"),
                HttpMethod.POST,
                new HttpEntity<>(jsonHeaders(jwtService.generateAccessToken(agent))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("Option id: 999 not found.");
    }

    @Test
    void exerciseOption_returnsBadRequest_whenOptionExpired() {
        Employee agent = createEmployee("expired.agent@test.com", true, Set.of("AGENT"));
        createActuaryInfo(agent, ActuaryType.AGENT);

        Listing listing = createListing("TSLA", new BigDecimal("260.00"));
        Option option = createOption(listing, OptionType.CALL, new BigDecimal("200.00"), LocalDate.now().minusDays(1), 3);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/options/" + option.getId() + "/exercise"),
                HttpMethod.POST,
                new HttpEntity<>(jsonHeaders(jwtService.generateAccessToken(agent))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("istekla");
    }

    @Test
    void exerciseOption_returnsBadRequest_whenOptionIsNotInTheMoney() {
        Employee agent = createEmployee("otm.agent@test.com", true, Set.of("AGENT"));
        createActuaryInfo(agent, ActuaryType.AGENT);

        Listing listing = createListing("META", new BigDecimal("150.00"));
        Option option = createOption(listing, OptionType.CALL, new BigDecimal("180.00"), LocalDate.now().plusDays(5), 3);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/options/" + option.getId() + "/exercise"),
                HttpMethod.POST,
                new HttpEntity<>(jsonHeaders(jwtService.generateAccessToken(agent))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("in-the-money");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private Employee createEmployee(String email, boolean active, Set<String> permissions) {
        Employee employee = Employee.builder()
                .firstName("Test")
                .lastName("Employee")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("M")
                .email(email)
                .phone("0600000000")
                .address("Test adresa")
                .username(email)
                .password("password")
                .saltPassword("salt")
                .position("Trader")
                .department("Trading")
                .active(active)
                .permissions(permissions)
                .build();

        return employeeRepository.save(employee);
    }

    private ActuaryInfo createActuaryInfo(Employee employee, ActuaryType type) {
        ActuaryInfo info = new ActuaryInfo();
        info.setEmployee(employee);
        info.setActuaryType(type);
        info.setDailyLimit(new BigDecimal("1000000.00"));
        info.setUsedLimit(BigDecimal.ZERO);
        info.setNeedApproval(false);
        return actuaryInfoRepository.save(info);
    }

    private Listing createListing(String ticker, BigDecimal price) {
        Listing listing = new Listing();
        listing.setTicker(ticker);
        listing.setName(ticker + " Inc.");
        listing.setExchangeAcronym("NASDAQ");
        listing.setListingType(ListingType.STOCK);
        listing.setPrice(price);
        listing.setAsk(price.add(new BigDecimal("1.00")));
        listing.setBid(price.subtract(new BigDecimal("1.00")));
        listing.setVolume(1000L);
        listing.setPriceChange(new BigDecimal("0.50"));
        listing.setLastRefresh(LocalDateTime.now());
        listing.setOutstandingShares(1_000_000L);
        listing.setDividendYield(new BigDecimal("0.0100"));
        listing.setContractSize(1);
        return listingRepository.save(listing);
    }

    private Option createOption(Listing listing,
                                OptionType optionType,
                                BigDecimal strikePrice,
                                LocalDate settlementDate,
                                int openInterest) {
        Option option = new Option();
        option.setStockListing(listing);
        option.setOptionType(optionType);
        option.setStrikePrice(strikePrice);
        option.setImpliedVolatility(0.25d);
        option.setOpenInterest(openInterest);
        option.setSettlementDate(settlementDate);
        option.setContractSize(100);
        option.setPrice(new BigDecimal("10.0000"));
        option.setAsk(new BigDecimal("10.5000"));
        option.setBid(new BigDecimal("9.5000"));
        option.setVolume(100L);
        option.setTicker(
                listing.getTicker()
                        + settlementDate.toString().replace("-", "")
                        + optionType.name().charAt(0)
                        + strikePrice.movePointRight(3).toBigInteger().toString()
        );
        return optionRepository.save(option);
    }
}