package rs.raf.banka2_bek.assistant.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;
import rs.raf.banka2_bek.assistant.repository.AssistantConversationRepository;

import java.time.LocalDateTime;

/**
 * Soft-delete za Arbitro konverzacije starije od {@code assistant.conversation-retention-days}.
 *
 * Cron: 03:30 svaki dan (offset od OrderCleanupScheduler 01:00 i FundValueSnapshotScheduler 23:45).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantConversationCleanupScheduler {

    private final AssistantConversationRepository repository;
    private final AssistantProperties properties;

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanup() {
        int retentionDays = properties.getConversationRetentionDays();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int updated = repository.softDeleteOlderThan(LocalDateTime.now(), cutoff);
        if (updated > 0) {
            log.info("ARBITRO_CLEANUP soft-deleted {} conversations older than {} days",
                    updated, retentionDays);
        }
    }
}
