package rs.raf.banka2_bek.savings.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import rs.raf.banka2_bek.currency.model.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "savings_interest_rates",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_savings_rates_currency_term_active",
        columnNames = {"currency_id", "term_months", "active"}
    )
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsInterestRate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(name = "annual_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal annualRate;

    @Column(nullable = false)
    @ColumnDefault("1")
    private Boolean active;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
