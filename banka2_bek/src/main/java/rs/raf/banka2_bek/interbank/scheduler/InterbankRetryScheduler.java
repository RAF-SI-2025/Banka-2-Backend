package rs.raf.banka2_bek.interbank.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions.InterbankAuthException;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions.InterbankCommunicationException;
import rs.raf.banka2_bek.interbank.model.InterbankMessage;
import rs.raf.banka2_bek.interbank.model.InterbankMessageStatus;
import rs.raf.banka2_bek.interbank.protocol.IdempotenceKey;
import rs.raf.banka2_bek.interbank.protocol.Message;
import rs.raf.banka2_bek.interbank.protocol.MessageType;
import rs.raf.banka2_bek.interbank.protocol.TransactionVote;
import rs.raf.banka2_bek.interbank.repository.InterbankMessageRepository;
import rs.raf.banka2_bek.interbank.service.InterbankClient;
import rs.raf.banka2_bek.interbank.service.InterbankMessageService;

import java.time.LocalDateTime;
import java.util.List;

/*
================================================================================
 TODO — RETRY PETLJA ZA NEPOTVRDJENE PORUKE (PROTOKOL §2.9)
 Zaduzen: BE tim
 Spec ref: protokol §2.9 Message exchange — "Svaka poruka mora biti retry-ovana
           dok se ne prizna" (at-most-once preko idempotence kljuceva)
--------------------------------------------------------------------------------
 FLOW:
  Svaka 2 minuta proveravamo InterbankMessage gde je status=PENDING i
  poslednji pokusaj stariji od interval praga. Za svaku poruku:
   1. Ako retryCount >= maxRetries:
      - status=STUCK, log ERROR (supervizor treba intervenciju)
      - Lokalna transakcija ostaje u PREPARED (rezervisana sredstva); manualna
        akcija ili supervisor MARK STUCK -> ROLLBACK lokalno
   2. Inace:
      - InterbankClient.sendMessage(routingNumber, type, envelope, responseType)
      - 200/204 -> markOutboundSent (status=SENT)
      - 202     -> ostani PENDING (legitimno cekanje)
      - 4xx/5xx/network -> markOutboundFailed (retryCount++)
      - 401     -> auth issue, skip retry, log ERROR

 IDEMPOTENCY (§2.2):
  Idempotence key se ZADRZAVA pri retry-u. Druga banka pri ponovnom
  prijemu vraca isti odgovor (cache hit u InterbankMessageService).

 KONFIGURACIJA:
   interbank.retry.interval-seconds=30
   interbank.retry.max-retries=10
   interbank.retry.stuck-timeout-minutes=30

 TESTOVI:
  - Retry se ne dešava pre interval praga
  - Max retries -> STUCK + log
  - 202 ne uvecava retryCount, ostaje PENDING
  - Uspesan retry oslobadja iz pending-a
  - Idempotency: ponovljeni response je cache-iran kod druge banke
================================================================================
*/
@Slf4j
@Component
public class InterbankRetryScheduler {

    private final InterbankMessageRepository messageRepository;
    private final InterbankMessageService messageService;
    private final InterbankClient interbankClient;

    @Value("${interbank.retry.max-retries:10}")
    private int maxRetries;

    @Value("${interbank.retry.interval-seconds:30}")
    private int retryIntervalSeconds;

    @Value("${interbank.retry.stuck-timeout-minutes:30}")
    private int stuckTimeoutMinutes;

    public InterbankRetryScheduler(InterbankMessageRepository messageRepository,
                                   InterbankMessageService messageService,
                                   InterbankClient interbankClient) {
        this.messageRepository = messageRepository;
        this.messageService = messageService;
        this.interbankClient = interbankClient;
    }

    @Scheduled(fixedRate = 120_000)
    public void retryPendingMessages() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(retryIntervalSeconds);

        List<InterbankMessage> pending = messageRepository.findPendingForRetry(
                InterbankMessageStatus.PENDING, cutoff);

        if (pending.isEmpty()) {
            log.debug("[RetryScheduler] Nema PENDING poruka za retry.");
            return;
        }

        log.info("[RetryScheduler] Pokrenuta retry rutina – {} PENDING poruka.", pending.size());

        for (InterbankMessage message : pending) {
            try {
                retrySingleMessage(message);
            } catch (Exception e) {
                log.error("[RetryScheduler] Neočekivana greška pri retry messageId={}: {}",
                        message.getId(), e.getMessage(), e);
            }
        }

        log.info("[RetryScheduler] Retry rutina završena.");
    }

    // =========================================================================
    // Interni helperi
    // =========================================================================

    private void retrySingleMessage(InterbankMessage message) {
        IdempotenceKey key = new IdempotenceKey(
                message.getSenderRoutingNumber(),
                message.getLocallyGeneratedKey()
        );

        // Stuck po timeout-u – bez obzira na retryCount
        if (isStuckByTimeout(message)) {
            markStuck(key, message, "Stuck timeout od " + stuckTimeoutMinutes + " minuta prekoračen");
            return;
        }

        // Stuck po broju pokušaja
        if (message.getRetryCount() >= maxRetries) {
            markStuck(key, message, "Dostignut maxRetries=" + maxRetries);
            return;
        }

        int targetRouting = message.getPeerRoutingNumber();
        MessageType type = message.getMessageType();

        log.info("[RetryScheduler] Retry messageId={} type={} routing={} pokušaj #{}",
                message.getId(), type, targetRouting, message.getRetryCount() + 1);

        // Rekonstruišemo envelope sa ISTIM idempotency ključem (§2.2)
        // Partner cache-ira odgovor po ovom ključu i vraća ga bez ponovnog pokretanja logike
        Message<String> envelope = new Message<>(key, type, message.getRequestBody());

        try {
            interbankClient.sendMessage(targetRouting, type, envelope, resolveResponseType(type));
            log.info("[RetryScheduler] Retry uspešan za messageId={}", message.getId());

        } catch (InterbankAuthException e) {
            // 401 – InterbankClient je već pozvao markOutboundFailed
            log.error("[RetryScheduler] AUTH GREŠKA messageId={} routing={} – " +
                    "skip retry, potrebna rotacija tokena!", message.getId(), targetRouting);

        } catch (InterbankCommunicationException e) {
            // 4xx/5xx/network – InterbankClient je već pozvao markOutboundFailed (retryCount++)
            log.warn("[RetryScheduler] Komunikacijska greška messageId={}: {}",
                    message.getId(), e.getMessage());
        }
    }

    private boolean isStuckByTimeout(InterbankMessage message) {
        if (message.getCreatedAt() == null) return false;
        return LocalDateTime.now().isAfter(message.getCreatedAt().plusMinutes(stuckTimeoutMinutes));
    }

    private void markStuck(IdempotenceKey key, InterbankMessage message, String razlog) {
        messageService.markOutboundFailed(key, razlog);
        log.error("[RetryScheduler] STUCK messageId={} type={} routing={} razlog='{}'. " +
                        "Lokalna transakcija ostaje PREPARED – supervisor mora intervencijom!",
                message.getId(), message.getMessageType(),
                message.getPeerRoutingNumber(), razlog);
    }

    @SuppressWarnings("unchecked")
    private <Resp> Class<Resp> resolveResponseType(MessageType type) {
        return switch (type) {
            case NEW_TX -> (Class<Resp>) TransactionVote.class;
            case COMMIT_TX, ROLLBACK_TX -> (Class<Resp>) Void.class;
        };
    }
}