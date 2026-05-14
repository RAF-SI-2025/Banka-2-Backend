package rs.raf.banka2_bek.interbank.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.model.InterbankMessage;
import rs.raf.banka2_bek.interbank.model.InterbankMessageStatus;
import rs.raf.banka2_bek.interbank.protocol.*;
import rs.raf.banka2_bek.interbank.repository.InterbankMessageRepository;
import rs.raf.banka2_bek.interbank.service.InterbankClient;
import rs.raf.banka2_bek.interbank.service.InterbankMessageService;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InterbankRetryScheduler {

    private final InterbankMessageRepository messageRepository;
    private final InterbankClient client;
    private final InterbankMessageService messageService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedRate = 120_000)
    public void retryStaleMessages() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(120);
        List<InterbankMessage> pending =
                messageRepository.findPendingForRetry(InterbankMessageStatus.PENDING, cutoff);

        for (InterbankMessage msg : pending) {
            try {
                retryOne(msg);
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException oneTried) {
                // Drugi scheduler instance je vec uzeo ovu poruku — preskoci tiho,
                // sledeci ciklus ce videti sveze stanje.
                log.debug("Retry skipped (concurrent worker took msg id={}): {}",
                        msg.getId(), oneTried.getMessage());
            } catch (Exception e) {
                log.error("Retry error msg id={} type={}: {}",
                        msg.getId(), msg.getMessageType(), e.getMessage());
            }
        }
    }

    private void retryOne(InterbankMessage msg) throws Exception {
        IdempotenceKey key = new IdempotenceKey(
                msg.getSenderRoutingNumber(), msg.getLocallyGeneratedKey());
        int targetRn = msg.getPeerRoutingNumber();

        switch (msg.getMessageType()) {
            case NEW_TX -> {
                Message<Transaction> env = objectMapper.readValue(
                        msg.getRequestBody(), new TypeReference<Message<Transaction>>() {});
                try {
                    TransactionVote vote = client.sendMessage(
                            targetRn, MessageType.NEW_TX, env, TransactionVote.class);
                    if (vote != null) {
                        messageService.markOutboundSent(key, 200,
                                objectMapper.writeValueAsString(vote));
                    } else {
                        messageService.markOutboundSent(key, 202, null);
                    }
                } catch (InterbankExceptions.InterbankCommunicationException |
                         InterbankExceptions.InterbankAuthException e) {
                    messageService.markOutboundFailed(key, e.getMessage());
                }
            }
            case COMMIT_TX -> {
                Message<CommitTransaction> env = objectMapper.readValue(
                        msg.getRequestBody(), new TypeReference<Message<CommitTransaction>>() {});
                try {
                    client.sendMessage(targetRn, MessageType.COMMIT_TX, env, Void.class);
                    messageService.markOutboundSent(key, 204, null);
                } catch (InterbankExceptions.InterbankCommunicationException |
                         InterbankExceptions.InterbankAuthException e) {
                    messageService.markOutboundFailed(key, e.getMessage());
                }
            }
            case ROLLBACK_TX -> {
                Message<RollbackTransaction> env = objectMapper.readValue(
                        msg.getRequestBody(), new TypeReference<Message<RollbackTransaction>>() {});
                try {
                    client.sendMessage(targetRn, MessageType.ROLLBACK_TX, env, Void.class);
                    messageService.markOutboundSent(key, 204, null);
                } catch (InterbankExceptions.InterbankCommunicationException |
                         InterbankExceptions.InterbankAuthException e) {
                    messageService.markOutboundFailed(key, e.getMessage());
                }
            }
        }
    }
}
