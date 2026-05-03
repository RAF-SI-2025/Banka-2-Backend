package rs.raf.banka2_bek.interbank.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.protocol.*;
import rs.raf.banka2_bek.interbank.service.InterbankMessageService;
import rs.raf.banka2_bek.interbank.service.TransactionExecutorService;
import java.util.Optional;


/*
================================================================================
 TODO — INBOUND ENDPOINT ZA PORUKE OD DRUGIH BANAKA (PROTOKOL §2.11)
 Zaduzen: BE tim
 Spec ref: protokol §2.11 Sending messages, §2.10 Authentication,
           §2.12 Message types
--------------------------------------------------------------------------------
 JEDINSTVENA TACKA ZA SVE INBOUND PORUKE (po protokolu):
   POST /interbank
   Content-Type: application/json
   X-Api-Key: <token koji smo MI izdali toj banci>

 STATUSI ODGOVORA (§2.11):
   202 Accepted — primljeno ali jos neobradeno; pošiljač retry-uje
   200 OK       — primljeno + zavrseno; body = response (npr. TransactionVote
                  za NEW_TX)
   204 No Content — primljeno + zavrseno bez tela
   401 — los X-Api-Key
   400 — malformed envelope
   500 — internal errors

 AUTHENTICATION (§2.10):
   - Procitaj X-Api-Key header
   - Provera u BankRoutingService da postoji partner sa tim inboundToken-om
   - Dodatno: envelope.idempotenceKey.routingNumber MORA biti routingNumber
     tog partnera (sprecava CSRF iz druge banke)

 IDEMPOTENCY (§2.2):
   - InterbankMessageService.findCachedResponse(envelope.idempotenceKey)
   - Ako postoji: vrati cached response sa istim httpStatus-om (200/204)
   - Ako ne: izvrsi handler, pa pozovi recordInboundResponse atomicno

 DISPATCH PO TIPU (§2.12):
   NEW_TX      -> TransactionExecutorService.handleNewTx → vrati TransactionVote (200)
   COMMIT_TX   -> TransactionExecutorService.handleCommitTx → 204
   ROLLBACK_TX -> TransactionExecutorService.handleRollbackTx → 204

 NAPOMENA:
   Endpoint-ovi za public-stock, /negotiations/* i /user/* idu u
   OtcNegotiationController (§3.1-3.7), NE ovde. Ovaj kontroler je samo
   za §2 Transaction execution protocol.
================================================================================
*/
@RestController
@RequestMapping("/interbank")
@RequiredArgsConstructor
public class InterbankInboundController {


    private final InterbankProperties properties;
    private final ObjectMapper objectMapper;
    private final InterbankMessageService interbankMessageService;
    private final TransactionExecutorService transactionExecutorService;

    /**
     * Glavni endpoint po §2.11. Body je Message<Type> envelope sa
     * idempotenceKey + messageType + message (Transaction / CommitTransaction
     * / RollbackTransaction).
     * <p>
     * Vraca:
     * - 200 OK + TransactionVote za NEW_TX
     * - 204 No Content za COMMIT_TX, ROLLBACK_TX
     * - 202 Accepted ako poruka nije jos obradena (npr. backoff)
     */
    @PostMapping
    public ResponseEntity<Object> receiveMessage(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestBody String rawBody) {

        try {
            if (apiKey == null || apiKey.isBlank()) {
                return ResponseEntity.status(401).build();
            }

            Optional<InterbankProperties.PartnerBank> partnerBankOpt = properties.getPartners()
                    .stream()
                    .filter(partner -> apiKey.equals(partner.getInboundToken()))
                    .findFirst();

            if (partnerBankOpt.isEmpty()) {
                return ResponseEntity.status(401).build();
            }

            InterbankProperties.PartnerBank partnerBank = partnerBankOpt.get();

            JsonNode envelope = objectMapper.readTree(rawBody);

            if (!envelope.hasNonNull("idempotenceKey")
                    || !envelope.hasNonNull("messageType")
                    || !envelope.hasNonNull("message")) {
                return ResponseEntity.badRequest().build();
            }

            IdempotenceKey idempotenceKey =
                    objectMapper.convertValue(envelope.get("idempotenceKey"), IdempotenceKey.class);
            MessageType messageType =
                    objectMapper.convertValue(envelope.get("messageType"), MessageType.class);
            JsonNode messageNode = envelope.get("message");

            if (idempotenceKey.routingNumber() != partnerBank.getRoutingNumber()) {
                return ResponseEntity.status(401).build();
            }

            Optional<String> cachedResponseOpt = interbankMessageService.findCachedResponse(idempotenceKey);

            if (cachedResponseOpt.isPresent()) {
                String cachedResponse = cachedResponseOpt.get();

                if (cachedResponse == null || cachedResponse.isBlank()) {
                    return ResponseEntity.noContent().build();
                }

                return ResponseEntity.ok(objectMapper.readTree(cachedResponse));
            }

            return dispatchByMessageType(messageType, idempotenceKey, messageNode);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    private ResponseEntity<Object> dispatchByMessageType(
            MessageType messageType,
            IdempotenceKey idempotenceKey,
            JsonNode message
    ) {
        return switch (messageType) {
            case NEW_TX -> {
                Transaction tx = objectMapper.convertValue(message, Transaction.class);
                TransactionVote vote = transactionExecutorService.handleNewTx(tx, idempotenceKey);
                yield ResponseEntity.ok(vote);
            }
            case COMMIT_TX -> {
                CommitTransaction body = objectMapper.convertValue(message, CommitTransaction.class);
                transactionExecutorService.handleCommitTx(body, idempotenceKey);
                yield ResponseEntity.noContent().build();
            }
            case ROLLBACK_TX -> {
                RollbackTransaction body = objectMapper.convertValue(message, RollbackTransaction.class);
                transactionExecutorService.handleRollbackTx(body, idempotenceKey);
                yield ResponseEntity.noContent().build();
            }


        };
    }
}
