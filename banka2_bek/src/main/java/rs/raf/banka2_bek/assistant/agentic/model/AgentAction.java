package rs.raf.banka2_bek.assistant.agentic.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pending agentic action kreiran kada LLM pozove write tool i agentic mode
 * je ON. Cuva parametre, conversation reference, status, idempotency UUID.
 *
 * Lifecycle:
 *   1. AgentActionGateway.createPending() — status=PENDING + TTL 5 min
 *   2. FE prikazuje preview modal
 *   3. POST /assistant/actions/{uuid}/confirm — status=EXECUTED ili FAILED
 *   4. POST /assistant/actions/{uuid}/reject — status=REJECTED
 *   5. AgentActionExpirationScheduler — status=EXPIRED ako proslo TTL
 *
 * Spec: LLM_Asistent_Plan.txt v3.5 §17.
 */
@Entity
@Table(name = "agent_actions",
        indexes = {
                @Index(name = "idx_agent_action_uuid", columnList = "actionUuid", unique = true),
                @Index(name = "idx_agent_action_user_status", columnList = "userId,userRole,status"),
                @Index(name = "idx_agent_action_created", columnList = "createdAt"),
                // Phase 5 optimizacija — composite index za scheduler upit
                // findStalePending / markExpired (WHERE status='PENDING' AND expiresAt < now)
                @Index(name = "idx_agent_action_status_expires", columnList = "status,expiresAt")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Idempotency UUID — generise se BE-stranom, ide u SSE preview event. */
    @Column(nullable = false, unique = true, length = 36)
    @Builder.Default
    private String actionUuid = UUID.randomUUID().toString();

    /** Conversation UUID — reverse lookup za audit + kasnije resume LLM-a. */
    @Column(length = 36)
    private String conversationUuid;

    /**
     * Phase 5 multi-step plan — UUID parent akcije ako je ova akcija deo
     * lanca koji LLM predlaze ("prvo prebaci EUR u USD, pa BUY AAPL").
     * Null ako je samostalna akcija. FE prikazuje progress indicator
     * "Korak X od Y" gde Y dolazi iz {@link #planTotalSteps}.
     */
    @Column(length = 36)
    private String parentActionUuid;

    /** Total broj koraka u multi-step planu (samo na child akcijama). */
    private Integer planTotalSteps;

    /** Trenutni step index (1-based, samo na child akcijama). */
    private Integer planStepIndex;

    /** Tool name (write tool naziv, npr. "create_payment"). */
    @Column(nullable = false, length = 80)
    private String toolName;

    /** Sirov JSON sa parametrima koje je LLM predlozio (pre user edit-a). */
    @Lob
    @Column(name = "parameters_json", columnDefinition = "TEXT", nullable = false)
    private String parametersJson;

    /** Final JSON parametri (posle user edit-a) — null ako jos nije confirm-ovan. */
    @Lob
    @Column(name = "final_parameters_json", columnDefinition = "TEXT")
    private String finalParametersJson;

    /** Human-readable summary za preview UI ("Placanje 5000 RSD Stefanu"). */
    @Column(length = 500)
    private String summary;

    /** Lista warning poruka za UI (opciono). */
    @Lob
    @Column(name = "warnings_json", columnDefinition = "TEXT")
    private String warningsJson;

    /** True ako write tool zahteva OTP gate u confirm endpoint-u. */
    @ColumnDefault("0")
    @Column(nullable = false)
    private boolean requiresOtp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AgentActionStatus status = AgentActionStatus.PENDING;

    /** User ID iz UserContext-a — owner check pri confirm-u. */
    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String userRole;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /** Timestamp posle confirm/reject/expire — null dok je PENDING. */
    private LocalDateTime resolvedAt;

    /** Rezultat izvrsenja (JSON) — popunjeno ako status=EXECUTED. */
    @Lob
    @Column(name = "execution_result_json", columnDefinition = "TEXT")
    private String executionResultJson;

    /** Error message — popunjeno ako status=FAILED. */
    @Column(length = 1000)
    private String errorMessage;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = AgentActionStatus.PENDING;
        if (actionUuid == null) actionUuid = UUID.randomUUID().toString();
    }

    public boolean isPending() {
        return status == AgentActionStatus.PENDING;
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
