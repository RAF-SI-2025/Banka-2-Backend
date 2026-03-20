package rs.raf.banka2_bek.card.model;

import jakarta.persistence.*;
import lombok.*;
import rs.raf.banka2_bek.account.model.Account;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "card_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CardRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    private BigDecimal cardLimit;

    @Column(nullable = false)
    private String clientEmail;

    @Column(nullable = false)
    private String clientName;

    @Column(nullable = false)
    private String status; // PENDING, APPROVED, REJECTED

    private String rejectionReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;
    private String processedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }
}
