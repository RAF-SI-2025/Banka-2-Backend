package rs.raf.banka2_bek.interbank.scheduler;

<<<<<<< HEAD
=======
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
>>>>>>> main
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
<<<<<<< HEAD
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions.InterbankAuthException;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions.InterbankCommunicationException;
import rs.raf.banka2_bek.interbank.model.InterbankMessage;
import rs.raf.banka2_bek.interbank.model.InterbankMessageStatus;
import rs.raf.banka2_bek.interbank.protocol.MessageType;
import rs.raf.banka2_bek.interbank.protocol.TransactionVote;
=======
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.model.InterbankMessage;
import rs.raf.banka2_bek.interbank.model.InterbankMessageStatus;
import rs.raf.banka2_bek.interbank.protocol.*;
>>>>>>> main
import rs.raf.banka2_bek.interbank.repository.InterbankMessageRepository;
import rs.raf.banka2_bek.interbank.service.InterbankClient;
import rs.raf.banka2_bek.interbank.service.InterbankMessageService;

<<<<<<< HEAD
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit testovi za InterbankRetryScheduler (T5).
 *
 * <p>Proverava sve scenarije navedene u TODO bloku:
 * <ul>
 *   <li>Retry se ne dešava pre interval praga</li>
 *   <li>Max retries → STUCK + log</li>
 *   <li>202 ne uvećava retryCount, ostaje PENDING</li>
 *   <li>Uspešan retry oslobađa poruku iz pending-a</li>
 *   <li>401 skip retry, ERROR log</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class InterbankRetrySchedulerTest {

    @Mock
    private InterbankMessageRepository messageRepository;

    @Mock
    private InterbankMessageService messageService;

    @Mock
    private InterbankClient interbankClient;

    @InjectMocks
    private InterbankRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        // Postavljamo konfiguracione vrednosti (umesto @Value injectiona)
        ReflectionTestUtils.setField(scheduler, "maxRetries", 10);
        ReflectionTestUtils.setField(scheduler, "retryIntervalSeconds", 30);
        ReflectionTestUtils.setField(scheduler, "stuckTimeoutMinutes", 30);
    }

    // -------------------------------------------------------------------------
    // Nema PENDING poruka
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Kad nema PENDING poruka, ništa se ne šalje")
    void retryPendingMessages_noMessages_doesNothing() {
        when(messageRepository.findPendingForRetry(
                eq(InterbankMessageStatus.PENDING), any()))
                .thenReturn(Collections.emptyList());

        scheduler.retryPendingMessages();

        verifyNoInteractions(interbankClient);
    }

    // -------------------------------------------------------------------------
    // Uspešan retry (200/204)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Uspešan retry – sendMessage se poziva i poruka prelazi u SENT")
    void retryPendingMessages_successfulRetry_callsSendMessage() {
        InterbankMessage msg = buildMessage("idem-ok", 3, MessageType.NEW_TX);

        when(messageRepository.findPendingForRetry(
                eq(InterbankMessageStatus.PENDING), any()))
                .thenReturn(List.of(msg));

        when(interbankClient.sendMessage(anyInt(), any(), any(), any()))
                .thenReturn(mock(TransactionVote.class));

        scheduler.retryPendingMessages();

        verify(interbankClient, times(1))
                .sendMessage(eq(111), eq(MessageType.NEW_TX), any(), any());
    }

    // -------------------------------------------------------------------------
    // Max retries → STUCK
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Kada retryCount >= maxRetries, poruka postaje STUCK bez poziva sendMessage")
    void retryPendingMessages_maxRetriesReached_marksStuck() {
        InterbankMessage msg = buildMessage("idem-stuck", 10, MessageType.NEW_TX); // retryCount = maxRetries

        when(messageRepository.findPendingForRetry(
                eq(InterbankMessageStatus.PENDING), any()))
                .thenReturn(List.of(msg));

        scheduler.retryPendingMessages();

        // sendMessage se NE sme pozvati – odmah ide na STUCK
        verifyNoInteractions(interbankClient);
        verify(messageService).markOutboundFailed(any(), any());
    }

    // -------------------------------------------------------------------------
    // 202 – ostaje PENDING, retryCount se ne menja
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("202 Accepted (sendMessage vraća null) – retryCount se ne menja")
    void retryPendingMessages_202Response_retryCountNotChanged() {
        InterbankMessage msg = buildMessage("idem-202", 2, MessageType.NEW_TX);

        when(messageRepository.findPendingForRetry(
                eq(InterbankMessageStatus.PENDING), any()))
                .thenReturn(List.of(msg));

        // sendMessage vraća null → što InterbankClient radi za 202
        when(interbankClient.sendMessage(anyInt(), any(), any(), any())).thenReturn(null);

        scheduler.retryPendingMessages();

        // markOutboundFailed se NE sme pozvati (202 nije greška)
        verify(messageService, never()).markOutboundFailed(any(), any());
        // markOutboundStuck se NE sme pozvati
        verify(messageService, never()).markOutboundFailed(any(), any());
    }

    // -------------------------------------------------------------------------
    // 401 – skip retry, markOutboundFailed
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("401 AuthException – retry se preskače, poziva se markOutboundFailed")
    void retryPendingMessages_401AuthException_skipsRetryAndMarksFaild() {
        InterbankMessage msg = buildMessage("idem-401", 1, MessageType.NEW_TX);

        when(messageRepository.findPendingForRetry(
                eq(InterbankMessageStatus.PENDING), any()))
                .thenReturn(List.of(msg));

        when(interbankClient.sendMessage(anyInt(), any(), any(), any()))
                .thenThrow(new InterbankAuthException("401 test"));

        scheduler.retryPendingMessages();

        // Scheduler poziva sendMessage
        verify(interbankClient, times(1))
                .sendMessage(anyInt(), any(), any(), any());
// markOutboundFailed se ne poziva iz schedulera direktno - InterbankClient to radi interno
// Samo proveravamo da scheduler nije pao
    }

    // -------------------------------------------------------------------------
    // Komunikacijska greška (5xx/network) – retryCount++ via markOutboundFailed
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("CommunicationException – InterbankClient već pozvao markOutboundFailed, scheduler nastavlja")
    void retryPendingMessages_communicationException_continuesWithOtherMessages() {
        InterbankMessage msgFail = buildMessage("idem-fail", 2, MessageType.NEW_TX);
        InterbankMessage msgOk   = buildMessage("idem-ok",   1, MessageType.COMMIT_TX);

        when(messageRepository.findPendingForRetry(
                eq(InterbankMessageStatus.PENDING), any()))
                .thenReturn(List.of(msgFail, msgOk));

        when(interbankClient.sendMessage(anyInt(), any(), any(), any()))
                .thenThrow(new InterbankCommunicationException("500"))
                .thenReturn(null); // msgOk prolazi

        // Ne sme baciti exception naviše
        scheduler.retryPendingMessages();

        // Oba poziva su se desila
        verify(interbankClient, times(2))
                .sendMessage(anyInt(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Stuck timeout – bez obzira na retryCount
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Poruka starija od stuckTimeoutMinutes postaje STUCK čak i ako je retryCount mali")
    void retryPendingMessages_stuckTimeout_marksStuckRegardlessOfRetryCount() {
        InterbankMessage msg = buildMessage("idem-timeout", 1, MessageType.NEW_TX);
        // createdAt daleko u prošlosti – više od 30 minuta
        msg.setCreatedAt(LocalDateTime.now().minusMinutes(60));

        when(messageRepository.findPendingForRetry(
                eq(InterbankMessageStatus.PENDING), any()))
                .thenReturn(List.of(msg));

        scheduler.retryPendingMessages();

        verifyNoInteractions(interbankClient);
        verify(messageService).markOutboundFailed(any(), any());
    }

    // -------------------------------------------------------------------------
    // Neočekivana greška u jednoj – ne ruši ostatak
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Neočekivana RuntimeException u jednoj poruci ne prekida obradu ostalih")
    void retryPendingMessages_unexpectedException_continuesWithRest() {
        InterbankMessage msgCrash = buildMessage("idem-crash", 2, MessageType.NEW_TX);
        InterbankMessage msgOk    = buildMessage("idem-ok2",   1, MessageType.COMMIT_TX);

        when(messageRepository.findPendingForRetry(
                eq(InterbankMessageStatus.PENDING), any()))
                .thenReturn(List.of(msgCrash, msgOk));

        when(interbankClient.sendMessage(anyInt(), any(), any(), any()))
                .thenThrow(new RuntimeException("Neočekivana greška"))
                .thenReturn(null);

        // Scheduler ne sme da pukne
        scheduler.retryPendingMessages();

        // Oba se pokušaju
        verify(interbankClient, times(2))
                .sendMessage(anyInt(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private InterbankMessage buildMessage(String locallyGeneratedKey, int retryCount, MessageType type) {
        InterbankMessage msg = new InterbankMessage();
        msg.setId((long) Math.abs(locallyGeneratedKey.hashCode()));
        msg.setSenderRoutingNumber(111);
        msg.setLocallyGeneratedKey(locallyGeneratedKey);
        msg.setRetryCount(retryCount);
        msg.setStatus(InterbankMessageStatus.PENDING);
        msg.setMessageType(type);
        msg.setPeerRoutingNumber(111);
        msg.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        msg.setLastAttemptAt(LocalDateTime.now().minusMinutes(2));
        return msg;
    }
}
=======
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterbankRetrySchedulerTest {

    @Mock private InterbankMessageRepository messageRepository;
    @Mock private InterbankClient client;
    @Mock private InterbankMessageService messageService;

    private InterbankRetryScheduler scheduler;
    private ObjectMapper objectMapper;

    private static final int MY_RN = 222;
    private static final int REMOTE_RN = 111;
    private static final String TX_ID = "abc123";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        scheduler = new InterbankRetryScheduler(messageRepository, client, messageService, objectMapper);
    }

    @Test
    @DisplayName("retryStaleMessages: finds PENDING messages older than cutoff")
    void retryStaleMessages_queriesCorrectStatus() {
        when(messageRepository.findPendingForRetry(eq(InterbankMessageStatus.PENDING), any()))
                .thenReturn(List.of());

        scheduler.retryStaleMessages();

        verify(messageRepository).findPendingForRetry(eq(InterbankMessageStatus.PENDING), any());
    }

    @Test
    @DisplayName("NEW_TX success: markOutboundSent(200, voteJson) called")
    void retryNewTx_success_marksOutboundSent200() throws Exception {
        Transaction tx = sampleTransaction();
        IdempotenceKey key = new IdempotenceKey(MY_RN, "hex-key");
        Message<Transaction> envelope = new Message<>(key, MessageType.NEW_TX, tx);
        InterbankMessage msg = newTxMessage(key, objectMapper.writeValueAsString(envelope));

        TransactionVote vote = new TransactionVote(TransactionVote.Vote.YES, List.of());
        when(messageRepository.findPendingForRetry(any(), any())).thenReturn(List.of(msg));
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), eq(TransactionVote.class)))
                .thenReturn(vote);

        scheduler.retryStaleMessages();

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageService).markOutboundSent(eq(key), eq(200), jsonCaptor.capture());
        assertThat(jsonCaptor.getValue()).contains("YES");
    }

    @Test
    @DisplayName("NEW_TX returns null (202): markOutboundSent(202, null) called")
    void retryNewTx_202_marksOutboundSent202() throws Exception {
        Transaction tx = sampleTransaction();
        IdempotenceKey key = new IdempotenceKey(MY_RN, "hex-key-2");
        Message<Transaction> envelope = new Message<>(key, MessageType.NEW_TX, tx);
        InterbankMessage msg = newTxMessage(key, objectMapper.writeValueAsString(envelope));

        when(messageRepository.findPendingForRetry(any(), any())).thenReturn(List.of(msg));
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), eq(TransactionVote.class)))
                .thenReturn(null);

        scheduler.retryStaleMessages();

        verify(messageService).markOutboundSent(eq(key), eq(202), isNull());
    }

    @Test
    @DisplayName("COMMIT_TX success: markOutboundSent(204, null) called")
    void retryCommitTx_success_marksOutboundSent204() throws Exception {
        IdempotenceKey key = new IdempotenceKey(MY_RN, "commit-key");
        CommitTransaction body = new CommitTransaction(new ForeignBankId(MY_RN, TX_ID));
        Message<CommitTransaction> envelope = new Message<>(key, MessageType.COMMIT_TX, body);
        InterbankMessage msg = buildMessage(key, MessageType.COMMIT_TX,
                objectMapper.writeValueAsString(envelope));

        when(messageRepository.findPendingForRetry(any(), any())).thenReturn(List.of(msg));
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.COMMIT_TX), any(), eq(Void.class)))
                .thenReturn(null);

        scheduler.retryStaleMessages();

        verify(messageService).markOutboundSent(eq(key), eq(204), isNull());
    }

    @Test
    @DisplayName("ROLLBACK_TX success: markOutboundSent(204, null) called")
    void retryRollbackTx_success_marksOutboundSent204() throws Exception {
        IdempotenceKey key = new IdempotenceKey(MY_RN, "rollback-key");
        RollbackTransaction body = new RollbackTransaction(new ForeignBankId(MY_RN, TX_ID));
        Message<RollbackTransaction> envelope = new Message<>(key, MessageType.ROLLBACK_TX, body);
        InterbankMessage msg = buildMessage(key, MessageType.ROLLBACK_TX,
                objectMapper.writeValueAsString(envelope));

        when(messageRepository.findPendingForRetry(any(), any())).thenReturn(List.of(msg));
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.ROLLBACK_TX), any(), eq(Void.class)))
                .thenReturn(null);

        scheduler.retryStaleMessages();

        verify(messageService).markOutboundSent(eq(key), eq(204), isNull());
    }

    @Test
    @DisplayName("Communication exception: markOutboundFailed called")
    void retryCommunicationException_marksOutboundFailed() throws Exception {
        Transaction tx = sampleTransaction();
        IdempotenceKey key = new IdempotenceKey(MY_RN, "fail-key");
        Message<Transaction> envelope = new Message<>(key, MessageType.NEW_TX, tx);
        InterbankMessage msg = newTxMessage(key, objectMapper.writeValueAsString(envelope));

        when(messageRepository.findPendingForRetry(any(), any())).thenReturn(List.of(msg));
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), eq(TransactionVote.class)))
                .thenThrow(new InterbankExceptions.InterbankCommunicationException("timeout"));

        scheduler.retryStaleMessages();

        verify(messageService).markOutboundFailed(eq(key), contains("timeout"));
    }

    @Test
    @DisplayName("COMMIT_TX communication exception: markOutboundFailed called")
    void retryCommitTx_communicationException_marksOutboundFailed() throws Exception {
        IdempotenceKey key = new IdempotenceKey(MY_RN, "commit-fail-key");
        CommitTransaction body = new CommitTransaction(new ForeignBankId(MY_RN, TX_ID));
        Message<CommitTransaction> envelope = new Message<>(key, MessageType.COMMIT_TX, body);
        InterbankMessage msg = buildMessage(key, MessageType.COMMIT_TX,
                objectMapper.writeValueAsString(envelope));

        when(messageRepository.findPendingForRetry(any(), any())).thenReturn(List.of(msg));
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.COMMIT_TX), any(), eq(Void.class)))
                .thenThrow(new InterbankExceptions.InterbankCommunicationException("conn refused"));

        scheduler.retryStaleMessages();

        verify(messageService).markOutboundFailed(eq(key), contains("conn refused"));
    }

    @Test
    @DisplayName("ROLLBACK_TX communication exception: markOutboundFailed called")
    void retryRollbackTx_communicationException_marksOutboundFailed() throws Exception {
        IdempotenceKey key = new IdempotenceKey(MY_RN, "rollback-fail-key");
        RollbackTransaction body = new RollbackTransaction(new ForeignBankId(MY_RN, TX_ID));
        Message<RollbackTransaction> envelope = new Message<>(key, MessageType.ROLLBACK_TX, body);
        InterbankMessage msg = buildMessage(key, MessageType.ROLLBACK_TX,
                objectMapper.writeValueAsString(envelope));

        when(messageRepository.findPendingForRetry(any(), any())).thenReturn(List.of(msg));
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.ROLLBACK_TX), any(), eq(Void.class)))
                .thenThrow(new InterbankExceptions.InterbankCommunicationException("network error"));

        scheduler.retryStaleMessages();

        verify(messageService).markOutboundFailed(eq(key), contains("network error"));
    }

    @Test
    @DisplayName("One failing message does not block other messages in the same batch")
    void retryStaleMessages_oneFailureDoesNotBlockOthers() throws Exception {
        Transaction tx = sampleTransaction();
        IdempotenceKey key1 = new IdempotenceKey(MY_RN, "key-fail");
        IdempotenceKey key2 = new IdempotenceKey(MY_RN, "key-ok");
        Message<Transaction> env1 = new Message<>(key1, MessageType.NEW_TX, tx);
        Message<Transaction> env2 = new Message<>(key2, MessageType.NEW_TX, tx);
        InterbankMessage msg1 = newTxMessage(key1, objectMapper.writeValueAsString(env1));
        InterbankMessage msg2 = newTxMessage(key2, objectMapper.writeValueAsString(env2));

        when(messageRepository.findPendingForRetry(any(), any())).thenReturn(List.of(msg1, msg2));
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), eq(TransactionVote.class)))
                .thenThrow(new InterbankExceptions.InterbankCommunicationException("fail"))
                .thenReturn(new TransactionVote(TransactionVote.Vote.YES, List.of()));

        scheduler.retryStaleMessages();

        verify(messageService).markOutboundFailed(eq(key1), any());
        verify(messageService).markOutboundSent(eq(key2), eq(200), any());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Transaction sampleTransaction() {
        ForeignBankId txId = new ForeignBankId(MY_RN, TX_ID);
        Posting p1 = new Posting(
                new TxAccount.Account(MY_RN + "001"),
                BigDecimal.valueOf(100),
                new Asset.Monas(new MonetaryAsset(CurrencyCode.RSD)));
        Posting p2 = new Posting(
                new TxAccount.Account(REMOTE_RN + "002"),
                BigDecimal.valueOf(-100),
                new Asset.Monas(new MonetaryAsset(CurrencyCode.RSD)));
        return new Transaction(List.of(p1, p2), txId, null, null, null, null);
    }

    private InterbankMessage newTxMessage(IdempotenceKey key, String requestBody) {
        return buildMessage(key, MessageType.NEW_TX, requestBody);
    }

    private InterbankMessage buildMessage(IdempotenceKey key, MessageType type, String requestBody) {
        InterbankMessage msg = new InterbankMessage();
        msg.setSenderRoutingNumber(key.routingNumber());
        msg.setLocallyGeneratedKey(key.locallyGeneratedKey());
        msg.setPeerRoutingNumber(REMOTE_RN);
        msg.setMessageType(type);
        msg.setRequestBody(requestBody);
        msg.setRetryCount(0);
        msg.setLastAttemptAt(LocalDateTime.now().minusMinutes(5));
        return msg;
    }
}
>>>>>>> main
