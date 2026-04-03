package rs.raf.banka2_bek.transfers.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.config.GlobalExceptionHandler;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.otp.service.OtpService;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.transfers.dto.TransferResponseDto;
import rs.raf.banka2_bek.transfers.service.TransferService;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class TransferControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TransferService transferService;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private OtpService otpService;

    @InjectMocks
    private TransferController transferController;

    private TransferResponseDto testTransfer;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(transferController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        testTransfer = new TransferResponseDto();
        testTransfer.setFromAccountNumber("111111111111111111");
        testTransfer.setToAccountNumber("222222222222222222");
        testTransfer.setAmount(new BigDecimal("500.00"));
        testTransfer.setExchangeRate(BigDecimal.ONE);
        testTransfer.setCommission(BigDecimal.ZERO);
        testTransfer.setClientFirstName("Marko");
        testTransfer.setClientLastName("Markovic");
        testTransfer.setStatus(PaymentStatus.COMPLETED);

        // OTP verification always succeeds in controller tests
        when(otpService.verify(anyString(), anyString())).thenReturn(Map.of("verified", true));

        // Set up authentication so controller can extract email
        setupSecurityContext("marko@banka.rs");
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /transfers/internal — same currency
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /transfers/internal - 200 OK same currency")
    void internalTransfer_sameCurrency_returnsOk() throws Exception {
        Currency rsd = Currency.builder().id(1L).code("RSD").build();
        Account from = Account.builder().accountNumber("111111111111111111").currency(rsd).build();
        Account to = Account.builder().accountNumber("222222222222222222").currency(rsd).build();

        when(accountRepository.findByAccountNumber("111111111111111111")).thenReturn(Optional.of(from));
        when(accountRepository.findByAccountNumber("222222222222222222")).thenReturn(Optional.of(to));
        when(transferService.internalTransfer(any())).thenReturn(testTransfer);

        String payload = """
                {
                  "fromAccountNumber": "111111111111111111",
                  "toAccountNumber": "222222222222222222",
                  "amount": 500.00,
                  "otpCode": "123456"
                }
                """;

        mockMvc.perform(post("/transfers/internal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .principal(SecurityContextHolder.getContext().getAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromAccountNumber").value("111111111111111111"))
                .andExpect(jsonPath("$.toAccountNumber").value("222222222222222222"))
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(transferService).internalTransfer(any());
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /transfers/internal — cross currency (auto-routes to FX)
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /transfers/internal - 200 OK cross currency routes to FX via service")
    void internalTransfer_crossCurrency_routesToFx() throws Exception {
        // The controller now always calls internalTransfer; FX routing happens inside the service
        TransferResponseDto fxResponse = new TransferResponseDto();
        fxResponse.setFromAccountNumber("111111111111111111");
        fxResponse.setToAccountNumber("222222222222222222");
        fxResponse.setAmount(new BigDecimal("500.00"));
        fxResponse.setExchangeRate(new BigDecimal("1.08"));
        fxResponse.setCommission(new BigDecimal("2.50"));
        fxResponse.setStatus(PaymentStatus.COMPLETED);

        when(transferService.internalTransfer(any())).thenReturn(fxResponse);

        String payload = """
                {
                  "fromAccountNumber": "111111111111111111",
                  "toAccountNumber": "222222222222222222",
                  "amount": 500.00,
                  "otpCode": "123456"
                }
                """;

        mockMvc.perform(post("/transfers/internal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .principal(SecurityContextHolder.getContext().getAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchangeRate").value(1.08))
                .andExpect(jsonPath("$.commission").value(2.50));

        verify(transferService).internalTransfer(any());
    }

    @Test
    @DisplayName("POST /transfers/internal - 200 OK when accounts not found (falls through to internal)")
    void internalTransfer_accountsNotFound_fallsToInternal() throws Exception {
        when(accountRepository.findByAccountNumber("111111111111111111")).thenReturn(Optional.empty());
        when(accountRepository.findByAccountNumber("222222222222222222")).thenReturn(Optional.empty());
        when(transferService.internalTransfer(any())).thenReturn(testTransfer);

        String payload = """
                {
                  "fromAccountNumber": "111111111111111111",
                  "toAccountNumber": "222222222222222222",
                  "amount": 500.00,
                  "otpCode": "123456"
                }
                """;

        mockMvc.perform(post("/transfers/internal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .principal(SecurityContextHolder.getContext().getAuthentication()))
                .andExpect(status().isOk());

        verify(transferService).internalTransfer(any());
    }

    @Test
    @DisplayName("POST /transfers/internal - 400 when service throws")
    void internalTransfer_serviceThrows() throws Exception {
        when(accountRepository.findByAccountNumber(any())).thenReturn(Optional.empty());
        when(transferService.internalTransfer(any()))
                .thenThrow(new IllegalArgumentException("Insufficient funds"));

        String payload = """
                {
                  "fromAccountNumber": "111111111111111111",
                  "toAccountNumber": "222222222222222222",
                  "amount": 500.00,
                  "otpCode": "123456"
                }
                """;

        mockMvc.perform(post("/transfers/internal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .principal(SecurityContextHolder.getContext().getAuthentication()))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /transfers/fx
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /transfers/fx - 200 OK")
    void fxTransfer_returnsOk() throws Exception {
        TransferResponseDto fxResponse = new TransferResponseDto();
        fxResponse.setFromAccountNumber("111111111111111111");
        fxResponse.setToAccountNumber("222222222222222222");
        fxResponse.setAmount(new BigDecimal("500.00"));
        fxResponse.setExchangeRate(new BigDecimal("1.08"));
        fxResponse.setStatus(PaymentStatus.COMPLETED);

        when(transferService.fxTransfer(any())).thenReturn(fxResponse);

        String payload = """
                {
                  "fromAccountNumber": "111111111111111111",
                  "toAccountNumber": "222222222222222222",
                  "amount": 500.00,
                  "otpCode": "123456"
                }
                """;

        mockMvc.perform(post("/transfers/fx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .principal(SecurityContextHolder.getContext().getAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchangeRate").value(1.08));

        verify(transferService).fxTransfer(any());
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /transfers
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /transfers - 200 OK with transfers for authenticated client")
    void getAllTransfers_returnsTransfers() throws Exception {
        setupSecurityContext("marko@banka.rs");

        Client client = new Client();
        client.setEmail("marko@banka.rs");
        when(clientRepository.findByEmail("marko@banka.rs")).thenReturn(Optional.of(client));
        when(transferService.getAllTransfers(eq(client), isNull(), isNull(), isNull())).thenReturn(List.of(testTransfer));

        mockMvc.perform(get("/transfers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fromAccountNumber").value("111111111111111111"));
    }

    @Test
    @DisplayName("GET /transfers - 200 OK empty when client not found")
    void getAllTransfers_clientNotFound_returnsEmpty() throws Exception {
        setupSecurityContext("unknown@banka.rs");
        when(clientRepository.findByEmail("unknown@banka.rs")).thenReturn(Optional.empty());

        mockMvc.perform(get("/transfers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /transfers - 200 OK empty when no authentication")
    void getAllTransfers_noAuth_returnsEmpty() throws Exception {
        SecurityContext secCtx = mock(SecurityContext.class);
        when(secCtx.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(secCtx);

        mockMvc.perform(get("/transfers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /transfers/{transferId}
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /transfers/1 - 200 OK")
    void getTransferById_returnsOk() throws Exception {
        when(transferService.getTransferById(1L)).thenReturn(testTransfer);

        mockMvc.perform(get("/transfers/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromAccountNumber").value("111111111111111111"))
                .andExpect(jsonPath("$.amount").value(500.00));

        verify(transferService).getTransferById(1L);
    }

    @Test
    @DisplayName("GET /transfers/999 - 400 when not found")
    void getTransferById_notFound() throws Exception {
        when(transferService.getTransferById(999L))
                .thenThrow(new IllegalArgumentException("Transfer not found"));

        mockMvc.perform(get("/transfers/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────────────────────────
    //  Helper
    // ──────────────────────────────────────────────────────────────────

    private void setupSecurityContext(String email) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(email);
        when(auth.getPrincipal()).thenReturn(email);
        when(auth.isAuthenticated()).thenReturn(true);
        SecurityContext secCtx = mock(SecurityContext.class);
        when(secCtx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(secCtx);
    }
}
