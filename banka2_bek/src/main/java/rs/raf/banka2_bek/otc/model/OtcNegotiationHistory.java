package rs.raf.banka2_bek.otc.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// ============================================================
// TODO [B10 - Istorija OTC pregovora | Nosilac: Aja Timotic]
//
// Entitet koji cuva jedan snimak stanja pregovora u trenutku
// svake izmene (counter-offer, accept, decline).
//
// IMPLEMENTIRATI — dodati sledeca polja (ispod id):
//
//   - negotiationId : Long
//       @Column(name = "negotiation_id", nullable = false)
//       Strani kljuc ka OtcOffer.id (logicki FK, bez @ManyToOne
//       da bi historija ostala cak i kad se ponuda obrise).
//
//   - quantity : Integer
//       @Column(nullable = false)
//       Kolicina akcija u trenutku ove izmene.
//
//   - pricePerShare : BigDecimal
//       @Column(name = "price_per_share", nullable = false, precision = 19, scale = 4)
//       Cena po akciji u trenutku ove izmene.
//
//   - premium : BigDecimal
//       @Column(nullable = false, precision = 19, scale = 4)
//       Premija opcije u trenutku ove izmene.
//
//   - settlementDate : LocalDate
//       @Column(name = "settlement_date", nullable = false)
//       Datum izmirenja koji je vazio u ovoj iteraciji.
//
//   - status : String
//       @Column(nullable = false, length = 32)
//       Naziv statusa ponude (npr. "ACTIVE", "ACCEPTED", "DECLINED")
//       kao String jer OtcOfferStatus enum moze rasti — ne koristiti
//       @Enumerated da bi historija bila otporna na refaktoring.
//
//   - modifiedById : Long
//       @Column(name = "modified_by_id", nullable = false)
//       ID korisnika koji je pokrenuo ovu izmenu (kupac ili prodavac).
//
//   - modifiedByName : String
//       @Column(name = "modified_by_name", nullable = false, length = 255)
//       Puno ime korisnika u trenutku izmene (snapshot — ne FK jer
//       korisnik moze biti obrisan ili promeniti ime).
//
//   - createdAt : LocalDateTime
//       @CreationTimestamp
//       @Column(name = "created_at", nullable = false, updatable = false)
//       Automatski postavlja Hibernate pri INSERT-u.
//
// Anotacije na klasi: @Entity, @Table(name = "otc_negotiation_history"),
//   @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor, @Builder
//   (prati obrazac iz SavingsDeposit.java).
//
// Importi koji ce biti potrebni (dodati po potrebi):
//   import java.math.BigDecimal;
//   import java.time.LocalDate;
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B10.
// ============================================================

@Entity
@Table(name = "otc_negotiation_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtcNegotiationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "negotiation_id", nullable = false)
    private Long negotiationId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "price_per_share", nullable = false, precision = 19, scale = 4)
    private BigDecimal pricePerShare;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal premium;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "modified_by_id", nullable = false)
    private Long modifiedById;

    @Column(name = "modified_by_name", nullable = false, length = 255)
    private String modifiedByName;


    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
