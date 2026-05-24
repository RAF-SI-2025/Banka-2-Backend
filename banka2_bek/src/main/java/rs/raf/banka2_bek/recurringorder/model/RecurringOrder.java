package rs.raf.banka2_bek.recurringorder.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ============================================================
// [B8 - Trajni nalozi (DCA / RecurringOrder) | Nosilac: Nikola Djurovic] - DONE
//
// JPA entitet koji predstavlja jedan trajni nalog (DCA nalog).
// Tabla u bazi: `recurring_orders`.
//
// IMPLEMENTIRANO — dodata sva polja (nakon `id`):
//   - ownerId      Long,   @Column(nullable=false)
//                          vlasnik naloga (klijent ili aktuar)
//   - ownerType    String, @Column(nullable=false, length=16)
//                          vrednosti "CLIENT" ili "EMPLOYEE" — konzistentno
//                          sa Order.userRole konvencijom iz orders paketa
//   - listingId    Long,   @Column(nullable=false)
//                          ID hartije od vrednosti (referenca na listings tabelu)
//   - direction    String, @Column(nullable=false, length=4)
//                          vrednosti "BUY" ili "SELL" — koristiti plain String,
//                          ne uvoditi novi enum
//   - mode         RecurringMode, @Enumerated(EnumType.STRING), @Column(nullable=false, length=16)
//                          da li je `value` kolicina ili novcani iznos
//   - value        BigDecimal, @Column(nullable=false, precision=19, scale=4)
//                          kolicina hartija (BY_QUANTITY) ili iznos u valuti racuna (BY_AMOUNT)
//   - accountId    Long,   @Column(nullable=false)
//                          racun sa kojeg se skida iznos pri izvrsavanju
//   - cadence      RecurringCadence, @Enumerated(EnumType.STRING), @Column(nullable=false, length=8)
//                          ucestalost: DAILY / WEEKLY / MONTHLY
//   - nextRun      LocalDateTime, @Column(nullable=false)
//                          datum i vreme sledeceg izvrsavanja; scheduler ga azurira
//                          pozivom cadence.advancedFrom(nextRun) posle svakog PASS-a
//   - active       boolean, @ColumnDefault("1"), @Column(nullable=false)
//                          false = pauzirano ili otkazano; scheduler preskace neaktivne
//   - createdAt    LocalDateTime, @CreationTimestamp, @Column(updatable=false)
//   - updatedAt    LocalDateTime, @UpdateTimestamp
//
// Konvencija: pratiti paket `savings` kao sablon (Lombok @Builder, @Getter, @Setter,
//             @NoArgsConstructor, @AllArgsConstructor, @Version nije potreban ovde).
// Spec: Zadaci_Backend.pdf, zadatak B8.
// ============================================================
@Entity
@Table(name = "recurring_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 16)
    private String ownerType;

    @Column(nullable = false)
    private Long listingId;

    @Column(nullable = false, length = 4)
    private String direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecurringMode mode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal value;

    @Column(nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private RecurringCadence cadence;

    @Column(nullable = false)
    private LocalDateTime nextRun;

    @ColumnDefault("1")
    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
