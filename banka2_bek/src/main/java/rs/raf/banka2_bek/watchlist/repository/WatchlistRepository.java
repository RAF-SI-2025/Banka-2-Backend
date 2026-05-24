package rs.raf.banka2_bek.watchlist.repository;

// ============================================================
// TODO [B6 - Watchlist | Nosilac: L. Draskovic]
//
// JPA repozitorijum za entitet Watchlist.
//
// IMPLEMENTIRATI (custom metode):
//   - List<Watchlist> findByOwnerIdAndOwnerTypeOrderByCreatedAtAsc(Long ownerId, WatchlistOwnerType ownerType)
//       -- vraca sve liste datog vlasnika, sortirane po datumu kreiranja
//   - Optional<Watchlist> findByIdAndOwnerIdAndOwnerType(Long id, Long ownerId, WatchlistOwnerType ownerType)
//       -- pronalazi listu po id-u i proverava vlasnistvo u jednom upitu (koristi se u service-u za ownership check)
//   - boolean existsByOwnerIdAndOwnerTypeAndName(Long ownerId, WatchlistOwnerType ownerType, String name)
//       -- provera duplikata naziva pre kreiranja/preimenovanja liste
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B6.
// ============================================================

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.watchlist.model.Watchlist;
import rs.raf.banka2_bek.watchlist.model.WatchlistOwnerType;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    List<Watchlist> findByOwnerIdAndOwnerTypeOrderByCreatedAtAsc(Long ownerId, WatchlistOwnerType ownerType);

    Optional<Watchlist> findByIdAndOwnerIdAndOwnerType(Long id, Long ownerId, WatchlistOwnerType ownerType);

    boolean existsByOwnerIdAndOwnerTypeAndName(Long ownerId, WatchlistOwnerType ownerType, String name);
}