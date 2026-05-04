package rs.raf.banka2_bek.investmentfund.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fund_value_snapshots", uniqueConstraints = {
        @UniqueConstraint(name = "uk_fvs_fund_date",
                columnNames = {"fund_id", "snapshot_date"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FundValueSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fund_id", nullable = false)
    private Long fundId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "fund_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal fundValue;

    @Column(name = "liquid_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal liquidAmount;

    @Column(name = "invested_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal investedTotal;

    @Column(precision = 19, scale = 4)
    private BigDecimal profit;
}
