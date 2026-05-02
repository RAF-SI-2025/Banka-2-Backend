package rs.raf.banka2_bek.interbank.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lokalni zapis inter-bank OTC pregovora (mirror OtcNegotiation / OtcOffer iz protokola).
 *
 * Spec: Celina 5 (Nova) — OTC izmedju banaka; protokol §2.3 (ForeignBankId), §3 (OTC negotiation).
 * Sve strane su uvek ForeignBankId — par routingNumber + neproziran string
 * (kolone buyer/seller/lastModifiedBy + sam negotiationId).
 */
@Entity
@Table(
        name = "interbank_otc_negotiations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_interbank_negotiation_foreign_id",
                columnNames = {"negotiation_routing_number", "negotiation_opaque_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterbankOtcNegotiation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Autoritativni ID pregovora kod banke prodavca (ForeignBankId). */
    @Column(name = "negotiation_routing_number", nullable = false, length = 8)
    private String negotiationRoutingNumber;

    @Column(name = "negotiation_opaque_id", nullable = false, length = 64)
    private String negotiationOpaqueId;

    @Column(name = "stock_ticker", nullable = false, length = 32)
    private String stockTicker;

    /** ISO-8601 datum/vreme poravnanja, cuva se kao Instant u UTC. */
    @Column(name = "settlement_instant", nullable = false)
    private Instant settlementInstant;

    @Column(name = "price_per_unit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal pricePerUnitAmount;

    @Column(name = "price_per_unit_currency", nullable = false, length = 8)
    private String pricePerUnitCurrency;

    @Column(name = "premium_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal premiumAmount;

    @Column(name = "premium_currency", nullable = false, length = 8)
    private String premiumCurrency;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "buyer_routing_number", nullable = false, length = 8)
    private String buyerRoutingNumber;

    @Column(name = "buyer_opaque_id", nullable = false, length = 64)
    private String buyerOpaqueId;

    @Column(name = "seller_routing_number", nullable = false, length = 8)
    private String sellerRoutingNumber;

    @Column(name = "seller_opaque_id", nullable = false, length = 64)
    private String sellerOpaqueId;

    @Column(name = "last_modified_by_routing_number", nullable = false, length = 8)
    private String lastModifiedByRoutingNumber;

    @Column(name = "last_modified_by_opaque_id", nullable = false, length = 64)
    private String lastModifiedByOpaqueId;

    @Column(name = "ongoing", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private boolean ongoing = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
