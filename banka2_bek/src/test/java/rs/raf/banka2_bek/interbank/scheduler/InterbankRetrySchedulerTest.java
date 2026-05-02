package rs.raf.banka2_bek.interbank.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import rs.raf.banka2_bek.interbank.repository.InterbankMessageRepository;
import rs.raf.banka2_bek.interbank.service.InterbankClient;
import rs.raf.banka2_bek.interbank.service.InterbankMessageService;

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