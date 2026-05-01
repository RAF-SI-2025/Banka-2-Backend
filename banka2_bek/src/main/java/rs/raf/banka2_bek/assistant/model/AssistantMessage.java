package rs.raf.banka2_bek.assistant.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Pojedinacna poruka u Arbitro konverzaciji.
 * - role=USER/ASSISTANT/SYSTEM/TOOL (OpenAI semantika)
 * - toolCalls JSONB se popunjava SAMO kad role=ASSISTANT i model je pozvao alate
 * - toolCallId se popunjava SAMO kad role=TOOL
 * - thinking content se NIKAD ne persistuje (samo audit count u service-u)
 */
@Entity
@Table(name = "assistant_messages", indexes = {
        @Index(name = "idx_assistant_msg_conv", columnList = "conversation_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistantMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private AssistantConversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssistantMessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * JSON serijalizovana lista tool_call objekata (OpenAI format).
     * Plain text storage; konvertovati u JsonBinaryType (hypersistence) tek
     * kad pom.xml dobije tu zavisnost. Pattern je vec uspostavljen u
     * interbank/model/InterbankMessage.java.
     */
    @Column(columnDefinition = "TEXT")
    private String toolCalls;

    @Column(length = 80)
    private String toolCallId;

    @Column(length = 120)
    private String pageRoute;

    @Column(length = 120)
    private String pageName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
