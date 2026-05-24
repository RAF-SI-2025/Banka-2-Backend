package rs.raf.banka2_bek.notification.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// [B1 — DONE] In-app notification entity persisted in table "notifications".
// All B1-specified fields are implemented: recipientId, recipientType,
// notificationType (STRING enum), title, body, read (@ColumnDefault("0")),
// createdAt (@PrePersist auto-set), referenceType (nullable), referenceId (nullable).
//
// [B4 — Petar] referenceType / referenceId are the primary mechanism for the
// frontend to deep-link to the originating resource (e.g., referenceType="PAYMENT",
// referenceId=paymentId). B4 should populate these fields in every notify() call
// so that both the UI and B4 email templates can navigate to / enrich from the resource.
//
// [B5 — Aleksa] When adding PRICE_ALERT notifications, pass referenceType="PRICE_ALERT",
// referenceId=alertId so the frontend can open the alert detail.
//
// [B8 — Nikola Djurovic - DONE] When adding RECURRING_ORDER_SKIPPED notifications, pass
// referenceType="RECURRING_ORDER", referenceId=recurringOrderId.

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long recipientId;

    @Column(nullable = false)
    private String recipientType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType notificationType;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(nullable = false)
    @ColumnDefault("0")
    private boolean read;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private String referenceType;

    @Column
    private Long referenceId;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
