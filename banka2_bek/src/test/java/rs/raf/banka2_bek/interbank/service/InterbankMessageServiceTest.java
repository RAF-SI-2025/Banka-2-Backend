package rs.raf.banka2_bek.interbank.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.model.InterbankMessage;
import rs.raf.banka2_bek.interbank.model.InterbankMessageDirection;
import rs.raf.banka2_bek.interbank.model.InterbankMessageStatus;
import rs.raf.banka2_bek.interbank.protocol.IdempotenceKey;
import rs.raf.banka2_bek.interbank.protocol.MessageType;
import rs.raf.banka2_bek.interbank.repository.InterbankMessageRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InterbankMessageService (§2.2 idempotency + §2.9 message log).
 */
@ExtendWith(MockitoExtension.class)
class InterbankMessageServiceTest {

    @Mock
    private InterbankMessageRepository repository;

    @Mock
    private BankRoutingService bankRoutingService;

    @InjectMocks
    private InterbankMessageService service;

    private static final int MY_RN = 222;
    private static final IdempotenceKey KEY = new IdempotenceKey(111, "abc123key");

    // -------------------------------------------------------------------------
    // findCachedResponse
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findCachedResponse returns body when message exists with non-null response")
    void findCachedResponse_hit_returnsBody() {
        InterbankMessage msg = InterbankMessage.builder()
                .responseBody("{\"vote\":\"YES\"}")
                .build();
        when(repository.findBySenderRoutingNumberAndLocallyGeneratedKey(111, "abc123key"))
                .thenReturn(Optional.of(msg));

        Optional<String> result = service.findCachedResponse(KEY);

        assertThat(result).contains("{\"vote\":\"YES\"}");
    }

    @Test
    @DisplayName("findCachedResponse returns empty Optional when response body is null")
    void findCachedResponse_nullResponseBody_returnsEmpty() {
        InterbankMessage msg = InterbankMessage.builder()
                .responseBody(null)
                .build();
        when(repository.findBySenderRoutingNumberAndLocallyGeneratedKey(111, "abc123key"))
                .thenReturn(Optional.of(msg));

        assertThat(service.findCachedResponse(KEY)).isEmpty();
    }

    @Test
    @DisplayName("findCachedResponse returns empty Optional when message not found")
    void findCachedResponse_miss_returnsEmpty() {
        when(repository.findBySenderRoutingNumberAndLocallyGeneratedKey(111, "abc123key"))
                .thenReturn(Optional.empty());

        assertThat(service.findCachedResponse(KEY)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // recordInboundResponse
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("recordInboundResponse saves message with INBOUND direction, status, and all fields")
    void recordInboundResponse_savesCorrectEntity() {
        ArgumentCaptor<InterbankMessage> captor = ArgumentCaptor.forClass(InterbankMessage.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordInboundResponse(KEY, MessageType.NEW_TX, "{\"tx\":1}", 200, "{\"vote\":\"YES\"}", "txId-42");

        verify(repository).save(captor.capture());
        InterbankMessage saved = captor.getValue();

        assertThat(saved.getDirection()).isEqualTo(InterbankMessageDirection.INBOUND);
        assertThat(saved.getStatus()).isEqualTo(InterbankMessageStatus.INBOUND);
        assertThat(saved.getSenderRoutingNumber()).isEqualTo(111);
        assertThat(saved.getLocallyGeneratedKey()).isEqualTo("abc123key");
        assertThat(saved.getMessageType()).isEqualTo(MessageType.NEW_TX);
        assertThat(saved.getRequestBody()).isEqualTo("{\"tx\":1}");
        assertThat(saved.getResponseBody()).isEqualTo("{\"vote\":\"YES\"}");
        assertThat(saved.getHttpStatus()).isEqualTo(200);
        assertThat(saved.getTransactionId()).isEqualTo("txId-42");
        assertThat(saved.getRetryCount()).isEqualTo(0);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // generateKey
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateKey uses myRoutingNumber")
    void generateKey_routingNumberFromService() {
        when(bankRoutingService.myRoutingNumber()).thenReturn(MY_RN);

        IdempotenceKey key = service.generateKey();

        assertThat(key.routingNumber()).isEqualTo(MY_RN);
    }

    @Test
    @DisplayName("generateKey produces 64-char hex locallyGeneratedKey")
    void generateKey_localKeyIs64HexChars() {
        when(bankRoutingService.myRoutingNumber()).thenReturn(MY_RN);

        IdempotenceKey key = service.generateKey();

        assertThat(key.locallyGeneratedKey())
                .hasSize(64)
                .matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("generateKey produces unique keys on each call")
    void generateKey_uniquePerCall() {
        when(bankRoutingService.myRoutingNumber()).thenReturn(MY_RN);

        IdempotenceKey k1 = service.generateKey();
        IdempotenceKey k2 = service.generateKey();

        assertThat(k1.locallyGeneratedKey()).isNotEqualTo(k2.locallyGeneratedKey());
    }

    // -------------------------------------------------------------------------
    // recordOutbound
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("recordOutbound saves PENDING OUTBOUND message with correct fields")
    void recordOutbound_savesPendingOutbound() {
        ArgumentCaptor<InterbankMessage> captor = ArgumentCaptor.forClass(InterbankMessage.class);
        InterbankMessage saved = InterbankMessage.builder().id(1L).build();
        when(repository.save(any())).thenReturn(saved);

        service.recordOutbound(KEY, 333, MessageType.NEW_TX, "{\"msg\":1}", "txId-7");

        verify(repository).save(captor.capture());
        InterbankMessage msg = captor.getValue();

        assertThat(msg.getDirection()).isEqualTo(InterbankMessageDirection.OUTBOUND);
        assertThat(msg.getStatus()).isEqualTo(InterbankMessageStatus.PENDING);
        assertThat(msg.getSenderRoutingNumber()).isEqualTo(111);
        assertThat(msg.getLocallyGeneratedKey()).isEqualTo("abc123key");
        assertThat(msg.getMessageType()).isEqualTo(MessageType.NEW_TX);
        assertThat(msg.getRequestBody()).isEqualTo("{\"msg\":1}");
        assertThat(msg.getPeerRoutingNumber()).isEqualTo(333);
        assertThat(msg.getTransactionId()).isEqualTo("txId-7");
        assertThat(msg.getRetryCount()).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // markOutboundSent
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("markOutboundSent with 200 sets status=SENT and stores response body")
    void markOutboundSent_200_setsStatusSent() {
        InterbankMessage msg = pendingMessage();
        when(repository.findBySenderRoutingNumberAndLocallyGeneratedKey(111, "abc123key"))
                .thenReturn(Optional.of(msg));

        service.markOutboundSent(KEY, 200, "{\"vote\":\"YES\"}");

        assertThat(msg.getStatus()).isEqualTo(InterbankMessageStatus.SENT);
        assertThat(msg.getHttpStatus()).isEqualTo(200);
        assertThat(msg.getResponseBody()).isEqualTo("{\"vote\":\"YES\"}");
    }

    @Test
    @DisplayName("markOutboundSent with 204 sets status=SENT")
    void markOutboundSent_204_setsStatusSent() {
        InterbankMessage msg = pendingMessage();
        when(repository.findBySenderRoutingNumberAndLocallyGeneratedKey(111, "abc123key"))
                .thenReturn(Optional.of(msg));

        service.markOutboundSent(KEY, 204, null);

        assertThat(msg.getStatus()).isEqualTo(InterbankMessageStatus.SENT);
    }

    @Test
    @DisplayName("markOutboundSent with 202 keeps status PENDING and increments retryCount")
    void markOutboundSent_202_staysPendingAndIncrementsRetry() {
        InterbankMessage msg = pendingMessage();
        when(repository.findBySenderRoutingNumberAndLocallyGeneratedKey(111, "abc123key"))
                .thenReturn(Optional.of(msg));

        service.markOutboundSent(KEY, 202, null);

        assertThat(msg.getStatus()).isEqualTo(InterbankMessageStatus.PENDING);
        assertThat(msg.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("markOutboundSent throws when message not found")
    void markOutboundSent_notFound_throws() {
        when(repository.findBySenderRoutingNumberAndLocallyGeneratedKey(any(Integer.class), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markOutboundSent(KEY, 200, null))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class);
    }

    // -------------------------------------------------------------------------
    // markOutboundFailed
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("markOutboundFailed increments retryCount and stores error, stays PENDING below threshold")
    void markOutboundFailed_belowMaxRetries_staysPending() {
        InterbankMessage msg = pendingMessage(); // retryCount=0
        when(repository.findBySenderRoutingNumberAndLocallyGeneratedKey(111, "abc123key"))
                .thenReturn(Optional.of(msg));

        service.markOutboundFailed(KEY, "timeout");

        assertThat(msg.getRetryCount()).isEqualTo(1);
        assertThat(msg.getLastError()).isEqualTo("timeout");
        assertThat(msg.getStatus()).isEqualTo(InterbankMessageStatus.PENDING);
        verify(repository).save(msg);
    }

    @Test
    @DisplayName("markOutboundFailed transitions to STUCK when retryCount reaches MAX_RETRIES")
    void markOutboundFailed_atMaxRetries_becomesStuck() {
        InterbankMessage msg = pendingMessage();
        msg.setRetryCount(4); // MAX_RETRIES=5; after increment → 5 → STUCK
        when(repository.findBySenderRoutingNumberAndLocallyGeneratedKey(111, "abc123key"))
                .thenReturn(Optional.of(msg));

        service.markOutboundFailed(KEY, "connection refused");

        assertThat(msg.getRetryCount()).isEqualTo(5);
        assertThat(msg.getStatus()).isEqualTo(InterbankMessageStatus.STUCK);
    }

    @Test
    @DisplayName("markOutboundFailed throws when message not found")
    void markOutboundFailed_notFound_throws() {
        when(repository.findBySenderRoutingNumberAndLocallyGeneratedKey(any(Integer.class), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markOutboundFailed(KEY, "error"))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private InterbankMessage pendingMessage() {
        return InterbankMessage.builder()
                .senderRoutingNumber(111)
                .locallyGeneratedKey("abc123key")
                .direction(InterbankMessageDirection.OUTBOUND)
                .status(InterbankMessageStatus.PENDING)
                .retryCount(0)
                .build();
    }
}
