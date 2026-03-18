package rs.raf.banka2_bek.account.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.raf.banka2_bek.account.controller.exception_handler.AccountExceptionHandler;
import rs.raf.banka2_bek.account.dto.AccountResponseDto;
import rs.raf.banka2_bek.company.dto.CompanyDto;
import rs.raf.banka2_bek.account.service.AccountService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AccountService accountService;

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

    // ══════════════════════════════════════════════════════════════════════════
    //  GET /accounts/my
    // ══════════════════════════════════════════════════════════════════════════

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
    @DisplayName("GET /accounts/my - 404 kada klijent ne postoji")
    void getMyAccounts_clientNotFound_returns404() throws Exception {
        when(accountService.getMyAccounts())
                .thenThrow(new IllegalArgumentException("Client with email test@example.com not found."));

        mockMvc.perform(get("/accounts/my")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Client with email test@example.com not found."));
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

    // ══════════════════════════════════════════════════════════════════════════
    //  GET /accounts/{id}
    // ══════════════════════════════════════════════════════════════════════════

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
    @DisplayName("GET /accounts/999 - 404 kada racun ne postoji")
    void getAccountById_notFound_returns404() throws Exception {
        when(accountService.getAccountById(999L))
                .thenThrow(new IllegalArgumentException("Account with ID 999 not found."));

        mockMvc.perform(get("/accounts/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Account with ID 999 not found."));
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
}
