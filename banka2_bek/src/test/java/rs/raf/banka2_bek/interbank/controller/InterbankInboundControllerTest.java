package rs.raf.banka2_bek.interbank.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptionHandler;
import rs.raf.banka2_bek.interbank.protocol.*;
import rs.raf.banka2_bek.interbank.service.InterbankMessageService;
import rs.raf.banka2_bek.interbank.service.TransactionExecutorService;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for InterbankInboundController (§2.10 auth + §2.2 idempotency + §2.12 dispatch).
 */
@ExtendWith(MockitoExtension.class)
class InterbankInboundControllerTest {

    @Mock
    private InterbankProperties properties;
    @Mock
    private InterbankMessageService messageService;
    @Mock
    private TransactionExecutorService executorService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final int PARTNER_RN = 111;
    private static final String VALID_INBOUND_TOKEN = "validInboundToken";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        InterbankInboundController controller =
                new InterbankInboundController(properties, objectMapper, messageService, executorService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new InterbankExceptionHandler())
                .build();

        InterbankProperties.PartnerBank partner = new InterbankProperties.PartnerBank();
        partner.setRoutingNumber(PARTNER_RN);
        partner.setInboundToken(VALID_INBOUND_TOKEN);
        partner.setOutboundToken("outToken1");
        partner.setBaseUrl("http://bank1:8080");
        lenient().when(properties.getPartners()).thenReturn(List.of(partner));
    }

    // -------------------------------------------------------------------------
    // Auth checks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /interbank without X-Api-Key returns 401")
    void receiveMessage_missingApiKey_returns401() throws Exception {
        mockMvc.perform(post("/interbank")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildNewTxEnvelope()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(messageService, executorService);
    }

    @Test
    @DisplayName("POST /interbank with blank X-Api-Key returns 401")
    void receiveMessage_blankApiKey_returns401() throws Exception {
        mockMvc.perform(post("/interbank")
                        .header("X-Api-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildNewTxEnvelope()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /interbank with unknown token returns 401")
    void receiveMessage_unknownToken_returns401() throws Exception {
        mockMvc.perform(post("/interbank")
                        .header("X-Api-Key", "wrongToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildNewTxEnvelope()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /interbank with routing number mismatch returns 401")
    void receiveMessage_routingNumberMismatch_returns401() throws Exception {
        // Key says routing=999, but partner has routing=111
        String envelope = buildEnvelopeJson(999, "somekey", MessageType.COMMIT_TX,
                objectMapper.writeValueAsString(new CommitTransaction(new ForeignBankId(999, "x"))));

        mockMvc.perform(post("/interbank")
                        .header("X-Api-Key", VALID_INBOUND_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(envelope))
                .andExpect(status().isUnauthorized());
    }
    @Test
    @DisplayName("POST /interbank with malformed envelope returns 400")
    void receiveMessage_malformedEnvelope_returns400() throws Exception {
        String malformedEnvelope = """
            {
              "idempotenceKey": {
                "routingNumber": 111,
                "locallyGeneratedKey": "key-bad"
              },
              "messageType": "NEW_TX"
            }
            """;

        mockMvc.perform(post("/interbank")
                        .header("X-Api-Key", VALID_INBOUND_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedEnvelope))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, executorService);
    }
    @Test
    @DisplayName("POST /interbank returns 500 when executor throws unexpected exception")
    void receiveMessage_executorThrows_returns500() throws Exception {
        when(messageService.findCachedResponse(any())).thenReturn(Optional.empty());
        when(executorService.handleNewTx(any(Transaction.class), any(IdempotenceKey.class)))
                .thenThrow(new RuntimeException("unexpected error"));

        mockMvc.perform(post("/interbank")
                        .header("X-Api-Key", VALID_INBOUND_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildNewTxEnvelope()))
                .andExpect(status().isInternalServerError());

        verify(executorService).handleNewTx(any(Transaction.class), any(IdempotenceKey.class));
    }

    // -------------------------------------------------------------------------
    // Idempotency cache hit
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Cache hit with response body returns 200 with cached body")
    void receiveMessage_cacheHitWithBody_returns200() throws Exception {
        when(messageService.findCachedResponse(any())).thenReturn(Optional.of("{\"vote\":\"YES\"}"));

        mockMvc.perform(post("/interbank")
                        .header("X-Api-Key", VALID_INBOUND_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildNewTxEnvelope()))
                .andExpect(status().isOk());

        verifyNoInteractions(executorService);
    }

    @Test
    @DisplayName("Cache hit with blank body returns 204")
    void receiveMessage_cacheHitBlankBody_returns204() throws Exception {
        when(messageService.findCachedResponse(any())).thenReturn(Optional.of(""));

        mockMvc.perform(post("/interbank")
                        .header("X-Api-Key", VALID_INBOUND_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildNewTxEnvelope()))
                .andExpect(status().isNoContent());

        verifyNoInteractions(executorService);
    }

    // -------------------------------------------------------------------------
    // Dispatch paths (cache miss)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Cache miss + NEW_TX dispatches to handleNewTx and returns 200")
    void receiveMessage_newTxCacheMiss_dispatches() throws Exception {
        when(messageService.findCachedResponse(any())).thenReturn(Optional.empty());
        TransactionVote vote = new TransactionVote(TransactionVote.Vote.YES, List.of());
        when(executorService.handleNewTx(any(), any())).thenReturn(vote);

        mockMvc.perform(post("/interbank")
                        .header("X-Api-Key", VALID_INBOUND_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildNewTxEnvelope()))
                .andExpect(status().isOk());

        verify(executorService).handleNewTx(any(Transaction.class), any(IdempotenceKey.class));
    }

    @Test
    @DisplayName("Cache miss + COMMIT_TX dispatches to handleCommitTx and returns 204")
    void receiveMessage_commitTxCacheMiss_dispatches() throws Exception {
        when(messageService.findCachedResponse(any())).thenReturn(Optional.empty());
        doNothing().when(executorService).handleCommitTx(any(), any());

        mockMvc.perform(post("/interbank")
                        .header("X-Api-Key", VALID_INBOUND_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildCommitTxEnvelope()))
                .andExpect(status().isNoContent());

        verify(executorService).handleCommitTx(any(CommitTransaction.class), any(IdempotenceKey.class));
    }

    @Test
    @DisplayName("Cache miss + ROLLBACK_TX dispatches to handleRollbackTx and returns 204")
    void receiveMessage_rollbackTxCacheMiss_dispatches() throws Exception {
        when(messageService.findCachedResponse(any())).thenReturn(Optional.empty());
        doNothing().when(executorService).handleRollbackTx(any(), any());

        mockMvc.perform(post("/interbank")
                        .header("X-Api-Key", VALID_INBOUND_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRollbackTxEnvelope()))
                .andExpect(status().isNoContent());

        verify(executorService).handleRollbackTx(any(RollbackTransaction.class), any(IdempotenceKey.class));
    }


    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildNewTxEnvelope() throws Exception {
        ForeignBankId txId = new ForeignBankId(PARTNER_RN, "uuid-tx-1");
        Transaction tx = new Transaction(List.of(), txId, "test", null, null, null);
        return buildEnvelopeJson(PARTNER_RN, "key0000001", MessageType.NEW_TX,
                objectMapper.writeValueAsString(tx));
    }

    private String buildCommitTxEnvelope() throws Exception {
        ForeignBankId txId = new ForeignBankId(PARTNER_RN, "uuid-tx-1");
        return buildEnvelopeJson(PARTNER_RN, "key0000002", MessageType.COMMIT_TX,
                objectMapper.writeValueAsString(new CommitTransaction(txId)));
    }

    private String buildRollbackTxEnvelope() throws Exception {
        ForeignBankId txId = new ForeignBankId(PARTNER_RN, "uuid-tx-1");
        return buildEnvelopeJson(PARTNER_RN, "key0000003", MessageType.ROLLBACK_TX,
                objectMapper.writeValueAsString(new RollbackTransaction(txId)));
    }

    /**
     * Builds a raw JSON envelope string. The `message` field is embedded as a raw JSON node
     * so it remains properly nested when parsed.
     */
    private String buildEnvelopeJson(int routingNumber, String localKey,
                                     MessageType type, String messageJson) {
        return String.format(
                "{\"idempotenceKey\":{\"routingNumber\":%d,\"locallyGeneratedKey\":\"%s\"}," +
                "\"messageType\":\"%s\"," +
                "\"message\":%s}",
                routingNumber, localKey, type.name(), messageJson
        );
    }
}
