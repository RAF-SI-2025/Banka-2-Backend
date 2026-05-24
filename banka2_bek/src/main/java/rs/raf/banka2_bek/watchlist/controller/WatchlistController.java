package rs.raf.banka2_bek.watchlist.controller;

// ============================================================
// TODO [B6 - Watchlist | Nosilac: L. Draskovic]
//
// REST kontroler za Watchlist funkcionalnost.
// Koristiti @RequiredArgsConstructor za injekciju WatchlistService-a,
// kao sto radi SavingsDepositController.
//
// Base path: /watchlists
//
// IMPLEMENTIRATI (endpoint metode):
//
//   POST   /watchlists
//     -- kreira novu listu; request body: @Valid CreateWatchlistDto
//        vraca: ResponseEntity<WatchlistDto> sa status 200
//        poziva: watchlistService.createWatchlist(dto)
//
//   GET    /watchlists
//     -- vraca sve liste tekuceg korisnika
//        vraca: ResponseEntity<List<WatchlistDto>>
//        poziva: watchlistService.listMyWatchlists()
//
//   PATCH  /watchlists/{id}
//     -- preimenuje listu; @PathVariable Long id, @Valid @RequestBody CreateWatchlistDto
//        vraca: ResponseEntity<WatchlistDto>
//        poziva: watchlistService.renameWatchlist(id, dto)
//
//   DELETE /watchlists/{id}
//     -- brise listu i sve njene stavke; @PathVariable Long id
//        vraca: ResponseEntity<Void> sa status 204 No Content
//        poziva: watchlistService.deleteWatchlist(id)
//
//   POST   /watchlists/{id}/items
//     -- dodaje hartiju na listu; @PathVariable Long id, @RequestParam Long listingId
//        vraca: ResponseEntity<WatchlistItemDto> sa status 200
//        poziva: watchlistService.addItem(id, listingId)
//
//   DELETE /watchlists/{id}/items/{listingId}
//     -- uklanja hartiju sa liste; @PathVariable Long id, @PathVariable Long listingId
//        vraca: ResponseEntity<Void> sa status 204 No Content
//        poziva: watchlistService.removeItem(id, listingId)
//
//   GET    /watchlists/{id}/items
//     -- vraca stavke liste sa live podacima; @PathVariable Long id,
//        @RequestParam(required=false) String type  -- filter po tipu hartije (STOCK/FOREX/FUTURE/OPTION)
//        vraca: ResponseEntity<List<WatchlistItemDto>>
//        poziva: watchlistService.listItems(id, type)
//
// SECURITY (dodati u GlobalSecurityConfig.java -- radi to koordinator, ne ti):
//   .requestMatchers("/watchlists/**").authenticated()
//
// Konvencija: pratiti paket `savings` kao sablon (SavingsDepositController).
// Spec: Zadaci_Backend.pdf, zadatak B6.
// ============================================================

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import rs.raf.banka2_bek.watchlist.dto.CreateWatchlistDto;
import rs.raf.banka2_bek.watchlist.dto.WatchlistDto;
import rs.raf.banka2_bek.watchlist.dto.WatchlistItemDto;
import rs.raf.banka2_bek.watchlist.service.WatchlistService;

import java.util.List;

@RestController
@RequestMapping("/watchlists")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @PostMapping
    public ResponseEntity<WatchlistDto> create(@Valid @RequestBody CreateWatchlistDto dto) {
        WatchlistDto created = watchlistService.createWatchlist(dto);
        return ResponseEntity.ok(created);
    }

    @GetMapping
    public ResponseEntity<List<WatchlistDto>> list() {
        return ResponseEntity.ok(watchlistService.listMyWatchlists());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<WatchlistDto> rename(@PathVariable("id") Long id, @Valid @RequestBody CreateWatchlistDto dto) {
        return ResponseEntity.ok(watchlistService.renameWatchlist(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        watchlistService.deleteWatchlist(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<WatchlistItemDto> addItem(@PathVariable("id") Long id, @RequestParam("listingId") Long listingId) {
        return ResponseEntity.ok(watchlistService.addItem(id, listingId));
    }

    @DeleteMapping("/{id}/items/{listingId}")
    public ResponseEntity<Void> removeItem(@PathVariable("id") Long id, @PathVariable("listingId") Long listingId) {
        watchlistService.removeItem(id, listingId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/items")
    public ResponseEntity<List<WatchlistItemDto>> listItems(@PathVariable("id") Long id, @RequestParam(value = "type", required = false) String type) {
        return ResponseEntity.ok(watchlistService.listItems(id, type));
    }
}