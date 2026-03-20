package rs.raf.banka2_bek.account.model;

import jakarta.persistence.*;
import lombok.*;
import rs.raf.banka2_bek.currency.model.Currency;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "account_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AccountRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    private AccountSubtype accountSubtype;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    private BigDecimal initialDeposit;
    private Boolean createCard;

    // Podaci o podnosiocu
    @Column(nullable = false)
    private String clientEmail;

    @Column(nullable = false)
    private String clientName;

    // Status zahteva
    @Column(nullable = false)
    private String status; // PENDING, APPROVED, REJECTED

    private String rejectionReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;
    private String processedBy; // Email zaposlenog koji je obradio

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }
}
