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
