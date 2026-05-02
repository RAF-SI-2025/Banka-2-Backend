package rs.raf.banka2_bek.interbank.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T12 — Inter-bank OTC opcioni ugovor (sklopljen kroz pregovor izmedju
 * razlicitih banaka).
 *
 * Spec referenca:
 *  - Protokol §2.7.2 Options (lifecycle, settlementDate, exec, expiry)
 *  - Protokol §3.6 Accepting an offer (kako se ugovor formira)
 *  - Protokol §3.6.1 Forming option contracts (mapiranje OtcOffer -> OptionDescription)
 *  - Celina 4 (Nova) — Sklopljeni ugovori
 *  - Celina 5 (Nova) — OTC Trgovina, Postignut dogovor
 *
 * Ugovor se kreira kada druga strana posalje GET /negotiations/{rn}/{id}/accept
 * (§3.6). Pri kreiranju:
 *   - kupac placa premium prodavcu (§3.6 4 postinga)
 *   - prodavac rezervise hartije (§2.7.2)
 *   - sourceNegotiation se prebacuje u status ACCEPTED i ongoing=false
 *
 * `negotiationId` u protokolnom OptionDescription (§2.7.2) je
 * `ForeignBankId{ourRoutingNumber, sourceNegotiation.foreignNegotiationIdString}`
 * — ID je generisan kod prodavceve banke, sto cini option pseudo-account
 * dostupan samo prodavcu koji ce kasnije izvrsiti SAGA prenos vlasnistva.
 *
 * Ugovor je u ACTIVE statusu dok kupac ne iskoristi opciju (EXERCISED) ili
 * dok ne istekne settlementDate (EXPIRED). Po §2.7.2: "if that option was
 * not used, the resources stuck in an option shall be un-reserved" —
 * scheduler proverava `settlementDate < today AND status == ACTIVE` i
 * oslobadja rezervaciju (T9-style hook).
 */
@Entity
@Table(name = "interbank_otc_contracts", indexes = {
        @Index(name = "idx_ibotc_ctr_source_neg", columnList = "source_negotiation_id"),
        @Index(name = "idx_ibotc_ctr_status_settle", columnList = "status, settlement_date"),
        @Index(name = "idx_ibotc_ctr_local_party", columnList = "local_party_id, local_party_role")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterbankOtcContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FK ka InterbankOtcNegotiation koji je doveo do ovog ugovora. Cuvan
     * kao Long (ne @ManyToOne) — relacija je 1:1 i bez kaskadnog brisanja
     * (audit trace cak i ako se pregovor obrise).
     */
    @Column(name = "source_negotiation_id", nullable = false)
    private Long sourceNegotiationId;

    // ─── Lokalna strana (kao u InterbankOtcNegotiation) ───────────────────
    /** Da li smo MI kupac (BUYER) ili prodavac (SELLER) u ovom ugovoru. */
    @Enumerated(EnumType.STRING)
    @Column(name = "local_party_type", nullable = false, length = 16)
    private InterbankPartyType localPartyType;

    /** Lokalni id korisnika (clients.id ili employees.id). */
    @Column(name = "local_party_id", nullable = false)
    private Long localPartyId;

    /** Lokalna rola: "CLIENT" ili "EMPLOYEE". */
    @Column(name = "local_party_role", nullable = false, length = 16)
    private String localPartyRole;

    // ─── Strana strana (kompozitni ForeignBankId po §2.3) ─────────────────
    /** Routing number banke u kojoj je foreign strana. */
    @Column(name = "foreign_party_routing_number", nullable = false)
    private Integer foreignPartyRoutingNumber;

    /** Opaque id string foreign strane (max 64 bajta po §2.3). */
    @Column(name = "foreign_party_id_string", nullable = false, length = 64)
    private String foreignPartyIdString;

    // ─── Predmet ugovora (kopirano iz pregovora pri prihvatanju, §3.6.1) ──
    /** Ticker hartije (po §2.7.3 jedinstveno; svi imaju isti data source). */
    @Column(nullable = false, length = 16)
    private String ticker;

    /** Broj akcija (integer > 0 po protokolu §2.7.2). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    /** Strike price = pricePerUnit iz pregovora (§3.6.1). */
    @Column(name = "strike_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal strikePrice;

    @Column(name = "strike_currency", nullable = false, length = 3)
    private String strikeCurrency;

    /** Premija placena prodavcu pri prihvatanju (§3.6 4 postinga). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal premium;

    @Column(name = "premium_currency", nullable = false, length = 3)
    private String premiumCurrency;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InterbankOtcContractStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Postavlja se kad kupac posalje exercise zahtev i SAGA potvrdi (§2.7.2). */
    @Column(name = "exercised_at")
    private LocalDateTime exercisedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = InterbankOtcContractStatus.ACTIVE;
    }
}
