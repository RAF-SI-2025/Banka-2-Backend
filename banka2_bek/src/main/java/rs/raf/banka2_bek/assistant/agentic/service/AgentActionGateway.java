package rs.raf.banka2_bek.assistant.agentic.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.assistant.agentic.dto.AgentActionPreviewDto;
import rs.raf.banka2_bek.assistant.agentic.dto.ConfirmActionDto;
import rs.raf.banka2_bek.assistant.agentic.model.AgentAction;
import rs.raf.banka2_bek.assistant.agentic.model.AgentActionStatus;
import rs.raf.banka2_bek.assistant.agentic.repository.AgentActionRepository;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;
import rs.raf.banka2_bek.assistant.service.AuditLogger;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Centralizovan agentic flow lifecycle:
 *  1. createPending() — kreira AgentAction + emit-uje preview DTO
 *  2. confirm() — validira, poziva handler.executeFinal(), markira EXECUTED/FAILED
 *  3. reject() — markira REJECTED
 *  4. expire() — bulk update PENDING > TTL → EXPIRED
 *
 * Idempotency: actionUuid je UNIQUE u bazi — duplo confirm na isti uuid
 * vraca cached executionResult (ako je vec EXECUTED) ili odbija (ako je
 * REJECTED/EXPIRED/FAILED).
 *
 * Rate limiting: per-user sliding window (paralelno sa chat rate limiterom),
 * stroziji jer su agentic akcije ekspenzivnije od read upita.
 *
 * Spec: LLM_Asistent_Plan.txt v3.5 §17.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentActionGateway {

    private final AgentActionRepository repository;
    private final AssistantProperties properties;
    private final AuditLogger auditLogger;
    // Phase 4.6 OTP fix: handlers (CreatePayment/CreateBuyOrder/...) call
    // *Service.* directly, bypassing the controller layer where OTP is
    // verified. Gateway must verify OTP itself before dispatching the
    // handler so requiresOtp=true tools actually consume + check the code.
    private final rs.raf.banka2_bek.otp.service.OtpService otpService;
    private final rs.raf.banka2_bek.auth.util.UserResolver userResolver;
    /**
     * Polje imenovano da matchuje bean name "assistantObjectMapper" iz
     * AssistantConfig — Spring resolve-uje by name kad ima vise istog
     * tipa. Lombok @RequiredArgsConstructor ne moze da prosledi @Qualifier
     * na konstruktor parametre, pa polje mora imati to ime.
     */
    private final ObjectMapper assistantObjectMapper;

    /** Per-user sliding window agentic rate limiter (van chat-ovog limit-a). */
    private final Map<String, java.util.Deque<Instant>> rateWindows = new ConcurrentHashMap<>();

    /* ========================== CREATE ========================== */

    /**
     * Pravi PENDING AgentAction iz LLM-ovog tool poziva.
     *
     * @param conversationUuid trenutni Arbitro conversation UUID (audit trail)
     * @param toolName ime write tool-a (npr. "create_payment")
     * @param args sirov LLM argumenti
     * @param user trenutni korisnik
     * @param handler write handler koji ce izvrsiti akciju
     * @return preview DTO koji se emit-uje u SSE
     * @throws AgenticRateLimitedException ako user prekoraci limit
     * @throws AgenticDisabledException ako agentic mode nije aktivan
     */
    @Transactional
    public AgentActionPreviewDto createPending(String conversationUuid,
                                                String toolName,
                                                Map<String, Object> args,
                                                UserContext user,
                                                WriteToolHandler handler) {
        return createPending(conversationUuid, toolName, args, user, handler, null, null, null);
    }

    /**
     * Phase 5 multi-step plan — overload sa parent UUID + step info.
     * LLM moze emit-ovati lanac akcija ("prvo prebaci EUR u USD, pa BUY AAPL")
     * — svaki step kreira AgentAction sa istim parentActionUuid + 1-based index.
     */
    @Transactional
    public AgentActionPreviewDto createPending(String conversationUuid,
                                                String toolName,
                                                Map<String, Object> args,
                                                UserContext user,
                                                WriteToolHandler handler,
                                                String parentActionUuid,
                                                Integer planStepIndex,
                                                Integer planTotalSteps) {
        if (!properties.getAgentic().isEnabled()) {
            throw new AgenticDisabledException("Agentic mode nije aktivan na BE-u");
        }
        if (!tryAcquireRate(user)) {
            throw new AgenticRateLimitedException(
                    "Previse agentic akcija. Sacekaj minut.");
        }

        // Buildovanje preview-a se desava PRE kreiranja AgentAction-a — ako handler
        // baci IllegalArgumentException, ne kvarimo state.
        WriteToolHandler.PreviewResult preview = handler.buildPreview(args, user);

        String paramsJson = jsonOrThrow(args);
        String warningsJson = preview.warnings().isEmpty() ? null : jsonOrThrow(preview.warnings());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(properties.getAgentic().getActionTtlMin());

        AgentAction action = AgentAction.builder()
                .actionUuid(UUID.randomUUID().toString())
                .conversationUuid(conversationUuid)
                .toolName(toolName)
                .parametersJson(paramsJson)
                .summary(preview.summary())
                .warningsJson(warningsJson)
                .requiresOtp(handler.requiresOtp())
                .userId(user.userId())
                .userRole(user.userRole())
                .createdAt(now)
                .expiresAt(expiresAt)
                .status(AgentActionStatus.PENDING)
                .parentActionUuid(parentActionUuid)
                .planStepIndex(planStepIndex)
                .planTotalSteps(planTotalSteps)
                .build();
        repository.save(action);

        auditLogger.logAgentAction(user, action.getActionUuid(), toolName, "PENDING", null);

        return AgentActionPreviewDto.builder()
                .actionUuid(action.getActionUuid())
                .tool(toolName)
                .summary(preview.summary())
                .parameters(preview.displayFields())
                .warnings(preview.warnings())
                .requiresOtp(handler.requiresOtp())
                .expiresAt(expiresAt)
                .planStepIndex(planStepIndex)
                .planTotalSteps(planTotalSteps)
                .parentActionUuid(parentActionUuid)
                .build();
    }

    /* ========================== CONFIRM ========================== */

    /**
     * Confirm path — proverava ownership, validira state, poziva handler.executeFinal.
     * @return Map sa {@code status} (EXECUTED/FAILED), {@code result}, {@code error}.
     */
    @Transactional
    public Map<String, Object> confirm(String actionUuid,
                                        UserContext user,
                                        ConfirmActionDto request,
                                        Map<String, WriteToolHandler> writeHandlers) {
        AgentAction action = loadAndValidate(actionUuid, user);

        // Idempotency: ako je vec EXECUTED, vrati cached rezultat. Ako je
        // REJECTED/EXPIRED/FAILED, odbij.
        if (action.getStatus() == AgentActionStatus.EXECUTED) {
            return Map.of("status", "EXECUTED",
                    "result", parseJsonOrEmpty(action.getExecutionResultJson()),
                    "cached", true);
        }
        if (action.getStatus() != AgentActionStatus.PENDING) {
            return Map.of("status", action.getStatus().name(),
                    "error", "Akcija vise nije u PENDING stanju (vec je " + action.getStatus() + ")");
        }
        if (action.isExpired()) {
            action.setStatus(AgentActionStatus.EXPIRED);
            action.setResolvedAt(LocalDateTime.now());
            repository.save(action);
            auditLogger.logAgentAction(user, actionUuid, action.getToolName(), "EXPIRED", null);
            return Map.of("status", "EXPIRED",
                    "error", "Akcija je istekla (TTL " + properties.getAgentic().getActionTtlMin() + "min)");
        }

        WriteToolHandler handler = writeHandlers.get(action.getToolName());
        if (handler == null) {
            action.setStatus(AgentActionStatus.FAILED);
            action.setErrorMessage("Tool handler '" + action.getToolName() + "' nije registrovan");
            action.setResolvedAt(LocalDateTime.now());
            repository.save(action);
            return Map.of("status", "FAILED", "error", action.getErrorMessage());
        }

        // Ako handler zahteva OTP, otpCode mora biti prisutan + verifikovan.
        //
        // Phase 4.6: write tool handlers (CreatePayment/CreateBuyOrder/...)
        // pozivaju ServiceImpl direktno, BYPASS-uju Controller layer gde
        // se OTP standardno verifikuje. Bez ovog guard-a, pogresan OTP code
        // bi prolazio jer service ignorise dto.otpCode polje. Gateway dakle
        // sam mora da pozove OtpService.verify pre dispatch-a.
        if (handler.requiresOtp()) {
            if (request == null || request.getOtpCode() == null || request.getOtpCode().isBlank()) {
                return Map.of("status", "FAILED",
                        "error", "Verifikacioni kod je obavezan za ovu akciju");
            }
            String email = currentUserEmail();
            if (email == null) {
                return Map.of("status", "FAILED",
                        "error", "Nije moguce identifikovati korisnika za OTP proveru");
            }
            Map<String, Object> verifyResult = otpService.verify(email, request.getOtpCode());
            if (!Boolean.TRUE.equals(verifyResult.get("verified"))) {
                String msg = (String) verifyResult.getOrDefault("message", "OTP neispravan");
                Map<String, Object> body = new java.util.HashMap<>(verifyResult);
                body.put("status", "FAILED");
                body.put("error", msg);
                return body;
            }
        }

        // Resolve finalne parametre — user moze biti edit-ovao u UI-u.
        Map<String, Object> finalArgs = resolveFinalArgs(action, request);
        try {
            String finalArgsJson = jsonOrThrow(finalArgs);
            action.setFinalParametersJson(finalArgsJson);
        } catch (Exception e) {
            log.warn("Failed to serialize final args, ignoring", e);
        }

        // Execute kroz handler — handler interno gadja postojeci ServiceImpl
        // koji ima svoj OTP+permission guard.
        Map<String, Object> result;
        try {
            String otp = handler.requiresOtp() && request != null ? request.getOtpCode() : null;
            result = handler.executeFinal(finalArgs, user, otp);
            action.setStatus(AgentActionStatus.EXECUTED);
            action.setExecutionResultJson(jsonOrEmpty(result));
        } catch (RuntimeException e) {
            log.warn("Agentic action {} failed: {}", actionUuid, e.getMessage());
            action.setStatus(AgentActionStatus.FAILED);
            action.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            result = Map.of("error", action.getErrorMessage());
        }
        action.setResolvedAt(LocalDateTime.now());
        repository.save(action);

        auditLogger.logAgentAction(user, actionUuid, action.getToolName(),
                action.getStatus().name(), action.getErrorMessage());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", action.getStatus().name());
        if (action.getStatus() == AgentActionStatus.EXECUTED) {
            response.put("result", result);
        } else {
            response.put("error", action.getErrorMessage());
        }
        return response;
    }

    /* ========================== REJECT ========================== */

    @Transactional
    public void reject(String actionUuid, UserContext user) {
        AgentAction action = loadAndValidate(actionUuid, user);
        if (action.getStatus() != AgentActionStatus.PENDING) {
            return; // idempotent, ignorisi non-PENDING
        }
        action.setStatus(AgentActionStatus.REJECTED);
        action.setResolvedAt(LocalDateTime.now());
        repository.save(action);
        auditLogger.logAgentAction(user, actionUuid, action.getToolName(), "REJECTED", null);
    }

    /* ========================== EXPIRE (scheduler) ========================== */

    @Transactional
    public int expireStale() {
        int count = repository.markExpired(
                AgentActionStatus.PENDING,
                AgentActionStatus.EXPIRED,
                LocalDateTime.now());
        if (count > 0) {
            log.info("ARBITRO_AGENTIC scheduler expired {} stale pending actions", count);
        }
        return count;
    }

    /* ========================== HELPERS ========================== */

    private AgentAction loadAndValidate(String actionUuid, UserContext user) {
        AgentAction action = repository.findByActionUuid(actionUuid)
                .orElseThrow(() -> new EntityNotFoundException("Akcija ne postoji: " + actionUuid));
        if (!action.getUserId().equals(user.userId())
                || !action.getUserRole().equals(user.userRole())) {
            throw new EntityNotFoundException("Akcija ne pripada trenutnom korisniku");
        }
        return action;
    }

    private Map<String, Object> resolveFinalArgs(AgentAction action, ConfirmActionDto request) {
        // Polazimo od originalnih LLM args
        Map<String, Object> base = parseJsonOrEmpty(action.getParametersJson());
        if (request != null && request.getEditedParameters() != null) {
            // FE moze da posalje samo izmenjena polja (partial update)
            Map<String, Object> merged = new HashMap<>(base);
            merged.putAll(request.getEditedParameters());
            return merged;
        }
        return base;
    }

    private String jsonOrThrow(Object value) {
        try {
            return assistantObjectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize: " + e.getMessage(), e);
        }
    }

    /**
     * Phase 4.6 OTP verify helper — vraca email iz Spring SecurityContext-a.
     * UserResolver nema email-by-id helper, a OtpService.verify trazi email
     * (isto kao PaymentController/OrderController do).
     */
    private String currentUserEmail() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null) return null;
        String name = auth.getName();
        if (name == null || name.isBlank() || "anonymousUser".equalsIgnoreCase(name)) return null;
        // Suppress unused warning — userResolver is wired for future use cases
        // where we may need to fetch employee/client email by user id.
        if (userResolver == null) return name;
        return name;
    }

    private String jsonOrEmpty(Object value) {
        try {
            return assistantObjectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonOrEmpty(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return assistantObjectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private boolean tryAcquireRate(UserContext user) {
        String key = user.userRole() + ":" + user.userId();
        Duration window = Duration.ofMinutes(1);
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);
        java.util.Deque<Instant> deque = rateWindows.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            int limit = properties.getAgentic().getRateLimitPerMin();
            if (deque.size() >= limit) return false;
            deque.addLast(now);
            return true;
        }
    }

    /* ========================== EXCEPTIONS ========================== */

    public static class AgenticRateLimitedException extends RuntimeException {
        public AgenticRateLimitedException(String msg) { super(msg); }
    }

    public static class AgenticDisabledException extends RuntimeException {
        public AgenticDisabledException(String msg) { super(msg); }
    }

    /* ========================== READ HELPERS ========================== */

    @Transactional(readOnly = true)
    public List<AgentAction> listPending(UserContext user) {
        return repository.findByUserIdAndUserRoleAndStatusOrderByCreatedAtDesc(
                user.userId(), user.userRole(), AgentActionStatus.PENDING);
    }

    /** Phase 5 polish — pun audit istorijat resolvanih akcija za usera. */
    @Transactional(readOnly = true)
    public List<AgentAction> listHistory(UserContext user) {
        return repository.findResolvedHistory(
                user.userId(), user.userRole(), AgentActionStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public AgentAction getById(String actionUuid, UserContext user) {
        return loadAndValidate(actionUuid, user);
    }
}
