package rs.raf.banka2_bek.assistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import rs.raf.banka2_bek.assistant.agentic.dto.AgentActionPreviewDto;
import rs.raf.banka2_bek.assistant.wizard.dto.SlotPromptDto;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helper za Arbitro SSE event tipove. Plan v3.5 §7 + §17 (agentic mode).
 *
 * <pre>
 * thinking_start  {}
 * thinking_end    {}
 * tool_call       {name, args}
 * tool_result     {name, ok, summary}
 * token           {text}
 * source          {type, title, url?}
 * done            {messageId, totalTokens, latencyMs, reasoningChars}
 * error           {code, message}
 * action_preview  {actionUuid, tool, summary, parameters, warnings, requiresOtp, expiresAt}
 * action_executed {actionUuid, tool, status, result?, error?}
 * action_rejected {actionUuid}
 * </pre>
 */
@Slf4j
public final class SseEvents {

    private SseEvents() {}

    public static void thinkingStart(SseEmitter emitter) {
        send(emitter, "thinking_start", Map.of());
    }

    public static void thinkingEnd(SseEmitter emitter) {
        send(emitter, "thinking_end", Map.of());
    }

    public static void toolCall(SseEmitter emitter, String name, Object args) {
        send(emitter, "tool_call", Map.of("name", name, "args", args == null ? Map.of() : args));
    }

    public static void toolResult(SseEmitter emitter, String name, boolean ok, String summary) {
        send(emitter, "tool_result", Map.of("name", name, "ok", ok, "summary", summary == null ? "" : summary));
    }

    public static void token(SseEmitter emitter, String text) {
        send(emitter, "token", Map.of("text", text == null ? "" : text));
    }

    public static void source(SseEmitter emitter, String type, String title, String url) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("type", type);
        payload.put("title", title == null ? "" : title);
        if (url != null) payload.put("url", url);
        send(emitter, "source", payload);
    }

    public static void done(SseEmitter emitter, Long messageId, String conversationUuid,
                            int totalTokens, long latencyMs, int reasoningChars) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("messageId", messageId == null ? -1 : messageId);
        payload.put("conversationUuid", conversationUuid == null ? "" : conversationUuid);
        payload.put("totalTokens", totalTokens);
        payload.put("latencyMs", latencyMs);
        payload.put("reasoningChars", reasoningChars);
        send(emitter, "done", payload);
    }

    public static void error(SseEmitter emitter, String code, String message) {
        send(emitter, "error", Map.of("code", code, "message", message == null ? "" : message));
    }

    /* ========================== AGENTIC MODE (Phase 4 v3.5) ========================== */

    /**
     * Emit action_preview event — FE prikazuje confirmation modal.
     * Posle ovog event-a, AssistantService PAUZIRA tool-use loop dok se confirm
     * ili reject ne dogodi (preko zasebnog REST endpoint-a).
     */
    public static void actionPreview(SseEmitter emitter, AgentActionPreviewDto preview) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actionUuid", preview.getActionUuid());
        payload.put("tool", preview.getTool());
        payload.put("summary", preview.getSummary());
        payload.put("parameters", preview.getParameters() == null ? Map.of() : preview.getParameters());
        payload.put("warnings", preview.getWarnings() == null ? java.util.List.of() : preview.getWarnings());
        payload.put("requiresOtp", preview.isRequiresOtp());
        payload.put("expiresAt", preview.getExpiresAt() == null ? "" : preview.getExpiresAt().toString());
        // Phase 5 multi-step — opciona polja za chain progress indicator
        if (preview.getPlanStepIndex() != null) payload.put("planStepIndex", preview.getPlanStepIndex());
        if (preview.getPlanTotalSteps() != null) payload.put("planTotalSteps", preview.getPlanTotalSteps());
        if (preview.getParentActionUuid() != null) payload.put("parentActionUuid", preview.getParentActionUuid());
        send(emitter, "action_preview", payload);
    }

    /**
     * Emit action_executed event — posle uspesnog confirm-a, FE prikazuje
     * success notification i panel se vraca u idle state.
     */
    public static void actionExecuted(SseEmitter emitter, String actionUuid, String tool,
                                       String status, Object result, String error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actionUuid", actionUuid == null ? "" : actionUuid);
        payload.put("tool", tool == null ? "" : tool);
        payload.put("status", status == null ? "" : status);
        if (result != null) payload.put("result", result);
        if (error != null) payload.put("error", error);
        send(emitter, "action_executed", payload);
    }

    /**
     * Emit action_rejected event — posle user reject-a ili expire-a.
     */
    public static void actionRejected(SseEmitter emitter, String actionUuid, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actionUuid", actionUuid == null ? "" : actionUuid);
        payload.put("reason", reason == null ? "" : reason);
        send(emitter, "action_rejected", payload);
    }

    /* ========================== WIZARD (Phase 4.5) ========================== */

    /**
     * Emit agent_choice event — FE renderuje choice card sa pitanjem i opcijama.
     * AssistantService posle ovog event-a zatvara stream — sledeci korak ide
     * preko POST /assistant/wizard/{id}/select endpoint-a.
     */
    public static void agentChoice(SseEmitter emitter, SlotPromptDto prompt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("wizardId", prompt.wizardId().toString());
        payload.put("toolName", prompt.toolName());
        payload.put("title", prompt.title());
        payload.put("slotName", prompt.slotName());
        payload.put("prompt", prompt.prompt());
        payload.put("type", prompt.type());
        payload.put("options", prompt.options() == null ? java.util.List.of() : prompt.options());
        payload.put("stepIndex", prompt.stepIndex());
        payload.put("totalSteps", prompt.totalSteps());
        payload.put("previousSelections",
                prompt.previousSelections() == null ? java.util.List.of() : prompt.previousSelections());
        if (prompt.errorMessage() != null) payload.put("errorMessage", prompt.errorMessage());
        send(emitter, "agent_choice", payload);
    }

    private static void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException e) {
            // Klijent je verovatno disconnect-ovao
            log.debug("SSE send '{}' failed: {}", name, e.getMessage());
        }
    }
}
