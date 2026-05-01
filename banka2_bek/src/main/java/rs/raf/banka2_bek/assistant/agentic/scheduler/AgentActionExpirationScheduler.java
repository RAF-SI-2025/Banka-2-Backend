package rs.raf.banka2_bek.assistant.agentic.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.agentic.service.AgentActionGateway;

/**
 * Cron scheduler — svake 1 minute markira PENDING akcije sa proslim TTL-om
 * kao EXPIRED. Sprecava da stale pending akcije ostanu zauvek u tabeli.
 *
 * Properties: assistant.agentic.expiration-schedule-cron (default svake minute).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentActionExpirationScheduler {

    private final AgentActionGateway gateway;

    @Scheduled(cron = "${assistant.agentic.expiration-schedule-cron:0 * * * * *}")
    public void expireStaleActions() {
        try {
            int count = gateway.expireStale();
            if (count > 0) {
                log.debug("Agentic scheduler expired {} stale pending actions", count);
            }
        } catch (Exception e) {
            log.error("Agentic expiration scheduler failed", e);
        }
    }
}
