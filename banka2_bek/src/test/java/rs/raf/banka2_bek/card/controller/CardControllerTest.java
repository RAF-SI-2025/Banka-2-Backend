package rs.raf.banka2_bek.card.controller;

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
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.config.GlobalExceptionHandler;
import rs.raf.banka2_bek.card.dto.CardResponseDto;
import rs.raf.banka2_bek.card.model.CardRequest;
import rs.raf.banka2_bek.card.model.CardStatus;
import rs.raf.banka2_bek.card.repository.CardRequestRepository;
import rs.raf.banka2_bek.card.service.CardService;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class CardControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CardService cardService;

    @Mock
    private CardRequestRepository cardRequestRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private CardController cardController;

    private CardResponseDto testCard;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(cardController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        testCard = CardResponseDto.builder()
                .id(1L)
                .cardNumber("1234567890123456")
                .cardName("Visa Debit")
                .cvv("123")
                .accountNumber("222000112345678910")
                .ownerName("Marko Markovic")
                .cardLimit(new BigDecimal("100000"))
                .status(CardStatus.ACTIVE)
                .createdAt(LocalDate.of(2025, 3, 15))
                .expirationDate(LocalDate.of(2029, 3, 15))
                .build();
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /cards
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /cards - 201 Created")
    void createCard_returnsCreated() throws Exception {
        when(cardService.createCard(any())).thenReturn(testCard);

        String payload = """
                {
                  "accountId": 1,
                  "cardLimit": 100000
                }
                """;

        mockMvc.perform(post("/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.cardNumber").value("1234567890123456"))
                .andExpect(jsonPath("$.cardName").value("Visa Debit"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(cardService).createCard(any());
    }

    @Test
    @DisplayName("POST /cards - 400 when accountId is null")
    void createCard_missingAccountId_returnsBadRequest() throws Exception {
        String payload = """
                {
                  "cardLimit": 100000
                }
                """;

        mockMvc.perform(post("/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /cards - 400 when service throws")
    void createCard_serviceThrows_returnsBadRequest() throws Exception {
        when(cardService.createCard(any())).thenThrow(new IllegalArgumentException("Max cards reached"));

        String payload = """
                {
                  "accountId": 1,
                  "cardLimit": 100000
                }
                """;

        mockMvc.perform(post("/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /cards
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /cards - 200 OK with list of cards")
    void getMyCards_returnsList() throws Exception {
        when(cardService.getMyCards()).thenReturn(List.of(testCard));

        mockMvc.perform(get("/cards")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].cardNumber").value("1234567890123456"));

        verify(cardService).getMyCards();
    }

    @Test
    @DisplayName("GET /cards - 200 OK with empty list")
    void getMyCards_returnsEmptyList() throws Exception {
        when(cardService.getMyCards()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/cards")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /cards/account/{accountId}
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /cards/account/1 - 200 OK")
    void getCardsByAccount_returnsList() throws Exception {
        when(cardService.getCardsByAccount(1L)).thenReturn(List.of(testCard));

        mockMvc.perform(get("/cards/account/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].accountNumber").value("222000112345678910"));

        verify(cardService).getCardsByAccount(1L);
    }

    @Test
    @DisplayName("GET /cards/account/99 - 200 OK with empty list")
    void getCardsByAccount_empty() throws Exception {
        when(cardService.getCardsByAccount(99L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/cards/account/99")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ══════════════════════════════════════════════════════════════════
    //  PATCH /cards/{id}/block
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /cards/1/block - 200 OK")
    void blockCard_returnsOk() throws Exception {
        CardResponseDto blocked = CardResponseDto.builder()
                .id(1L).cardNumber("1234567890123456").status(CardStatus.BLOCKED).build();
        when(cardService.blockCard(1L)).thenReturn(blocked);

        mockMvc.perform(patch("/cards/1/block"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        verify(cardService).blockCard(1L);
    }

    @Test
    @DisplayName("PATCH /cards/999/block - 400 when card not found")
    void blockCard_notFound() throws Exception {
        when(cardService.blockCard(999L)).thenThrow(new IllegalArgumentException("Card not found"));

        mockMvc.perform(patch("/cards/999/block"))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════════
    //  PATCH /cards/{id}/unblock
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /cards/1/unblock - 200 OK")
    void unblockCard_returnsOk() throws Exception {
        CardResponseDto unblocked = CardResponseDto.builder()
                .id(1L).cardNumber("1234567890123456").status(CardStatus.ACTIVE).build();
        when(cardService.unblockCard(1L)).thenReturn(unblocked);

        mockMvc.perform(patch("/cards/1/unblock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(cardService).unblockCard(1L);
    }

    // ══════════════════════════════════════════════════════════════════
    //  PATCH /cards/{id}/deactivate
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /cards/1/deactivate - 200 OK")
    void deactivateCard_returnsOk() throws Exception {
        CardResponseDto deactivated = CardResponseDto.builder()
                .id(1L).cardNumber("1234567890123456").status(CardStatus.DEACTIVATED).build();
        when(cardService.deactivateCard(1L)).thenReturn(deactivated);

        mockMvc.perform(patch("/cards/1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEACTIVATED"));

        verify(cardService).deactivateCard(1L);
    }

    // ══════════════════════════════════════════════════════════════════
    //  PATCH /cards/{id}/limit
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /cards/1/limit - 200 OK")
    void updateCardLimit_returnsOk() throws Exception {
        CardResponseDto updated = CardResponseDto.builder()
                .id(1L).cardLimit(new BigDecimal("200000")).status(CardStatus.ACTIVE).build();
        when(cardService.updateCardLimit(eq(1L), any(BigDecimal.class))).thenReturn(updated);

        String payload = """
                {
                  "cardLimit": 200000
                }
                """;

        mockMvc.perform(patch("/cards/1/limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardLimit").value(200000));

        verify(cardService).updateCardLimit(eq(1L), any(BigDecimal.class));
    }

    @Test
    @DisplayName("PATCH /cards/1/limit - 400 when limit is null")
    void updateCardLimit_missingLimit_returnsBadRequest() throws Exception {
        String payload = """
                {}
                """;

        mockMvc.perform(patch("/cards/1/limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /cards/1/limit - 400 when limit is negative")
    void updateCardLimit_negativeLimit_returnsBadRequest() throws Exception {
        String payload = """
                {
                  "cardLimit": -500
                }
                """;

        mockMvc.perform(patch("/cards/1/limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /cards/requests
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /cards/requests - 201 Created")
    void submitCardRequest_returnsCreated() throws Exception {
        setupSecurityContext("marko@banka.rs");

        Client client = new Client();
        client.setFirstName("Marko");
        client.setLastName("Markovic");
        when(clientRepository.findByEmail("marko@banka.rs")).thenReturn(Optional.of(client));

        Account account = Account.builder().id(1L).accountNumber("222000112345678910").build();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        CardRequest savedReq = CardRequest.builder()
                .id(10L)
                .account(account)
                .cardLimit(new BigDecimal("100000"))
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();
        when(cardRequestRepository.save(any(CardRequest.class))).thenReturn(savedReq);

        String payload = """
                {
                  "accountId": 1,
                  "cardLimit": 100000
                }
                """;

        mockMvc.perform(post("/cards/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.clientName").value("Marko Markovic"));
    }

    @Test
    @DisplayName("POST /cards/requests - uses email as name when client not found")
    void submitCardRequest_clientNotFound_usesEmail() throws Exception {
        setupSecurityContext("unknown@banka.rs");

        when(clientRepository.findByEmail("unknown@banka.rs")).thenReturn(Optional.empty());

        Account account = Account.builder().id(1L).accountNumber("222000112345678910").build();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        CardRequest savedReq = CardRequest.builder()
                .id(11L)
                .account(account)
                .cardLimit(new BigDecimal("100000"))
                .clientEmail("unknown@banka.rs")
                .clientName("unknown@banka.rs")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();
        when(cardRequestRepository.save(any(CardRequest.class))).thenReturn(savedReq);

        String payload = """
                {
                  "accountId": 1,
                  "cardLimit": 100000
                }
                """;

        mockMvc.perform(post("/cards/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientName").value("unknown@banka.rs"));
    }

    @Test
    @DisplayName("POST /cards/requests - uses default limit when cardLimit is null")
    void submitCardRequest_defaultLimit() throws Exception {
        setupSecurityContext("marko@banka.rs");

        Client client = new Client();
        client.setFirstName("Marko");
        client.setLastName("Markovic");
        when(clientRepository.findByEmail("marko@banka.rs")).thenReturn(Optional.of(client));

        Account account = Account.builder().id(1L).accountNumber("222000112345678910").build();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        CardRequest savedReq = CardRequest.builder()
                .id(12L)
                .account(account)
                .cardLimit(new BigDecimal("100000"))
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();
        when(cardRequestRepository.save(any(CardRequest.class))).thenReturn(savedReq);

        String payload = """
                {
                  "accountId": 1
                }
                """;

        mockMvc.perform(post("/cards/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /cards/requests - 400 when account not found")
    void submitCardRequest_accountNotFound() throws Exception {
        setupSecurityContext("marko@banka.rs");
        when(clientRepository.findByEmail("marko@banka.rs")).thenReturn(Optional.empty());
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        String payload = """
                {
                  "accountId": 999
                }
                """;

        mockMvc.perform(post("/cards/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /cards/requests/my
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /cards/requests/my - 200 OK")
    void getMyCardRequests_returnsPage() throws Exception {
        setupSecurityContext("marko@banka.rs");

        Account account = Account.builder().id(1L).accountNumber("222000112345678910").build();
        CardRequest req = CardRequest.builder()
                .id(10L)
                .account(account)
                .cardLimit(new BigDecimal("100000"))
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();

        var pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        Page<CardRequest> page = new PageImpl<>(List.of(req), pageable, 1);
        when(cardRequestRepository.findByClientEmail(eq("marko@banka.rs"), any())).thenReturn(page);

        mockMvc.perform(get("/cards/requests/my")
                        .param("page", "0")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /cards/requests
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /cards/requests - 200 OK all requests")
    void getAllCardRequests_returnsAll() throws Exception {
        Account account = Account.builder().id(1L).accountNumber("222000112345678910").build();
        CardRequest req = CardRequest.builder()
                .id(10L)
                .account(account)
                .cardLimit(new BigDecimal("100000"))
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();

        var pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        Page<CardRequest> page = new PageImpl<>(List.of(req), pageable, 1);
        when(cardRequestRepository.findAll(any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/cards/requests")
                        .param("page", "0")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("GET /cards/requests?status=PENDING - 200 OK filtered")
    void getAllCardRequests_filteredByStatus() throws Exception {
        Account account = Account.builder().id(1L).accountNumber("222000112345678910").build();
        CardRequest req = CardRequest.builder()
                .id(10L)
                .account(account)
                .cardLimit(new BigDecimal("100000"))
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();

        var pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        Page<CardRequest> page = new PageImpl<>(List.of(req), pageable, 1);
        when(cardRequestRepository.findByStatus(eq("PENDING"), any())).thenReturn(page);

        mockMvc.perform(get("/cards/requests")
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  PATCH /cards/requests/{id}/approve
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /cards/requests/10/approve - 200 OK")
    void approveCardRequest_returnsOk() throws Exception {
        setupSecurityContext("employee@banka.rs");

        Client owner = new Client();
        owner.setId(5L);
        owner.setFirstName("Marko");
        owner.setLastName("Markovic");

        Account account = Account.builder().id(1L).accountNumber("222000112345678910").client(owner).build();
        CardRequest req = CardRequest.builder()
                .id(10L)
                .account(account)
                .cardLimit(new BigDecimal("100000"))
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();

        when(cardRequestRepository.findById(10L)).thenReturn(Optional.of(req));
        when(cardRequestRepository.save(any(CardRequest.class))).thenReturn(req);

        mockMvc.perform(patch("/cards/requests/10/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(cardService).createCardForAccount(eq(1L), eq(5L), any(BigDecimal.class), any());
    }

    @Test
    @DisplayName("PATCH /cards/requests/999/approve - 400 when not found")
    void approveCardRequest_notFound() throws Exception {
        setupSecurityContext("employee@banka.rs");
        when(cardRequestRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/cards/requests/999/approve"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /cards/requests/10/approve - 403 when already processed")
    void approveCardRequest_alreadyProcessed() throws Exception {
        setupSecurityContext("employee@banka.rs");

        Account account = Account.builder().id(1L).accountNumber("222000112345678910").build();
        CardRequest req = CardRequest.builder()
                .id(10L)
                .account(account)
                .status("APPROVED")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .cardLimit(new BigDecimal("100000"))
                .build();

        when(cardRequestRepository.findById(10L)).thenReturn(Optional.of(req));

        mockMvc.perform(patch("/cards/requests/10/approve"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /cards/requests/10/approve - 403 when account has no owner")
    void approveCardRequest_noOwner() throws Exception {
        setupSecurityContext("employee@banka.rs");

        Account account = Account.builder().id(1L).accountNumber("222000112345678910").client(null).build();
        CardRequest req = CardRequest.builder()
                .id(10L)
                .account(account)
                .cardLimit(new BigDecimal("100000"))
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();

        when(cardRequestRepository.findById(10L)).thenReturn(Optional.of(req));

        mockMvc.perform(patch("/cards/requests/10/approve"))
                .andExpect(status().isForbidden());
    }

    // ══════════════════════════════════════════════════════════════════
    //  PATCH /cards/requests/{id}/reject
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /cards/requests/10/reject - 200 OK with reason")
    void rejectCardRequest_returnsOk() throws Exception {
        setupSecurityContext("employee@banka.rs");

        Account account = Account.builder().id(1L).accountNumber("222000112345678910").build();
        CardRequest req = CardRequest.builder()
                .id(10L)
                .account(account)
                .cardLimit(new BigDecimal("100000"))
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();

        when(cardRequestRepository.findById(10L)).thenReturn(Optional.of(req));
        when(cardRequestRepository.save(any(CardRequest.class))).thenReturn(req);

        String payload = """
                {
                  "reason": "Nedovoljno sredstava"
                }
                """;

        mockMvc.perform(patch("/cards/requests/10/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("PATCH /cards/requests/10/reject - 200 OK without body")
    void rejectCardRequest_noBody_returnsOk() throws Exception {
        setupSecurityContext("employee@banka.rs");

        Account account = Account.builder().id(1L).accountNumber("222000112345678910").build();
        CardRequest req = CardRequest.builder()
                .id(10L)
                .account(account)
                .cardLimit(new BigDecimal("100000"))
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .build();

        when(cardRequestRepository.findById(10L)).thenReturn(Optional.of(req));
        when(cardRequestRepository.save(any(CardRequest.class))).thenReturn(req);

        mockMvc.perform(patch("/cards/requests/10/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("PATCH /cards/requests/999/reject - 400 when not found")
    void rejectCardRequest_notFound() throws Exception {
        setupSecurityContext("employee@banka.rs");
        when(cardRequestRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/cards/requests/999/reject"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /cards/requests/10/reject - 403 when already processed")
    void rejectCardRequest_alreadyProcessed() throws Exception {
        setupSecurityContext("employee@banka.rs");

        Account account = Account.builder().id(1L).accountNumber("222000112345678910").build();
        CardRequest req = CardRequest.builder()
                .id(10L)
                .account(account)
                .status("REJECTED")
                .createdAt(LocalDateTime.of(2025, 3, 20, 12, 0))
                .clientEmail("marko@banka.rs")
                .clientName("Marko Markovic")
                .cardLimit(new BigDecimal("100000"))
                .build();

        when(cardRequestRepository.findById(10L)).thenReturn(Optional.of(req));

        mockMvc.perform(patch("/cards/requests/10/reject"))
                .andExpect(status().isForbidden());
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
