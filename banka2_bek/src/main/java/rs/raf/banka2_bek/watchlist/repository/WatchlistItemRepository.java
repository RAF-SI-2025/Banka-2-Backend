package rs.raf.banka2_bek.watchlist.repository;

// ============================================================
// TODO [B6 - Watchlist | Nosilac: L. Draskovic]
//
// JPA repozitorijum za entitet WatchlistItem.
//
// IMPLEMENTIRATI (custom metode):
//   - List<WatchlistItem> findByWatchlistIdOrderByAddedAtAsc(Long watchlistId)
//       -- vraca sve stavke date liste sortirane po datumu dodavanja
//   - Optional<WatchlistItem> findByWatchlistIdAndListingId(Long watchlistId, Long listingId)
//       -- pronalazi konkretnu stavku po listi i listingu (koristi se pre brisanja)
//   - void deleteByWatchlistIdAndListingId(Long watchlistId, Long listingId)
//       -- brise stavku po listi i listingu; anotovati sa @Modifying + @Transactional
//   - void deleteAllByWatchlistId(Long watchlistId)
//       -- brise sve stavke liste (poziva se pre brisanja cele liste)
//       anotovati sa @Modifying + @Transactional
//   - boolean existsByWatchlistIdAndListingId(Long watchlistId, Long listingId)
//       -- provera da li je hartija vec na listi pre dodavanja
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B6.
// ============================================================

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.watchlist.model.WatchlistItem;

import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

    List<WatchlistItem> findByWatchlistIdOrderByAddedAtAsc(Long watchlistId);

    Optional<WatchlistItem> findByWatchlistIdAndListingId(Long watchlistId, Long listingId);

    @Modifying
    @Transactional
    void deleteByWatchlistIdAndListingId(Long watchlistId, Long listingId);

    @Modifying
    @Transactional
    void deleteAllByWatchlistId(Long watchlistId);

    boolean existsByWatchlistIdAndListingId(Long watchlistId, Long listingId);
}