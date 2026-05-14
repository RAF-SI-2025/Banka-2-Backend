package rs.raf.banka2_bek.investmentfund.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "client_fund_positions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cfp_user_fund",
                columnNames = {"fund_id", "user_id", "user_role"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientFundPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fund_id", nullable = false)
    private Long fundId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_role", nullable = false, length = 16)
    private String userRole; // "CLIENT" — banka je klijent sa ownerClientId

    @Column(name = "total_invested", nullable = false, precision = 19, scale = 4)
    @org.hibernate.annotations.ColumnDefault("0")
    private BigDecimal totalInvested;

    @Column(name = "last_modified_at", nullable = false)
    private LocalDateTime lastModifiedAt;
}
