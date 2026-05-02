package rs.raf.banka2_bek.interbank.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T12 — Inter-bank OTC pregovor (kupac i prodavac u razlicitim bankama).
 *
 * Spec referenca:
 *  - Protokol §2.3 Foreign object identifiers (kompozitni ForeignBankId)
 *  - Protokol §3 OTC negotiation protocol (§3.1–§3.7)
 *  - Celina 5 (Nova) — OTC Trgovina (Pregovaranje, Postignut dogovor)
 *  - Celina 4 (Nova) — Aktivne ponude (entitet polja)
 *
 * Razlika u odnosu na intra-bank `otc/model/OtcOffer`:
 *   - Strana iz druge banke je kompozitni opaque ID (routingNumber + idString)
 *     koji nas (po §2.3) NE smemo interpretirati. Zato cuvamo string id, ne
 *     FK ka lokalnoj tabeli klijenata/zaposlenih.
 *   - Pregovor ima eksplicitnu lokalnu/foreign stranu — `localPartyType`
 *     odredjuje da li smo MI kupac (BUYER) ili prodavac (SELLER), i ko od
 *     `localParty*` / `foreignParty*` polja je tudja banka.
 *
 * Autoritativna kopija pregovora je uvek kod prodavceve banke (§3.2). Ako
 * smo mi prodavac (`localPartyType == SELLER`), `foreignNegotiationId`
 * generisemo lokalno (UUID) i vracamo ga kupcu kao `ForeignBankId{
 * ourRoutingNumber, generatedId}`. Ako smo kupac, `foreignNegotiationId`
 * je sto nam je vratila partner banka iz POST /negotiations.
 *
 * Pravilo turne (§3.3):
 *   - turn je buyer ako lastModifiedBy != buyerId
 *   - turn je seller ako lastModifiedBy != sellerId
 * Suprotna strana ne sme postavljati counter-offer dok je njen ne-red
 * (handler odbija sa 409 Conflict).
 *
 * NE prosirivati intra-bank `otc/model/OtcOffer` — namene su razlicite i
 * mesanje bi pokvarilo postojeci flow (Long vs ForeignBankId tipovi za
 * strane, drugaciji status enum, drugaciji lifecycle).
 *
 * KORISNICI ENTITETA:
 *   T2 (OTC outbound) — kreira/azurira pri slanju ka partner banci
 *   T3 (OTC inbound)  — kreira/azurira pri primanju zahteva (POST/PUT/DELETE
 *                       /accept handler-i)
 *   listMyPositions / listBankPositions u InvestmentFundService NE
 *                       interaguju sa ovim entitetom (T12 cuva i pozicije
 *                       u fondu, ali pozicije nisu OTC pregovori).
 */
@Entity
@Table(name = "interbank_otc_negotiations", indexes = {
        @Index(
                name = "idx_ibotc_neg_foreign_id",
                columnList = "foreign_negotiation_routing_number, foreign_negotiation_id_string",
                unique = true
        ),
        @Index(name = "idx_ibotc_neg_status_modified", columnList = "status, last_modified_at"),
        @Index(name = "idx_ibotc_neg_local_party", columnList = "local_party_id, local_party_role")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterbankOtcNegotiation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── Foreign negotiation ID (§2.3, §3.2) ──────────────────────────────
    // Kompozitni ID koji se razmenjuje izmedju banaka. Po §3.2,
    // prodavceva banka ga generise, a kupceva banka ga koristi za
    // sve naredne PUT/GET/DELETE/accept zahteve.
    /** Routing number banke koja je generisala ovaj negotiationId (uvek = prodavceva banka). */
    @Column(name = "foreign_negotiation_routing_number", nullable = false)
    private Integer foreignNegotiationRoutingNumber;

    /** Opaque id string (max 64 bajta po §2.3). */
    @Column(name = "foreign_negotiation_id_string", nullable = false, length = 64)
    private String foreignNegotiationIdString;

    // ─── Lokalna i strana strana ──────────────────────────────────────────
    /** Da li smo MI kupac (BUYER) ili prodavac (SELLER) u ovom pregovoru. */
    @Enumerated(EnumType.STRING)
    @Column(name = "local_party_type", nullable = false, length = 16)
    private InterbankPartyType localPartyType;

    /** Lokalni id korisnika (clients.id ili employees.id, zavisno od role). */
    @Column(name = "local_party_id", nullable = false)
    private Long localPartyId;

    /**
     * Lokalna rola: "CLIENT" ili "EMPLOYEE". Po Celini 5 (Nova) §840-848
     * "Klijenti vide ponude Klijenata, Aktuari vide ponude Aktuara" — pravilo
     * se proverava na FE ili u service sloju (T2/T3), ovde je informativno.
     */
    @Column(name = "local_party_role", nullable = false, length = 16)
    private String localPartyRole;

    /** Routing number banke u kojoj je foreign strana. */
    @Column(name = "foreign_party_routing_number", nullable = false)
    private Integer foreignPartyRoutingNumber;

    /** Opaque id string foreign strane (max 64 bajta po §2.3, ne interpretiramo ga). */
    @Column(name = "foreign_party_id_string", nullable = false, length = 64)
    private String foreignPartyIdString;

    // ─── Predmet pregovora ────────────────────────────────────────────────
    /**
     * Ticker hartije o kojoj se pregovara. Po protokolu §2.7.3, akcije se
     * jedinstveno identifikuju ticker-om i sve banke moraju znati iste
     * ticker-e (isti data source). Drzimo string, ne @ManyToOne ka Listing,
     * jer u inter-bank kontekstu nismo garantovani da je listing prisutan
     * kod nas (FE ce resolve-ovati ime preko stock servisa kad treba).
     */
    @Column(nullable = false, length = 16)
    private String ticker;

    /** Broj akcija u pregovoru (integer > 0 po protokolu §2.7.2). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * Cena po akciji u listing valuti (vidi `priceCurrency`). BigDecimal
     * po protokolu §2.5 (NE float64).
     */
    @Column(name = "price_per_unit", nullable = false, precision = 19, scale = 4)
    private BigDecimal pricePerUnit;

    /** ISO4217 kod valute za pricePerUnit (npr. "USD", "EUR"). */
    @Column(name = "price_currency", nullable = false, length = 3)
    private String priceCurrency;

    /** Premija za opcioni ugovor — placa je kupac prodavcu kad pregovor bude prihvacen (§3.6). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal premium;

    /** Valuta premije (najcesce ista kao pricecurrency, ali protokol dozvoljava razliku). */
    @Column(name = "premium_currency", nullable = false, length = 3)
    private String premiumCurrency;

    /** Datum dospeca opcije — posle ovoga ugovor istice ako nije iskoriscen (§2.7.2). */
    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    // ─── Pravilo turne (§3.3) ─────────────────────────────────────────────
    /**
     * ForeignBankId.routingNumber strane koja je poslednja izmenila pregovor.
     * Koristi se za pravilo turne: turn je buyer ako lastModifiedBy != buyerId.
     */
    @Column(name = "last_modified_by_routing_number", nullable = false)
    private Integer lastModifiedByRoutingNumber;

    /** ForeignBankId.id strane koja je poslednja izmenila pregovor. */
    @Column(name = "last_modified_by_id_string", nullable = false, length = 64)
    private String lastModifiedByIdString;

    // ─── Stanje ───────────────────────────────────────────────────────────
    /**
     * §3.4 OtcNegotiation polje — true dok je pregovor otvoren. Postaje
     * false kad bilo koja strana posalje DELETE /negotiations (§3.5) ili
     * kad se sklopi ugovor (§3.6).
     *
     * Nezavisno polje od `status`-a: redundantno za brze GET-ove ali tacno
     * preslikava protokol shape (klijenti banke ce dobiti raw boolean iz
     * GET /negotiations/{rn}/{id}).
     */
    @Column(name = "is_ongoing", nullable = false)
    @ColumnDefault("1")
    private boolean ongoing = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InterbankOtcNegotiationStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_modified_at", nullable = false)
    private LocalDateTime lastModifiedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (lastModifiedAt == null) lastModifiedAt = now;
        if (status == null) status = InterbankOtcNegotiationStatus.ACTIVE;
    }

    @PreUpdate
    void onUpdate() {
        lastModifiedAt = LocalDateTime.now();
    }
}
