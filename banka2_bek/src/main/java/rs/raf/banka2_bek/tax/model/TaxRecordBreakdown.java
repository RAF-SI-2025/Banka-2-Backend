package rs.raf.banka2_bek.tax.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * P2.4 — per-listing breakdown of {@link TaxRecord}.
 *
 * Spec Celina 3 §516-518: porez na kapitalnu dobit je 15% od (sell - buy)
 * <em>per listing</em>, agregiran u RSD. Postojeci {@link TaxRecord}
 * cuva samo agregirani total za korisnika; ovaj entitet daje granularan
 * pogled — koliko je profita doslo iz koje hartije, i koliki je porez
 * vezan za nju.
 *
 * Koristi se za:
 *   * UI prikaz: "Porez tracking" portal moze da listira "AAPL: +$50 → 7.50 RSD",
 *     "MSFT: +$120 → 18.00 RSD", umesto cistog totala.
 *   * Auditiranje: kad korisnik pita zasto je porez 25.50 RSD, mozemo
 *     pokazati doprinos po hartiji.
 */
@Entity
@Table(name = "tax_record_breakdowns",
        indexes = {
                @Index(name = "ix_tax_breakdown_record", columnList = "tax_record_id"),
                @Index(name = "ix_tax_breakdown_listing", columnList = "listing_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxRecordBreakdown {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_record_id", nullable = false)
    private TaxRecord taxRecord;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "ticker", nullable = false, length = 32)
    private String ticker;

    /** ISO valuta listinga (USD, EUR, RSD...) — pre konverzije u RSD. */
    @Column(name = "listing_currency", nullable = false, length = 8)
    private String listingCurrency;

    /** Profit iz ovog listinga u valuti listinga. Moze biti negativan. */
    @Column(name = "profit_native", nullable = false, precision = 18, scale = 4)
    @org.hibernate.annotations.ColumnDefault("0")
    @Builder.Default
    private BigDecimal profitNative = BigDecimal.ZERO;

    /** Profit konvertovan u RSD srednjim kursom (negativni se ne oporezuju). */
    @Column(name = "profit_rsd", nullable = false, precision = 18, scale = 4)
    @org.hibernate.annotations.ColumnDefault("0")
    @Builder.Default
    private BigDecimal profitRsd = BigDecimal.ZERO;

    /** Porez na ovaj listing (15% × max(0, profitRsd)) u RSD. */
    @Column(name = "tax_owed", nullable = false, precision = 18, scale = 4)
    @org.hibernate.annotations.ColumnDefault("0")
    @Builder.Default
    private BigDecimal taxOwed = BigDecimal.ZERO;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
}
