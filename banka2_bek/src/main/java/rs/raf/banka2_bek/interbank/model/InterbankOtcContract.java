package rs.raf.banka2_bek.interbank.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Inter-bank OTC opcioni ugovor nakon prihvatanja ponude
 * (snapshot OptionDescription iz protokola §2.7.2).
 *
 * Spec: Celina 5 — opcioni ugovor; protokol §3.6 i §2.7.2.
 * Povezan je sa InterbankOtcNegotiation (negotiationId u protokolu = ForeignBankId
 * kod prodavceve banke).
 */
@Entity
@Table(name = "interbank_otc_contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterbankOtcContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "negotiation_id", nullable = false)
    private InterbankOtcNegotiation negotiation;

    /** Duplikat negotiationId iz ugovora radi brzog upita bez join-a. */
    @Column(name = "negotiation_routing_number", nullable = false, length = 8)
    private String negotiationRoutingNumber;

    @Column(name = "negotiation_opaque_id", nullable = false, length = 64)
    private String negotiationOpaqueId;

    @Column(name = "stock_ticker", nullable = false, length = 32)
    private String stockTicker;

    @Column(name = "price_per_unit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal pricePerUnitAmount;

    @Column(name = "price_per_unit_currency", nullable = false, length = 8)
    private String pricePerUnitCurrency;

    @Column(name = "settlement_instant", nullable = false)
    private Instant settlementInstant;

    @Column(name = "share_amount", nullable = false)
    private Integer shareAmount;

    @Column(name = "premium_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal premiumAmount;

    @Column(name = "premium_currency", nullable = false, length = 8)
    private String premiumCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private InterbankOtcContractStatus status = InterbankOtcContractStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
