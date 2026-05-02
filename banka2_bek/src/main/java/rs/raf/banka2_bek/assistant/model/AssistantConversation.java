package rs.raf.banka2_bek.assistant.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Konverzacija sa Arbitro asistentom. Soft-delete pattern (deletedAt) +
 * scheduled cleanup posle 7 dana (assistant.conversation-retention-days).
 */
@Entity
@Table(name = "assistant_conversations", indexes = {
        @Index(name = "idx_assistant_conv_user", columnList = "userId,userRole"),
        @Index(name = "idx_assistant_conv_uuid", columnList = "conversationUuid")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistantConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @UuidGenerator
    @Column(nullable = false, unique = true, columnDefinition = "uuid")
    private UUID conversationUuid;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String userRole;

    @Column(length = 120)
    private String title;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime deletedAt;

    /**
     * Phase 5 — kondenzovani sumar konverzacije generisan auto-LLM-om kad
     * istorija predje threshold (default 16 poruka). Sluzi kao zamena za
     * literally history u kontextu — stedi tokens i pomaze malim modelima
     * (Gemma 4 E2B) da odrze koherenciju u dugim razgovorima.
     */
    @Lob
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /** Index do kog su poruke bile pokrivene poslednjim summary-jem. */
    private Long summaryUpToMessageId;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<AssistantMessage> messages = new ArrayList<>();

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
