package rs.raf.banka2_bek.assistant.wizard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.assistant.agentic.dto.AgentActionPreviewDto;
import rs.raf.banka2_bek.assistant.agentic.service.AgentActionGateway;
import rs.raf.banka2_bek.assistant.tool.ToolRegistry;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.assistant.wizard.dto.SlotPromptDto;
import rs.raf.banka2_bek.assistant.wizard.dto.SlotSelectResponseDto;
import rs.raf.banka2_bek.assistant.wizard.model.SlotDefinition;
import rs.raf.banka2_bek.assistant.wizard.model.SlotOption;
import rs.raf.banka2_bek.assistant.wizard.model.SlotType;
import rs.raf.banka2_bek.assistant.wizard.model.WizardSession;
import rs.raf.banka2_bek.assistant.wizard.model.WizardTemplate;
import rs.raf.banka2_bek.assistant.wizard.registry.WizardRegistry;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Phase 4.5 — Multi-step interactive wizard service.
 *
 * <p>Lifecycle:</p>
 * <pre>
 *   AssistantService.runChat detects intent ("Plati Milici 100")
 *      → wizardService.start(toolName, user, conversationUuid, userMessage)
 *      → returns first SlotPromptDto (or null if no template found)
 *      → AssistantService emits agent_choice SSE event with the prompt
 *
 *   FE renders choice card, user selects
 *      → POST /assistant/wizard/{id}/select
 *      → wizardService.select(wizardId, slotName, value, user)
 *      → returns SlotSelectResponseDto:
 *           - AWAITING_NEXT_SLOT → next prompt
 *           - COMPLETED → action preview
 *           - INVALID → re-prompt
 * </pre>
 *
 * <p>Sessions live in-memory with 10-min TTL (cleaned by scheduler).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WizardService {

    private static final Duration TTL = Duration.ofMinutes(10);
    /** Special slot keys passed in the prefill context. */
    public static final String CTX_USER_MESSAGE = "__userMessage";

    private final WizardRegistry registry;
    private final AgentActionGateway agentActionGateway;
    private final ToolRegistry toolRegistry;

    private final ConcurrentMap<UUID, WizardSession> sessions = new ConcurrentHashMap<>();

    /* ============================== START ============================== */

    /**
     * Starts a new wizard session and returns the first slot prompt.
     *
     * @param toolName            target tool (must have a template in WizardRegistry)
     * @param user                acting user (must be in template.allowedRoles)
     * @param conversationUuid    chat conversation context
     * @param userMessage         original natural-language message — used for prefill heuristics
     *                            (e.g. extracting amount/recipient from "Plati Milici 100")
     * @return first slot prompt, or empty if no template / role denied / nothing to ask
     */
    public Optional<SlotPromptDto> start(String toolName, UserContext user,
                                          UUID conversationUuid, String userMessage) {
        WizardTemplate tpl = registry.get(toolName).orElse(null);
        if (tpl == null) {
            log.debug("WizardService: no template for {}", toolName);
            return Optional.empty();
        }
        if (!isRoleAllowed(tpl, user)) {
            log.debug("WizardService: role {} not allowed for {}", user.userRole(), toolName);
            return Optional.empty();
        }
        WizardSession session = new WizardSession(user.userId(), user.userRole(),
                conversationUuid, tpl);
        // Seed prefill context with original user message for slot resolvers
        if (userMessage != null) {
            session.recordSlot(CTX_USER_MESSAGE, userMessage);
        }
        sessions.put(session.getWizardId(), session);
        return advance(session, null);
    }

    /* ============================== SELECT (advance) ============================== */

    /**
     * Apply user's selection and advance the wizard.
     *
     * @return AWAITING_NEXT_SLOT with next prompt, COMPLETED with preview, INVALID
     *         with reprompt+reason, or EXPIRED.
     */
    public SlotSelectResponseDto select(UUID wizardId, String slotName, String value,
                                         UserContext user) {
        WizardSession session = sessions.get(wizardId);
        if (session == null) return SlotSelectResponseDto.expired();
        if (!session.ownedBy(user.userId(), user.userRole())) {
            log.warn("Wizard {} ownership mismatch — denied", wizardId);
            return SlotSelectResponseDto.expired();
        }
        if (Duration.between(session.getLastTouched(), Instant.now()).compareTo(TTL) > 0) {
            sessions.remove(wizardId);
            return SlotSelectResponseDto.expired();
        }

        SlotDefinition slot = session.currentSlot();
        if (slot == null) {
            return SlotSelectResponseDto.expired();
        }
        if (!slot.name().equals(slotName)) {
            log.warn("Wizard {} slot mismatch: expected={}, got={}",
                    wizardId, slot.name(), slotName);
            return SlotSelectResponseDto.invalid(buildPrompt(session, null, "Pogresan slot."),
                    "Pogresan slot");
        }

        // Transform raw value → typed object + capture human-readable label
        Object typed;
        String displayLabel = null;
        try {
            if (slot.type() == SlotType.CHOICE) {
                // For CHOICE, validate that value is one of the offered options
                List<SlotOption> opts = slot.optionsResolver().apply(user, session.getFilledSlots());
                SlotOption matched = opts.stream()
                        .filter(o -> o.value().equals(value))
                        .findFirst().orElse(null);
                if (matched == null) {
                    return SlotSelectResponseDto.invalid(
                            buildPrompt(session, null, "Izabrana opcija nije validna. Pokusajte ponovo."),
                            "Invalid choice");
                }
                typed = value;
                displayLabel = matched.label();
            } else if (slot.type() == SlotType.CONFIRM) {
                typed = value;
                displayLabel = "YES".equalsIgnoreCase(value) ? "Da" : "Ne";
            } else {
                typed = slot.transformer().apply(value);
                displayLabel = value;
            }
        } catch (Exception e) {
            return SlotSelectResponseDto.invalid(
                    buildPrompt(session, null, "Neispravan unos: " + e.getMessage()),
                    e.getMessage());
        }

        String validationError = slot.validator().apply(typed);
        if (validationError != null) {
            return SlotSelectResponseDto.invalid(
                    buildPrompt(session, null, validationError),
                    validationError);
        }

        session.recordSlotWithLabel(slot.name(), typed, displayLabel);
        session.advance();

        // If complete → dispatch agent action
        if (session.isComplete()) {
            return dispatchAndComplete(session, user);
        }

        // Otherwise → next prompt (skipping any prefilled slots)
        Optional<SlotPromptDto> next = advance(session, null);
        if (next.isEmpty()) {
            return dispatchAndComplete(session, user);
        }
        return SlotSelectResponseDto.awaitingNext(next.get());
    }

    /**
     * Recurses through prefill-able slots until a slot needs user interaction
     * or wizard is complete.
     */
    private Optional<SlotPromptDto> advance(WizardSession session, String error) {
        while (!session.isComplete()) {
            SlotDefinition slot = session.currentSlot();
            // Try prefill
            try {
                Optional<Object> prefilled = slot.prefill().apply(
                        contextUser(session), session.getFilledSlots());
                if (prefilled.isPresent()) {
                    Object v = prefilled.get();
                    // Empty-string sentinel = skip slot entirely (don't store)
                    if (v == null || (v instanceof String s && s.isEmpty())) {
                        session.advance();
                        continue;
                    }
                    session.recordSlot(slot.name(), v);
                    session.advance();
                    continue;
                }
            } catch (Exception e) {
                log.warn("Prefill failed for slot {}: {}", slot.name(), e.getMessage());
            }
            // Need user input — emit prompt
            return Optional.of(buildPrompt(session, slot, error));
        }
        return Optional.empty();
    }

    private SlotPromptDto buildPrompt(WizardSession session, SlotDefinition slotOverride, String error) {
        SlotDefinition slot = slotOverride != null ? slotOverride : session.currentSlot();
        WizardTemplate tpl = session.getTemplate();
        UserContext user = contextUser(session);

        List<SlotOption> options;
        if (slot.type() == SlotType.CHOICE) {
            try {
                options = slot.optionsResolver().apply(user, session.getFilledSlots());
                if (options == null) options = Collections.emptyList();
            } catch (Exception e) {
                log.warn("Options resolver failed for slot {}: {}", slot.name(), e.getMessage());
                options = Collections.emptyList();
            }
        } else if (slot.type() == SlotType.CONFIRM) {
            options = List.of(
                    new SlotOption("YES", "Da", null),
                    new SlotOption("NO", "Ne", null)
            );
        } else {
            options = Collections.emptyList();
        }

        // Build previous-selection summary — koristi sacuvan human-readable
        // label umesto raw value (npr. "Tekuci RSD — 222...8912" umesto "1").
        List<SlotPromptDto.PreviousSelection> previous = new ArrayList<>();
        for (Map.Entry<String, Object> e : session.getFilledSlots().entrySet()) {
            if (e.getKey().startsWith("__")) continue;
            String label = session.labelFor(e.getKey());
            if (label == null) label = formatPreviousLabel(e.getKey(), e.getValue());
            previous.add(new SlotPromptDto.PreviousSelection(e.getKey(), label));
        }

        // Phase 4.5+ cosmetic: dinamicno racunaj totalSteps. Slotovi koji
        // imaju prefill koji vraca empty-string sentinel (npr. limitValue
        // za MARKET orderType) se skipuju — odbijemo ih iz total-a.
        int total = countVisibleSteps(tpl, session, user);

        return new SlotPromptDto(
                session.getWizardId(),
                tpl.toolName(),
                tpl.title(),
                slot.name(),
                slot.prompt(),
                slot.type().name(),
                options,
                Math.min(previous.size() + 1, total),
                total,
                previous,
                error
        );
    }

    private String formatPreviousLabel(String key, Object value) {
        if (value == null) return "(prazno)";
        return key + ": " + value;
    }

    /* ============================== DISPATCH ============================== */

    private SlotSelectResponseDto dispatchAndComplete(WizardSession session, UserContext user) {
        try {
            WizardTemplate tpl = session.getTemplate();
            // Strip internal context keys before passing to handler
            Map<String, Object> args = new java.util.LinkedHashMap<>();
            session.getFilledSlots().forEach((k, v) -> {
                if (!k.startsWith("__")) args.put(k, v);
            });
            Map<String, Object> finalArgs = tpl.argsBuilder().apply(user, args);

            WriteToolHandler handler = (WriteToolHandler) toolRegistry.get(tpl.toolName())
                    .orElseThrow(() -> new IllegalStateException("Tool not found: " + tpl.toolName()));

            AgentActionPreviewDto preview = agentActionGateway.createPending(
                    session.getConversationUuid().toString(),
                    tpl.toolName(),
                    finalArgs,
                    user,
                    handler
            );
            sessions.remove(session.getWizardId());
            return SlotSelectResponseDto.completed(preview);
        } catch (Exception e) {
            log.warn("Wizard dispatch failed: {}", e.getMessage(), e);
            sessions.remove(session.getWizardId());
            return SlotSelectResponseDto.invalid(null,
                    "Nije moguce kreirati pregled: " + e.getMessage());
        }
    }

    /* ============================== CANCEL ============================== */

    public boolean cancel(UUID wizardId, UserContext user) {
        WizardSession session = sessions.get(wizardId);
        if (session == null) return false;
        if (!session.ownedBy(user.userId(), user.userRole())) return false;
        sessions.remove(wizardId);
        return true;
    }

    /* ============================== TTL CLEANUP ============================== */

    @Scheduled(fixedRate = 60_000)
    public void purgeExpired() {
        Instant cutoff = Instant.now().minus(TTL);
        sessions.entrySet().removeIf(e -> e.getValue().getLastTouched().isBefore(cutoff));
    }

    /* ============================== HELPERS ============================== */

    /**
     * Counts visible interactive slots, excluding ones that the prefill hook
     * indicates should be skipped (returns null or empty-string sentinel).
     * Sa ovim, "Korak X od Y" badge prikazuje stvaran broj koraka — npr. za
     * orderType=MARKET buy order, limitValue/stopValue prefill returns ""
     * sentinel pa total = 4 umesto 6.
     *
     * <p>Najbolji-effort: pozove prefill na svaki slot sa trenutnim
     * filledSlots. Ako prefill baci, broji slot (safe default).</p>
     */
    private int countVisibleSteps(WizardTemplate tpl, WizardSession session, UserContext user) {
        int visible = 0;
        for (SlotDefinition s : tpl.slots()) {
            if (s.name().startsWith("__")) continue;
            try {
                Optional<Object> pref = s.prefill().apply(user, session.getFilledSlots());
                if (pref.isPresent()) {
                    Object v = pref.get();
                    if (v == null || (v instanceof String str && str.isEmpty())) {
                        // skip — invisible slot
                        continue;
                    }
                }
            } catch (Exception ignored) {
                // ako prefill puca, brojimo slot kao visible (safe default)
            }
            visible++;
        }
        return Math.max(visible, 1);
    }

    private boolean isRoleAllowed(WizardTemplate tpl, UserContext user) {
        if (tpl.allowedRoles() == null || tpl.allowedRoles().isEmpty()) return true;
        return tpl.allowedRoles().stream().anyMatch(r -> r.equalsIgnoreCase(user.userRole()));
    }

    private UserContext contextUser(WizardSession session) {
        return new UserContext(session.getUserId(), session.getUserRole());
    }
}
