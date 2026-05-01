package rs.raf.banka2_bek.assistant.agentic.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import rs.raf.banka2_bek.assistant.agentic.service.AgentActionGateway.AgenticDisabledException;
import rs.raf.banka2_bek.assistant.agentic.service.AgentActionGateway.AgenticRateLimitedException;

import java.util.Map;

/**
 * Phase 5 polish — specific exception mapper-i za agentic flow.
 *
 * Pre ovog handler-a, AgenticDisabledException + AgenticRateLimitedException
 * su pucane kao generic RuntimeException kroz {@code GlobalExceptionHandler}
 * sa HTTP 400. Ovo je netacno semanticki — disabled je 403 (zabranjeno),
 * rate limited je 429 (Too Many Requests).
 *
 * @ControllerAdvice se ucitava PRE @RestControllerAdvice GlobalExceptionHandler-a
 * po Spring AOP order-u, pa specifican handler dobija prioritet.
 */
@ControllerAdvice
public class AgentActionExceptionHandler {

    @ExceptionHandler(AgenticDisabledException.class)
    public ResponseEntity<Map<String, Object>> handleDisabled(AgenticDisabledException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", "AGENTIC_DISABLED",
                        "message", ex.getMessage() == null ? "Agentic mode nije aktivan" : ex.getMessage()
                ));
    }

    @ExceptionHandler(AgenticRateLimitedException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimited(AgenticRateLimitedException ex) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .body(Map.of(
                        "error", "AGENTIC_RATE_LIMITED",
                        "message", ex.getMessage() == null ? "Previse agentic akcija. Sacekaj minut." : ex.getMessage()
                ));
    }
}
