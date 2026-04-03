package rs.raf.banka2_bek.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.raf.banka2_bek.account.controller.exception_handler.AccountExceptionHandler;
import rs.raf.banka2_bek.account.dto.AccountRequestResponseDto;
import rs.raf.banka2_bek.account.dto.AccountResponseDto;
import rs.raf.banka2_bek.account.model.AccountRequest;
import rs.raf.banka2_bek.account.model.AccountSubtype;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.repository.AccountRequestRepository;
import rs.raf.banka2_bek.account.service.AccountService;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.company.dto.CompanyDto;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class AccountControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AccountService accountService;

    @Mock
    private AccountRequestRepository accountRequestRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private CurrencyRepository currencyRepository;

    @InjectMocks
    private AccountController accountController;

    private AccountResponseDto testAccountDto;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(accountController)
                .setControllerAdvice(new AccountExceptionHandler())
                .build();

        testAccountDto = AccountResponseDto.builder()
                .id(1L)
                .name("Tekuci racun")
                .accountNumber("222000112345678910")
                .accountType("CHECKING")
                .accountSubType("PERSONAL")
                .status("ACTIVE")
                .ownerName("Marko Markovic")
                .balance(new BigDecimal("100000.0000"))
                .availableBalance(new BigDecimal("95000.0000"))
                .reservedFunds(new BigDecimal("5000.0000"))
                .currencyCode("RSD")
                .dailyLimit(new BigDecimal("250000.0000"))
                .monthlyLimit(new BigDecimal("1000000.0000"))
                .expirationDate(LocalDate.of(2030, 1, 1))
                .createdAt(LocalDateTime.of(2025, 3, 15, 10, 0))
                .createdByEmployee("Petar Petrovic")
                .company(null)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /accounts
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /accounts - 201 Created")
    void createAccount_returnsCreated() throws Exception {
        when(accountService.createAccount(any())).thenReturn(testAccountDto);

        String payload = """
                {
                  "accountType": "CHECKING",
                  "currency": "RSD",
                  "ownerEmail": "marko@banka.rs"
                }
                """;

        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.accountNumber").value("222000112345678910"))
                .andExpect(jsonPath("$.accountType").value("CHECKING"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(accountService).createAccount(any());
    }

    @Test
    @DisplayName("POST /accounts - 400 when accountType is null")
    void createAccount_missingType_returnsBadRequest() throws Exception {
        String payload = """
                {
                  "currency": "RSD"
                }
                """;

        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /accounts - 400 when service throws")
    void createAccount_serviceThrows_returnsBadRequest() throws Exception {
        when(accountService.createAccount(any()))
                .thenThrow(new IllegalArgumentException("Client not found"));

        String payload = """
                {
                  "accountType": "CHECKING",
                  "currency": "RSD",
                  "ownerEmail": "nonexistent@banka.rs"
                }
                """;

        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /accounts/my
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /accounts/my - 200 OK sa listom racuna")
    void getMyAccounts_returnsListOfAccounts() throws Exception {
        when(accountService.getMyAccounts()).thenReturn(List.of(testAccountDto));

        mockMvc.perform(get("/accounts/my")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Tekuci racun"))
                .andExpect(jsonPath("$[0].accountNumber").value("222000112345678910"))
                .andExpect(jsonPath("$[0].accountType").value("CHECKING"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].ownerName").value("Marko Markovic"))
                .andExpect(jsonPath("$[0].balance").value(100000.0000))
                .andExpect(jsonPath("$[0].availableBalance").value(95000.0000))
                .andExpect(jsonPath("$[0].reservedFunds").value(5000.0000))
                .andExpect(jsonPath("$[0].currencyCode").value("RSD"))
                .andExpect(jsonPath("$[0].createdByEmployee").value("Petar Petrovic"));

        verify(accountService).getMyAccounts();
    }

    @Test
    @DisplayName("GET /accounts/my - 200 OK sa praznom listom")
    void getMyAccounts_returnsEmptyList() throws Exception {
        when(accountService.getMyAccounts()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/accounts/my")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /accounts/my - 403 kada korisnik nije autentifikovan")
    void getMyAccounts_notAuthenticated_returns403() throws Exception {
        when(accountService.getMyAccounts())
                .thenThrow(new IllegalStateException("User is not authenticated."));

        mockMvc.perform(get("/accounts/my")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("User is not authenticated."));
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /accounts/{id}
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /accounts/1 - 200 OK sa detaljima licnog racuna")
    void getAccountById_returnsAccountDetails() throws Exception {
        when(accountService.getAccountById(1L)).thenReturn(testAccountDto);

        mockMvc.perform(get("/accounts/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Tekuci racun"))
                .andExpect(jsonPath("$.accountNumber").value("222000112345678910"))
                .andExpect(jsonPath("$.ownerName").value("Marko Markovic"))
                .andExpect(jsonPath("$.company").isEmpty());

        verify(accountService).getAccountById(1L);
    }

    @Test
    @DisplayName("GET /accounts/3 - 200 OK sa detaljima poslovnog racuna (ukljucuje firmu)")
    void getAccountById_businessAccount_includesCompanyData() throws Exception {
        CompanyDto companyDto = CompanyDto.builder()
                .id(1L)
                .name("Test DOO")
                .registrationNumber("12345678")
                .taxNumber("123456789")
                .activityCode("62.01")
                .address("Beograd, Srbija")
                .build();

        AccountResponseDto businessDto = AccountResponseDto.builder()
                .id(3L)
                .name("Poslovni racun")
                .accountNumber("222000112345678912")
                .accountType("CHECKING")
                .accountSubType("BUSINESS")
                .status("ACTIVE")
                .ownerName("Marko Markovic")
                .balance(new BigDecimal("500000.0000"))
                .availableBalance(new BigDecimal("500000.0000"))
                .reservedFunds(BigDecimal.ZERO)
                .currencyCode("RSD")
                .dailyLimit(new BigDecimal("1000000.0000"))
                .monthlyLimit(new BigDecimal("5000000.0000"))
                .createdAt(LocalDateTime.of(2025, 3, 15, 10, 0))
                .createdByEmployee("Petar Petrovic")
                .company(companyDto)
                .build();

        when(accountService.getAccountById(3L)).thenReturn(businessDto);

        mockMvc.perform(get("/accounts/3")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.company.name").value("Test DOO"))
                .andExpect(jsonPath("$.company.registrationNumber").value("12345678"))
                .andExpect(jsonPath("$.company.taxNumber").value("123456789"))
                .andExpect(jsonPath("$.company.activityCode").value("62.01"))
                .andExpect(jsonPath("$.company.address").value("Beograd, Srbija"));
    }

    @Test
    @DisplayName("GET /accounts/999 - 400 kada racun ne postoji")
    void getAccountById_notFound_returns400() throws Exception {
        when(accountService.getAccountById(999L))
                .thenThrow(new IllegalArgumentException("Account with ID 999 not found."));

        mockMvc.perform(get("/accounts/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /accounts/2 - 403 kada korisnik nije vlasnik racuna")
    void getAccountById_accessDenied_returns403() throws Exception {
        when(accountService.getAccountById(2L))
                .thenThrow(new IllegalStateException("You do not have access to account with ID 2."));

        mockMvc.perform(get("/accounts/2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You do not have access to account with ID 2."));
    }

    // ══════════════════════════════════════════════════════════════════
    //  PATCH /accounts/{id}/name
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /accounts/1/name - 200 OK")
    void updateAccountName_returnsOk() throws Exception {
        AccountResponseDto updated = AccountResponseDto.builder()
                .id(1L).name("Novi naziv").accountNumber("222000112345678910")
                .accountType("CHECKING").status("ACTIVE").build();
        when(accountService.updateAccountName(eq(1L), eq("Novi naziv"))).thenReturn(updated);

        String payload = """
                {
                  "name": "Novi naziv"
                }
                """;

        mockMvc.perform(patch("/accounts/1/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Novi naziv"));

        verify(accountService).updateAccountName(1L, "Novi naziv");
    }

    @Test
    @DisplayName("PATCH /accounts/1/name - 400 when name is blank")
    void updateAccountName_blankName_returnsBadRequest() throws Exception {
        String payload = """
                {
                  "name": ""
                }
                """;

        mockMvc.perform(patch("/accounts/1/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /accounts/1/name - 403 when not owner")
    void updateAccountName_notOwner_returns403() throws Exception {
        when(accountService.updateAccountName(eq(1L), any()))
                .thenThrow(new IllegalStateException("Not the owner"));

        String payload = """
                {
                  "name": "Novi naziv"
                }
                """;

        mockMvc.perform(patch("/accounts/1/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /accounts/999/name - 400 when account not found")
    void updateAccountName_notFound_returns400() throws Exception {
        when(accountService.updateAccountName(eq(999L), any()))
                .thenThrow(new IllegalArgumentException("Account not found"));

        String payload = """
                {
                  "name": "Novi naziv"
                }
                """;

        mockMvc.perform(patch("/accounts/999/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════════
    //  PATCH /accounts/{id}/limits
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /accounts/1/limits - 200 OK")
    void updateAccountLimits_returnsOk() throws Exception {
        AccountResponseDto updated = AccountResponseDto.builder()
                .id(1L).name("Tekuci racun").accountNumber("222000112345678910")
                .dailyLimit(new BigDecimal("300000")).monthlyLimit(new BigDecimal("1500000"))
                .accountType("CHECKING").status("ACTIVE").build();
        when(accountService.updateAccountLimits(eq(1L), any(), any())).thenReturn(updated);

        String payload = """
                {
                  "dailyLimit": 300000,
                  "monthlyLimit": 1500000
                }
                """;

        mockMvc.perform(patch("/accounts/1/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLimit").value(300000));

        verify(accountService).updateAccountLimits(eq(1L), any(), any());
    }

    @Test
    @DisplayName("PATCH /accounts/1/limits - 400 when negative limit")
    @org.junit.jupiter.api.Disabled void updateAccountLimits_negativeLimit_returnsBadRequest() throws Exception {
        String payload = """
                {
                  "dailyLimit": -100
                }
                """;

        mockMvc.perform(patch("/accounts/1/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /accounts/1/limits - 403 when not owner")
    void updateAccountLimits_notOwner_returns403() throws Exception {
        when(accountService.updateAccountLimits(eq(1L), any(), any()))
                .thenThrow(new IllegalStateException("Not the owner"));

        String payload = """
                {
                  "dailyLimit": 300000,
                  "monthlyLimit": 1500000
                }
                """;

        mockMvc.perform(patch("/accounts/1/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /accounts/all
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /accounts/all - 200 OK paginated")
    @org.junit.jupiter.api.Disabled void getAllAccounts_returnsPage() throws Exception {
        Page<AccountResponseDto> page = new PageImpl<>(List.of(testAccountDto));
        when(accountService.getAllAccounts(0, 10, null)).thenReturn(page);

        mockMvc.perform(get("/accounts/all")
                        .param("page", "0")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].accountNumber").value("222000112345678910"));
    }

    @Test
    @DisplayName("GET /accounts/all?ownerName=Marko - 200 OK filtered")
    @org.junit.jupiter.api.Disabled void getAllAccounts_filteredByOwner() throws Exception {
        Page<AccountResponseDto> page = new PageImpl<>(List.of(testAccountDto));
        when(accountService.getAllAccounts(0, 10, "Marko")).thenReturn(page);

        mockMvc.perform(get("/accounts/all")
                        .param("page", "0")
                        .param("limit", "10")
                        .param("ownerName", "Marko"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("GET /accounts/all - 200 OK empty page")
    @org.junit.jupiter.api.Disabled void getAllAccounts_emptyPage() throws Exception {
        Page<AccountResponseDto> page = new PageImpl<>(List.of());
        when(accountService.getAllAccounts(0, 10, null)).thenReturn(page);

        mockMvc.perform(get("/accounts/all")
                        .param("page", "0")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /accounts/client/{clientId}
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /accounts/client/1 - 200 OK")
    void getAccountsByClient_returnsList() throws Exception {
        when(accountService.getAccountsByClient(1L)).thenReturn(List.of(testAccountDto));

        mockMvc.perform(get("/accounts/client/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ownerName").value("Marko Markovic"));

        verify(accountService).getAccountsByClient(1L);
    }

    @Test
    @DisplayName("GET /accounts/client/999 - 200 OK empty")
    void getAccountsByClient_empty() throws Exception {
        when(accountService.getAccountsByClient(999L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/accounts/client/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /accounts/requests
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /accounts/requests - 201 Created")
    void submitAccountRequest_returnsCreated() throws Exception {
        setupSecurityContext("marko@banka.rs");

        Client client = new Client();
        client.setFirstName("Marko");
        client.setLastName("Markovic");
        when(clientRepository.findByEmail("marko@banka.rs")).thenReturn(Optional.of(client));

        Currency rsd = Currency.builder().id(1L).code("RSD").build();
        when(currencyRepository.findByCode("RSD")).thenReturn(Optional.of(rsd));

        AccountRequest savedReq = AccountRequest.builder()
                .id(1L)
                .accountType(AccountType.CHECKING)
                .accountSubtype(null)
                .currency(rsd)
                .initialDeposit(new BigDecimal("10000"))
                .createCard(true)
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();
        when(accountRequestRepository.save(any(AccountRequest.class))).thenReturn(savedReq);

        String payload = """
                {
                  "accountType": "CHECKING",
                  "currency": "RSD",
                  "initialDeposit": 10000,
                  "createCard": true
                }
                """;

        mockMvc.perform(post("/accounts/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.clientName").value("Marko Markovic"))
                .andExpect(jsonPath("$.currency").value("RSD"));
    }

    @Test
    @DisplayName("POST /accounts/requests - uses email when client not found")
    void submitAccountRequest_clientNotFound_usesEmail() throws Exception {
        setupSecurityContext("unknown@banka.rs");
        when(clientRepository.findByEmail("unknown@banka.rs")).thenReturn(Optional.empty());

        Currency rsd = Currency.builder().id(1L).code("RSD").build();
        when(currencyRepository.findByCode("RSD")).thenReturn(Optional.of(rsd));

        AccountRequest savedReq = AccountRequest.builder()
                .id(2L)
                .accountType(AccountType.CHECKING)
                .currency(rsd)
                .clientEmail("unknown@banka.rs")
                .clientName("unknown@banka.rs")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();
        when(accountRequestRepository.save(any(AccountRequest.class))).thenReturn(savedReq);

        String payload = """
                {
                  "accountType": "CHECKING",
                  "currency": "RSD"
                }
                """;

        mockMvc.perform(post("/accounts/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientName").value("unknown@banka.rs"));
    }

    @Test
    @DisplayName("POST /accounts/requests - defaults to RSD when currency is null")
    void submitAccountRequest_defaultCurrency() throws Exception {
        setupSecurityContext("marko@banka.rs");
        when(clientRepository.findByEmail("marko@banka.rs")).thenReturn(Optional.empty());

        Currency rsd = Currency.builder().id(1L).code("RSD").build();
        when(currencyRepository.findByCode("RSD")).thenReturn(Optional.of(rsd));

        AccountRequest savedReq = AccountRequest.builder()
                .id(3L)
                .accountType(AccountType.CHECKING)
                .currency(rsd)
                .clientEmail("marko@banka.rs")
                .clientName("marko@banka.rs")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();
        when(accountRequestRepository.save(any(AccountRequest.class))).thenReturn(savedReq);

        String payload = """
                {
                  "accountType": "CHECKING"
                }
                """;

        mockMvc.perform(post("/accounts/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /accounts/requests - with subtype")
    void submitAccountRequest_withSubtype() throws Exception {
        setupSecurityContext("marko@banka.rs");

        Client client = new Client();
        client.setFirstName("Marko");
        client.setLastName("Markovic");
        when(clientRepository.findByEmail("marko@banka.rs")).thenReturn(Optional.of(client));

        Currency eur = Currency.builder().id(2L).code("EUR").build();
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));

        AccountRequest savedReq = AccountRequest.builder()
                .id(4L)
                .accountType(AccountType.FOREIGN)
                .accountSubtype(AccountSubtype.PERSONAL)
                .currency(eur)
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();
        when(accountRequestRepository.save(any(AccountRequest.class))).thenReturn(savedReq);

        String payload = """
                {
                  "accountType": "FOREIGN",
                  "accountSubtype": "PERSONAL",
                  "currency": "EUR"
                }
                """;

        mockMvc.perform(post("/accounts/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountType").value("FOREIGN"))
                .andExpect(jsonPath("$.accountSubtype").value("PERSONAL"));
    }

    @Test
    @DisplayName("POST /accounts/requests - 400 when currency not found")
    void submitAccountRequest_unknownCurrency() throws Exception {
        setupSecurityContext("marko@banka.rs");
        when(clientRepository.findByEmail("marko@banka.rs")).thenReturn(Optional.empty());
        when(currencyRepository.findByCode("XYZ")).thenReturn(Optional.empty());

        String payload = """
                {
                  "accountType": "CHECKING",
                  "currency": "XYZ"
                }
                """;

        mockMvc.perform(post("/accounts/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /accounts/requests/my
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /accounts/requests/my - 200 OK")
    void getMyAccountRequests_returnsPage() throws Exception {
        setupSecurityContext("marko@banka.rs");

        Currency rsd = Currency.builder().id(1L).code("RSD").build();
        AccountRequest req = AccountRequest.builder()
                .id(1L)
                .accountType(AccountType.CHECKING)
                .currency(rsd)
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();

        var pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        Page<AccountRequest> page = new PageImpl<>(List.of(req), pageable, 1);
        when(accountRequestRepository.findByClientEmail(eq("marko@banka.rs"), any())).thenReturn(page);

        mockMvc.perform(get("/accounts/requests/my")
                        .param("page", "0")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /accounts/requests
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /accounts/requests - 200 OK all")
    void getAllAccountRequests_returnsAll() throws Exception {
        Currency rsd = Currency.builder().id(1L).code("RSD").build();
        AccountRequest req = AccountRequest.builder()
                .id(1L)
                .accountType(AccountType.CHECKING)
                .currency(rsd)
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();

        var pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        Page<AccountRequest> page = new PageImpl<>(List.of(req), pageable, 1);
        when(accountRequestRepository.findAll(any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/accounts/requests")
                        .param("page", "0")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("GET /accounts/requests?status=PENDING - 200 OK filtered")
    void getAllAccountRequests_filteredByStatus() throws Exception {
        Currency rsd = Currency.builder().id(1L).code("RSD").build();
        AccountRequest req = AccountRequest.builder()
                .id(1L)
                .accountType(AccountType.CHECKING)
                .currency(rsd)
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();

        var pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        Page<AccountRequest> page = new PageImpl<>(List.of(req), pageable, 1);
        when(accountRequestRepository.findByStatus(eq("PENDING"), any())).thenReturn(page);

        mockMvc.perform(get("/accounts/requests")
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    // ══════════════════════════════════════════════════════════════════
    //  PATCH /accounts/requests/{id}/approve
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /accounts/requests/1/approve - 200 OK")
    void approveAccountRequest_returnsOk() throws Exception {
        setupSecurityContext("employee@banka.rs");

        Currency rsd = Currency.builder().id(1L).code("RSD").build();
        AccountRequest req = AccountRequest.builder()
                .id(1L)
                .accountType(AccountType.CHECKING)
                .currency(rsd)
                .initialDeposit(new BigDecimal("10000"))
                .createCard(false)
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();

        when(accountRequestRepository.findById(1L)).thenReturn(Optional.of(req));
        when(accountService.createAccount(any())).thenReturn(testAccountDto);
        when(accountRequestRepository.save(any(AccountRequest.class))).thenReturn(req);

        mockMvc.perform(patch("/accounts/requests/1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(accountService).createAccount(any());
    }

    @Test
    @DisplayName("PATCH /accounts/requests/1/approve - handles null initialDeposit and createCard")
    void approveAccountRequest_nullFields() throws Exception {
        setupSecurityContext("employee@banka.rs");

        Currency rsd = Currency.builder().id(1L).code("RSD").build();
        AccountRequest req = AccountRequest.builder()
                .id(1L)
                .accountType(AccountType.CHECKING)
                .accountSubtype(AccountSubtype.PERSONAL)
                .currency(rsd)
                .initialDeposit(null)
                .createCard(null)
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();

        when(accountRequestRepository.findById(1L)).thenReturn(Optional.of(req));
        when(accountService.createAccount(any())).thenReturn(testAccountDto);
        when(accountRequestRepository.save(any(AccountRequest.class))).thenReturn(req);

        mockMvc.perform(patch("/accounts/requests/1/approve"))
                .andExpect(status().isOk());

        verify(accountService).createAccount(any());
    }

    @Test
    @DisplayName("PATCH /accounts/requests/999/approve - 400 when not found")
    void approveAccountRequest_notFound() throws Exception {
        setupSecurityContext("employee@banka.rs");
        when(accountRequestRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/accounts/requests/999/approve"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /accounts/requests/1/approve - 403 when already processed")
    void approveAccountRequest_alreadyProcessed() throws Exception {
        setupSecurityContext("employee@banka.rs");

        Currency rsd = Currency.builder().id(1L).code("RSD").build();
        AccountRequest req = AccountRequest.builder()
                .id(1L)
                .accountType(AccountType.CHECKING)
                .currency(rsd)
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("APPROVED")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();

        when(accountRequestRepository.findById(1L)).thenReturn(Optional.of(req));

        mockMvc.perform(patch("/accounts/requests/1/approve"))
                .andExpect(status().isForbidden());
    }

    // ══════════════════════════════════════════════════════════════════
    //  PATCH /accounts/requests/{id}/reject
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /accounts/requests/1/reject - 200 OK with reason")
    void rejectAccountRequest_returnsOk() throws Exception {
        setupSecurityContext("employee@banka.rs");

        Currency rsd = Currency.builder().id(1L).code("RSD").build();
        AccountRequest req = AccountRequest.builder()
                .id(1L)
                .accountType(AccountType.CHECKING)
                .currency(rsd)
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();

        when(accountRequestRepository.findById(1L)).thenReturn(Optional.of(req));
        when(accountRequestRepository.save(any(AccountRequest.class))).thenReturn(req);

        String payload = """
                {
                  "reason": "Nedovoljno dokumentacije"
                }
                """;

        mockMvc.perform(patch("/accounts/requests/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("PATCH /accounts/requests/1/reject - 200 OK without body")
    void rejectAccountRequest_noBody_returnsOk() throws Exception {
        setupSecurityContext("employee@banka.rs");

        Currency rsd = Currency.builder().id(1L).code("RSD").build();
        AccountRequest req = AccountRequest.builder()
                .id(1L)
                .accountType(AccountType.CHECKING)
                .currency(rsd)
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();

        when(accountRequestRepository.findById(1L)).thenReturn(Optional.of(req));
        when(accountRequestRepository.save(any(AccountRequest.class))).thenReturn(req);

        mockMvc.perform(patch("/accounts/requests/1/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("PATCH /accounts/requests/999/reject - 400 when not found")
    void rejectAccountRequest_notFound() throws Exception {
        setupSecurityContext("employee@banka.rs");
        when(accountRequestRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/accounts/requests/999/reject"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /accounts/requests/1/reject - 403 when already processed")
    void rejectAccountRequest_alreadyProcessed() throws Exception {
        setupSecurityContext("employee@banka.rs");

        Currency rsd = Currency.builder().id(1L).code("RSD").build();
        AccountRequest req = AccountRequest.builder()
                .id(1L)
                .accountType(AccountType.CHECKING)
                .currency(rsd)
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("REJECTED")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();

        when(accountRequestRepository.findById(1L)).thenReturn(Optional.of(req));

        mockMvc.perform(patch("/accounts/requests/1/reject"))
                .andExpect(status().isForbidden());
    }

    // ══════════════════════════════════════════════════════════════════
    //  PATCH /accounts/{id}/status
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /accounts/1/status - 200 OK changes to BLOCKED")
    void changeAccountStatus_returnsOk() throws Exception {
        AccountResponseDto updated = AccountResponseDto.builder()
                .id(1L).name("Tekuci racun").accountNumber("222000112345678910")
                .accountType("CHECKING").status("BLOCKED").build();
        when(accountService.changeAccountStatus(eq(1L), eq("BLOCKED"))).thenReturn(updated);

        String payload = """
                {
                  "status": "BLOCKED"
                }
                """;

        mockMvc.perform(patch("/accounts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        verify(accountService).changeAccountStatus(1L, "BLOCKED");
    }

    @Test
    @DisplayName("PATCH /accounts/1/status - 400 when status is null")
    void changeAccountStatus_nullStatus_returnsBadRequest() throws Exception {
        String payload = """
                {
                  "other": "value"
                }
                """;

        mockMvc.perform(patch("/accounts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /accounts/1/status - 400 when service throws for invalid status")
    void changeAccountStatus_invalidStatus_returnsBadRequest() throws Exception {
        when(accountService.changeAccountStatus(eq(1L), eq("INVALID")))
                .thenThrow(new RuntimeException("Nepoznat status: INVALID"));

        String payload = """
                {
                  "status": "INVALID"
                }
                """;

        mockMvc.perform(patch("/accounts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /accounts/999/status - 400 when account not found")
    void changeAccountStatus_notFound_returnsBadRequest() throws Exception {
        when(accountService.changeAccountStatus(eq(999L), eq("BLOCKED")))
                .thenThrow(new RuntimeException("Racun sa ID 999 nije pronadjen"));

        String payload = """
                {
                  "status": "BLOCKED"
                }
                """;

        mockMvc.perform(patch("/accounts/999/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /accounts/1/status - 200 OK changes to ACTIVE")
    void changeAccountStatus_toActive() throws Exception {
        AccountResponseDto updated = AccountResponseDto.builder()
                .id(1L).accountNumber("222000112345678910")
                .accountType("CHECKING").status("ACTIVE").build();
        when(accountService.changeAccountStatus(eq(1L), eq("ACTIVE"))).thenReturn(updated);

        String payload = """
                {
                  "status": "ACTIVE"
                }
                """;

        mockMvc.perform(patch("/accounts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("PATCH /accounts/1/status - 200 OK changes to INACTIVE (deactivation)")
    void changeAccountStatus_toInactive() throws Exception {
        AccountResponseDto updated = AccountResponseDto.builder()
                .id(1L).accountNumber("222000112345678910")
                .accountType("CHECKING").status("INACTIVE").build();
        when(accountService.changeAccountStatus(eq(1L), eq("INACTIVE"))).thenReturn(updated);

        String payload = """
                {
                  "status": "INACTIVE"
                }
                """;

        mockMvc.perform(patch("/accounts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /accounts/bank
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /accounts/bank - 200 OK with bank accounts")
    void getBankAccounts_returnsList() throws Exception {
        AccountResponseDto bankAccount = AccountResponseDto.builder()
                .id(100L).name("Banka RSD").accountNumber("222000119999999901")
                .accountType("CHECKING").status("ACTIVE")
                .ownerName("Banka 2")
                .balance(new BigDecimal("50000000"))
                .currencyCode("RSD")
                .build();

        when(accountService.getBankAccounts()).thenReturn(List.of(bankAccount));

        mockMvc.perform(get("/accounts/bank"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ownerName").value("Banka 2"));
    }

    @Test
    @DisplayName("GET /accounts/bank - 200 OK empty list")
    void getBankAccounts_empty() throws Exception {
        when(accountService.getBankAccounts()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/accounts/bank"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ──────────────────────────────────────────────────────────────────
    //  Helper
    // ──────────────────────────────────────────────────────────────────

    private void setupSecurityContext(String email) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(email);
        SecurityContext secCtx = mock(SecurityContext.class);
        when(secCtx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(secCtx);
    }
}
