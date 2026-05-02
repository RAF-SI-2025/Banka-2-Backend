package rs.raf.banka2_bek.assistant.agentic.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Preview payload koji ide u SSE {@code action_preview} event ka FE-u.
 * FE prikazuje confirmation modal sa ovim podacima.
 *
 * Spec: LLM_Asistent_Plan.txt v3.5 §17.
 */
@Data
@Builder
public class AgentActionPreviewDto {

    /** Idempotency UUID — FE ga prosledjuje u confirm/reject pozive. */
    private String actionUuid;

    /** Tool ime (npr. "create_payment") — FE bira ikonu/UI variant po njemu. */
    private String tool;

    /** Human-readable summary za naslov modala. */
    private String summary;

    /**
     * Strukturisana polja koja se prikazuju u UI.
     * Key: human-readable label ("Sa racuna", "Iznos", ...)
     * Value: vrednost (string ili number)
     */
    private Map<String, Object> parameters;

    /** Lista warning poruka ("Iznos prelazi tvoj dnevni limit"). */
    private List<String> warnings;

    /** True ako confirm endpoint zahteva otpCode polje. */
    private boolean requiresOtp;

    /** TTL deadline. FE prikazuje countdown. */
    private LocalDateTime expiresAt;

    /**
     * Phase 5 multi-step plan — popunjeno ako je akcija deo chain-a.
     * FE koristi za "Korak X od Y" progress indicator.
     */
    private Integer planStepIndex;
    private Integer planTotalSteps;
    private String parentActionUuid;
}
