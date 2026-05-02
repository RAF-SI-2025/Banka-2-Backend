package rs.raf.banka2_bek.interbank.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
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
    private final ObjectMapper objectMapper;

    public InterbankRetryScheduler(InterbankMessageRepository messageRepository,
                                   InterbankMessageService messageService,
                                   InterbankClient interbankClient) {
        this.messageRepository = messageRepository;
        this.messageService = messageService;
        this.interbankClient = interbankClient;
    }

    @Scheduled(fixedRate = 120_000)
    public void retryStaleMessages() {
        // TODO:
        //  1. messageRepo.findPendingForRetry(now - intervalSeconds)
        //  2. za svaku:
        //     - if retryCount >= maxRetries: markOutboundFailed → STUCK
        //     - else: client.sendMessage(...) + recordovati ishod
        //  3. Atomicno per-poruka (osim send-a) — ne blokiraj druge poruke
    }
}
