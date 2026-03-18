package rs.raf.banka2_bek.account.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.account.dto.CompanyDto;
import rs.raf.banka2_bek.account.dto.CreateAccountDto;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.model.AccountSubtype;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;

// Koristimo RANDOM_PORT da izbegnemo konflikte ako je port 8080 zauzet
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AccountControllerIT {

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private UserRepository userRepository; // Pretpostavljam da se tako zove tvoj repo

    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CurrencyRepository currencyRepository;
    @BeforeEach
    void setUp() {
        // Proveravamo da li korisnik već postoji da ne dupliramo
        if (userRepository.findByEmail("nikola.milenkovic@banka.rs").isEmpty()) {
            User user = new User();
            user.setEmail("nikola.milenkovic@banka.rs");
            // OBAVEZNO enkoduj šifru jer auth proverava hash!
            user.setPassword(passwordEncoder.encode("Zaposleni12"));
            user.setFirstName("Nikola");
            user.setLastName("Milenkovic");
            user.setActive(true);
            // Dodaj role i ostala obavezna polja koja tvoj User model zahteva
            userRepository.save(user);
        }
    }
    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    // Pomoćna metoda za dobijanje tokena
    private String getJwtToken() {
        // Prilagodi polja (username/password) svom LoginRequest DTO-u
        Map<String, String> loginRequest = Map.of(
                "email", "nikola.milenkovic@banka.rs",
                "password", "Zaposleni12"
        );

        // Pozivamo login endpoint (prilagodi putanju npr. /auth/login ili /login)
        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/auth/login"),
                loginRequest,
                Map.class
        );

        // Izvlačimo token iz Map-e (obično se polje zove "token" ili "jwt")
        return (String) response.getBody().get("accessToken");
    }

    @Test
    void testCreateAccountEndpoint() {
        // 1. Priprema baze (Mora postojati valuta i korisnik pre kreiranja računa)
        Currency eur = new Currency();
        eur.setCode("EUR");
        eur.setName("Euro");
        eur.setSymbol("€");        // OVO JE FALILO
        eur.setCountry("EU");      // Dodaj i ovo ako je obavezno
        eur.setDescription("Euro currency");
        eur.setActive(true);

        eur = currencyRepository.save(eur);
        Long savedCurrencyId = eur.getId();

        if (employeeRepository.findByEmail("nikola.milenkovic@banka.rs").isEmpty()) {
            Employee employee = Employee.builder()
                    .firstName("Nikola")
                    .lastName("Milenkovic")
                    .dateOfBirth(LocalDate.of(1990, 1, 1)) // Obavezno
                    .gender("M")                           // Obavezno
                    .email("nikola.milenkovic@banka.rs")   // Obavezno, unique
                    .phone("+381601234567")               // Obavezno
                    .address("Knez Mihailova 1")           // Obavezno
                    .username("nikola.milenkovic")         // Obavezno
                    .password(passwordEncoder.encode("Zaposleni12")) // Obavezno
                    .saltPassword("static_salt_for_test")  // Obavezno (tvoj model traži null=false)
                    .position("Developer")                 // Obavezno
                    .department("IT")                      // Obavezno
                    .active(true)                          // Obavezno
                    .permissions(Set.of("ADMIN", "CREATE_ACCOUNT")) // Tvoj model koristi Set<String>
                    .build();

            employeeRepository.save(employee);
        }

        // 2. Dobijanje tokena (ovo ti već radi)
        String token = getJwtToken();

        // 3. Priprema DTO-a sa ID-jem koji smo upravo dobili iz baze
        CreateAccountDto dto = new CreateAccountDto();
        dto.setAccountType(AccountType.CHECKING);
        dto.setAccountSubtype(AccountSubtype.PERSONAL);
        dto.setCurrencyId(savedCurrencyId); // Koristimo pravi ID iz baze
        dto.setInitialBalance(BigDecimal.valueOf(500.00));
        dto.setClientId(1L); // Uveri se da i korisnik sa ID 1 postoji u test bazi

        // 4. Slanje zahteva
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<CreateAccountDto> request = new HttpEntity<>(dto, headers);

        ResponseEntity<Account> response = restTemplate.postForEntity(
                url("/accounts"),
                request,
                Account.class
        );

        // 5. Asserti
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
    }

    @Test
    void testCreateBusinessAccountEndpoint() {
        Currency eur = currencyRepository.findById(1L)
                .orElseGet(() -> {
                    Currency newEur = Currency.builder()
                            // .id(1L) // Možeš probati i ručno da setuješ ID ako baza dozvoljava
                            .code("EUR")
                            .name("Euro")
                            .symbol("€")
                            .country("EU")
                            .description("Euro")
                            .active(true)
                            .build();
                    return currencyRepository.save(newEur);
                });

// 2. DOBIJANJE TOKENA
        String token = getJwtToken();

        // 3. PRIPREMA BUSINESS DTO-A
        CreateAccountDto dto = new CreateAccountDto();
        dto.setAccountType(AccountType.BUSINESS);
        dto.setAccountSubtype(AccountSubtype.SALARY);
        dto.setCurrencyId(eur.getId());
        dto.setInitialBalance(BigDecimal.valueOf(100000.00));
        dto.setClientId(1L); // ID korisnika koji će biti ovlašćeno lice

        // Kreiramo podatke o firmi unutar DTO-a
        CompanyDto companyDto = new CompanyDto();
        companyDto.setName("Moja Nova Firma d.o.o.");
        companyDto.setRegistrationNumber("87654321"); // Mora biti unique
        companyDto.setTaxId("10203040");               // Mora biti unique
        companyDto.setActivityCode("6201");
        companyDto.setAddress("Bulevar Oslobođenja 10, Novi Sad");

        dto.setCompany(companyDto);

        // 4. SLANJE ZAHTEVA
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CreateAccountDto> request = new HttpEntity<>(dto, headers);

        ResponseEntity<Account> response = restTemplate.postForEntity(
                url("/accounts"), request, Account.class
        );

        // 5. PROVERA (ASSERTI)
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).isNotNull();

        // Ključne provere za Business Account:
        assertThat(response.getBody().getCompany()).isNotNull();
        assertThat(response.getBody().getCompany().getName()).isEqualTo("Moja Nova Firma d.o.o.");
        assertThat(response.getBody().getCompany().getRegistrationNumber()).isEqualTo("87654321");

        // Provera da li je balans ispravan
        assertThat(response.getBody().getBalance()).isEqualByComparingTo(BigDecimal.valueOf(100000.00));

        // Provera da li je vlasnik (User) null, jer je vlasnik Company
        assertThat(response.getBody().getUser()).isNull();

    }
}