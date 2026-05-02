package rs.raf.banka2_bek.assistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.util.List;

/**
 * Strukturisani console audit log za Arbitro pozive.
 *
 * Za demo je dovoljno — Prometheus i strukturisana persistencija se odlazu za
 * Phase 3 (K8s deploy). Logger format je "ARBITRO_AUDIT" prefix da se moze
 * lako grepa-ti po log fajlu.
 */
@Component
@Slf4j
public class AuditLogger {

    public void logChat(UserContext user, String conversationUuid, String pageRoute,
                        int promptChars, int responseChars, int reasoningChars,
                        List<String> toolsUsed, long latencyMs, boolean success) {
        log.info("ARBITRO_AUDIT chat user={}:{} conv={} page={} promptChars={} respChars={} reasonChars={} tools={} latencyMs={} ok={}",
                user.userRole(), user.userId(), conversationUuid, pageRoute,
                promptChars, responseChars, reasoningChars, toolsUsed, latencyMs, success);
    }

    public void logRateLimit(UserContext user) {
        log.warn("ARBITRO_AUDIT rate_limit user={}:{}", user.userRole(), user.userId());
    }

    public void logToolCall(String conversationUuid, String toolName, boolean ok, long latencyMs) {
        log.info("ARBITRO_AUDIT tool conv={} tool={} ok={} latencyMs={}",
                conversationUuid, toolName, ok, latencyMs);
    }

    public void logError(UserContext user, String conversationUuid, String error) {
        log.error("ARBITRO_AUDIT error user={}:{} conv={} error={}",
                user.userRole(), user.userId(), conversationUuid, error);
    }

    /**
     * Log za Phase 4 agentic akcije — pratimo svaku tranziciju
     * (PENDING → EXECUTED/FAILED/REJECTED/EXPIRED) sa actionUuid kao
     * jedinstvenim audit ID-em.
     */
    public void logAgentAction(UserContext user, String actionUuid, String toolName,
                                String status, String error) {
        if ("FAILED".equals(status)) {
            log.warn("ARBITRO_AGENTIC user={}:{} action={} tool={} status={} error={}",
                    user.userRole(), user.userId(), actionUuid, toolName, status, error);
        } else {
            log.info("ARBITRO_AGENTIC user={}:{} action={} tool={} status={}",
                    user.userRole(), user.userId(), actionUuid, toolName, status);
        }
    }
}
