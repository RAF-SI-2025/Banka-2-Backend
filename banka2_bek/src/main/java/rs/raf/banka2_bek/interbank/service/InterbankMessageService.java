package rs.raf.banka2_bek.interbank.service;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.model.InterbankMessage;
import rs.raf.banka2_bek.interbank.model.InterbankMessageDirection;
import rs.raf.banka2_bek.interbank.model.InterbankMessageStatus;
import rs.raf.banka2_bek.interbank.protocol.IdempotenceKey;
import rs.raf.banka2_bek.interbank.protocol.MessageType;
import rs.raf.banka2_bek.interbank.repository.InterbankMessageRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

/*
================================================================================
 TODO — IDEMPOTENCY + MESSAGE LOG (PROTOKOL §2.2, §2.9, §2.11)
 Zaduzen: BE tim
 Spec ref: protokol §2.2 Idempotence keys, §2.9 Message exchange,
           §2.11 Sending messages (response caching)
--------------------------------------------------------------------------------
 KRITICNO PRAVILO (§2.2):
   "Banka MORA pratiti idempotence kljuceve zauvijek."
   Pri primanju poruke sa key-em koji je vec videjen, mora vratiti ISTI
   odgovor kao pri prvom prijemu — atomicno sa lokalnom transakcijom.

 OBAVEZNE METODE:

 INBOUND (mi smo primalac):

 1. Optional<String> findCachedResponse(IdempotenceKey key);
    Pretraga po (key.routingNumber, key.locallyGeneratedKey).
    Ako postoji: vrati cached response body (kao JSON string).
    Idempotency check pre svake operacije.

 2. void recordInboundResponse(IdempotenceKey key, MessageType messageType,
                                String requestBody, Integer httpStatus,
                                String responseBody);
    Upisi i request i response u istom redu, ATOMICNO sa biznis logikom
    (npr. prepareLocal). Ako se transakcija rollback-uje, brise se i ovaj
    zapis — pri retry-u poruka se obradjuje cisto.

 OUTBOUND (mi saljemo):

 3. IdempotenceKey generateKey();
    Vrati novu IdempotenceKey{ourRoutingNumber, UUID().toString()}.

 4. InterbankMessage recordOutbound(IdempotenceKey key, int targetRouting,
                                     MessageType type, String body);
    U lokalnoj transakciji koja je formirala poruku — upise zapis u
    message log sa status=PENDING. RetryScheduler ce ga pokupiti.

 5. void markOutboundSent(IdempotenceKey key, Integer httpStatus,
                           String responseBody);
    Posle uspesnog slanja: status=SENT, sacuvaj http status + telo
    odgovora (za audit). Samo za 200/204; 202 ostavi PENDING (retry kasnije).

 6. void markOutboundFailed(IdempotenceKey key, String errorMessage);
    Network greska / 4xx / 5xx — status ostane PENDING ali increment
    retryCount + lastError. Posle MAX_RETRY -> STUCK.

 NAPOMENA O TRANSAKCIONALNOSTI:
  Prema §2.2: idempotency lookup + biznis logika moraju ici u JEDNOJ lokalnoj
  transakciji. Spring @Transactional(propagation = REQUIRED) na pozivajucem
  servisu (TransactionExecutorService.handleNewTx itd.) je dovoljno.

 INDEX U BAZI:
  - (sender_routing_number, locally_generated_key) UNIQUE INDEX — za fast
    duplicate detection
  - (status, last_attempt_at) INDEX — za retry scheduler

 LIMITS:
  - locally_generated_key max 64 bajta (po protokolu §2.2)
  - InterbankMessage.idempotenceKey kolona: VARCHAR(64) + routing_number INT
================================================================================
*/
@Service
@Slf4j
@RequiredArgsConstructor
public class InterbankMessageService {

    private static final int MAX_RETRIES = 5;

    private final InterbankMessageRepository repository;
    private final BankRoutingService bankRoutingService;


    public Optional<String> findCachedResponse(IdempotenceKey key) {

        Optional<InterbankMessage> messageOpt = repository.findBySenderRoutingNumberAndLocallyGeneratedKey(
                key.routingNumber(),
                key.locallyGeneratedKey()
        );
        return messageOpt.map(InterbankMessage::getResponseBody);
    }

    @Transactional
    public void recordInboundResponse(IdempotenceKey key,
                                       MessageType messageType,
                                       String requestBody,
                                       Integer httpStatus,
                                       String responseBody,
                                      String transactionId) {

        repository.save(
                InterbankMessage.builder()
                        .transactionId(transactionId)
                        .direction(InterbankMessageDirection.INBOUND)
                        .status(InterbankMessageStatus.INBOUND)
                        .senderRoutingNumber(key.routingNumber())
                        .locallyGeneratedKey(key.locallyGeneratedKey())
                        .messageType(messageType)
                        .requestBody(requestBody)
                        .responseBody(responseBody)
                        .httpStatus(httpStatus)
                        .peerRoutingNumber(key.routingNumber())
                        .createdAt(LocalDateTime.now())
                        .lastAttemptAt(LocalDateTime.now())
                        .retryCount(0).build()
        );

    }

    public IdempotenceKey generateKey() {

        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for(byte b : bytes) sb.append(String.format("%02x", b));
        return new IdempotenceKey(bankRoutingService.myRoutingNumber(), sb.toString());
    }

    /**
     * §2.11 — Logs an outbound message with status=PENDING so the retry scheduler can pick it up.
     * Must be called inside the same @Transactional as the business operation that triggered the send
     * (e.g. prepareLocal) so that the log entry and the reservation commit or rollback together.
     */
    @Transactional
    public InterbankMessage recordOutbound(IdempotenceKey key,
                                            int targetRouting,
                                            MessageType type,
                                            String body,
                                            String transactionId) {

        return repository.save(
                InterbankMessage.builder()
                    .direction(InterbankMessageDirection.OUTBOUND)
                    .status(InterbankMessageStatus.PENDING)
                    .senderRoutingNumber(key.routingNumber())
                    .locallyGeneratedKey(key.locallyGeneratedKey())
                    .messageType(type)
                    .requestBody(body)
                    .transactionId(transactionId)
                    .peerRoutingNumber(targetRouting)
                    .createdAt(LocalDateTime.now())
                    .lastAttemptAt(LocalDateTime.now())
                    .retryCount(0).build()
        );
    }

    @Transactional
    public void markOutboundSent(IdempotenceKey key, Integer httpStatus, String responseBody) {
        InterbankMessage ibMessage =
                repository.findBySenderRoutingNumberAndLocallyGeneratedKey(
                        key.routingNumber(), key.locallyGeneratedKey()
                ).orElseThrow(() ->
                        new InterbankExceptions.InterbankProtocolException(
                                "No outbound message for key " + key + " was found."
                        )
                );

        if (httpStatus.equals(HttpStatus.OK.value()) || httpStatus.equals(HttpStatus.NO_CONTENT.value())) {
            ibMessage.setStatus(InterbankMessageStatus.SENT);
            ibMessage.setHttpStatus(httpStatus);
            ibMessage.setResponseBody(responseBody);
            ibMessage.setLastAttemptAt(LocalDateTime.now());
        }
        else if (httpStatus.equals(HttpStatus.ACCEPTED.value())){
            ibMessage.setRetryCount(ibMessage.getRetryCount() + 1);
            ibMessage.setLastAttemptAt(LocalDateTime.now());
        }
        else {
            markOutboundFailed(key, "Outbound message sending failed.");
        }

    }

    @Transactional
    public void markOutboundFailed(IdempotenceKey key, String errorMessage) {
        InterbankMessage ibMessage =
                repository.findBySenderRoutingNumberAndLocallyGeneratedKey(
                        key.routingNumber(), key.locallyGeneratedKey()
                ).orElseThrow(() ->
                        new InterbankExceptions.InterbankProtocolException(
                                "No outbound message for the key " + key + " was found."
                        )
                );
        ibMessage.setRetryCount(ibMessage.getRetryCount() + 1);
        ibMessage.setLastError(errorMessage);
        ibMessage.setLastAttemptAt(LocalDateTime.now());

        if (ibMessage.getRetryCount() >= MAX_RETRIES) {
            ibMessage.setStatus(InterbankMessageStatus.STUCK);

            log.error("Interbank outbound message STUCK after {} for key={}, error message: {} ", MAX_RETRIES, key, errorMessage);

        }

        repository.save(ibMessage);

    }

}
