package rs.raf.banka2_bek.savings.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import rs.raf.banka2_bek.currency.model.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "savings_transactions")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsTransaction {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposit_id", nullable = false)
    private SavingsDeposit deposit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private SavingsTransactionType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "processed_date", nullable = false)
    private LocalDate processedDate;

    @Column(name = "resulting_transaction_id")
    private Long resultingTransactionId;

    @Column(length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
