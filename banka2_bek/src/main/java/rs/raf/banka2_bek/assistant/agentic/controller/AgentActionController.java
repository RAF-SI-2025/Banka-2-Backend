package rs.raf.banka2_bek.assistant.agentic.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.assistant.agentic.dto.ConfirmActionDto;
import rs.raf.banka2_bek.assistant.agentic.model.AgentAction;
import rs.raf.banka2_bek.assistant.agentic.service.AgentActionGateway;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserResolver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Phase 4 endpoint — confirm/reject pending agentic akcija.
 *
 *  GET    /assistant/actions/{actionUuid}        — preview detalji
 *  POST   /assistant/actions/{actionUuid}/confirm  — potvrdi (sa otp ako treba)
 *  POST   /assistant/actions/{actionUuid}/reject — odbaci
 *  GET    /assistant/actions                     — lista PENDING za usera
 *
 * Spec: LLM_Asistent_Plan.txt v3.5 §17.
 */
@RestController
@RequestMapping("/assistant/actions")
@RequiredArgsConstructor
@Slf4j
public class AgentActionController {

    private final AgentActionGateway gateway;
    private final UserResolver userResolver;
    private final List<WriteToolHandler> writeHandlers;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listPending() {
        List<AgentAction> pending = gateway.listPending(userResolver.resolveCurrent());
        return ResponseEntity.ok(pending.stream().map(this::toShortDto).toList());
    }

    /** Phase 5 polish — istorija resolved akcija (audit za usera). */
    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> listHistory() {
        List<AgentAction> history = gateway.listHistory(userResolver.resolveCurrent());
        return ResponseEntity.ok(history.stream().map(this::toShortDto).toList());
    }

    @GetMapping("/{actionUuid}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String actionUuid) {
        AgentAction action = gateway.getById(actionUuid, userResolver.resolveCurrent());
        return ResponseEntity.ok(toFullDto(action));
    }

    @PostMapping("/{actionUuid}/confirm")
    public ResponseEntity<Map<String, Object>> confirm(@PathVariable String actionUuid,
                                                        @RequestBody(required = false) ConfirmActionDto request) {
        Map<String, WriteToolHandler> byName = writeHandlers.stream()
                .collect(Collectors.toMap(WriteToolHandler::name, h -> h, (a, b) -> a));
        Map<String, Object> result = gateway.confirm(
                actionUuid, userResolver.resolveCurrent(), request, byName);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{actionUuid}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable String actionUuid) {
        gateway.reject(actionUuid, userResolver.resolveCurrent());
        return ResponseEntity.ok(Map.of("status", "REJECTED"));
    }

    private Map<String, Object> toShortDto(AgentAction a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("actionUuid", a.getActionUuid());
        m.put("toolName", a.getToolName());
        m.put("summary", a.getSummary());
        m.put("status", a.getStatus().name());
        m.put("requiresOtp", a.isRequiresOtp());
        m.put("createdAt", a.getCreatedAt());
        m.put("expiresAt", a.getExpiresAt());
        return m;
    }

    private Map<String, Object> toFullDto(AgentAction a) {
        Map<String, Object> m = toShortDto(a);
        m.put("parametersJson", a.getParametersJson());
        m.put("warningsJson", a.getWarningsJson());
        if (a.getResolvedAt() != null) m.put("resolvedAt", a.getResolvedAt());
        if (a.getExecutionResultJson() != null) m.put("executionResultJson", a.getExecutionResultJson());
        if (a.getErrorMessage() != null) m.put("errorMessage", a.getErrorMessage());
        return m;
    }
}
