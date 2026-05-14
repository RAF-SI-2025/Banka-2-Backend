package rs.raf.banka2_bek.investmentfund.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "client_fund_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientFundTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fund_id", nullable = false)
    private Long fundId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_role", nullable = false, length = 16)
    private String userRole;

    @Column(name = "amount_rsd", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountRsd;

    @Column(name = "source_account_id", nullable = false)
    private Long sourceAccountId;

    @Column(name = "is_inflow", nullable = false)
    @org.hibernate.annotations.ColumnDefault("1")
    private boolean inflow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClientFundTransactionStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;
}
