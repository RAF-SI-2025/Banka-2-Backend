package rs.raf.banka2_bek.watchlist.model;

// ============================================================
// TODO [B6 - Watchlist | Nosilac: L. Draskovic]
//
// Entitet koji predstavlja jednu hartiju unutar korisnicke liste pracenja.
// Svaki red povezuje listu sa jednim listingom sa berze.
//
// IMPLEMENTIRATI:
//   - watchlistId (Long, NOT NULL) -- FK na Watchlist.id (kolona "watchlist_id")
//     Koristiti @Column(name="watchlist_id", nullable=false) -- NE @ManyToOne,
//     samo Long, da se izbegnu lazy-load komplikacije u service sloju.
//   - listingId (Long, NOT NULL) -- ID listinga iz tabele `listings` (kolona "listing_id")
//     Ovo je opaque referenca; ne raditi JOIN na Listing entitet -- service
//     ce pozivati ListingRepository/SecurityService za trenutnu cenu i dnevnu promenu.
//   - @CreationTimestamp addedAt (LocalDateTime, NOT NULL, updatable=false)
//     Beleji kada je korisnik dodao hartiju na listu.
//   - Lombok: @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
//   - @Table(name = "watchlist_items") sa UNIQUE constraint na (watchlist_id, listing_id)
//     tako da ista hartija ne moze biti dvaput u istoj listi:
//     @Table(name="watchlist_items", uniqueConstraints =
//         @UniqueConstraint(columnNames={"watchlist_id","listing_id"}))
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B6.
// ============================================================

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "watchlist_items", uniqueConstraints = @UniqueConstraint(columnNames = {"watchlist_id","listing_id"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "watchlist_id", nullable = false)
    private Long watchlistId;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;
}