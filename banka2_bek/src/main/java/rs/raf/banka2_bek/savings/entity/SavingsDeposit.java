package rs.raf.banka2_bek.savings.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import rs.raf.banka2_bek.currency.model.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "savings_deposits")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsDeposit {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "linked_account_id", nullable = false)
    private Long linkedAccountId;

    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(name = "annual_interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal annualInterestRate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Column(name = "next_interest_payment_date", nullable = false)
    private LocalDate nextInterestPaymentDate;

    @Column(name = "total_interest_paid", nullable = false, precision = 19, scale = 4)
    @ColumnDefault("0")
    private BigDecimal totalInterestPaid;

    @Column(name = "auto_renew", nullable = false)
    @ColumnDefault("0")
    private Boolean autoRenew;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SavingsDepositStatus status;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
