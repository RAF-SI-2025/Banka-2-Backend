package rs.raf.banka2_bek.stock.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.stock.dto.ListingDailyPriceDto;
import rs.raf.banka2_bek.stock.dto.ListingDto;
import rs.raf.banka2_bek.stock.service.ListingService;

import java.util.List;

/**
 * Controller za hartije od vrednosti.
 * Pristup: aktuari (sve hartije) i klijenti sa permisijom (samo STOCK i FUTURES).
 *
 * TODO: Dodati u GlobalSecurityConfig:
 *   .requestMatchers("/listings/**").hasAnyRole("ADMIN", "CLIENT", "EMPLOYEE")
 *   Dodatna provera u servisu: klijent vidi samo STOCK i FUTURES
 */
@RestController
@RequestMapping("/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;

    /**
     * GET /listings?type=STOCK&search=AAPL&page=0&size=20
     * Vraca stranicu hartija filtrirano po tipu i pretrazi.
     */
    @GetMapping
    public ResponseEntity<Page<ListingDto>> getListings(
            @RequestParam(defaultValue = "STOCK") String type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(listingService.getListings(type, search, page, size));
    }

    /**
     * GET /listings/{id} - Detalji jedne hartije
     */
    @GetMapping("/{id}")
    public ResponseEntity<ListingDto> getListingById(@PathVariable Long id) {
        return ResponseEntity.ok(listingService.getListingById(id));
    }

    /**
     * GET /listings/{id}/history?period=MONTH
     * Vraca istorijske cene za grafik.
     * period: DAY, WEEK, MONTH, YEAR, FIVE_YEARS, ALL
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<ListingDailyPriceDto>> getListingHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "MONTH") String period) {
        return ResponseEntity.ok(listingService.getListingHistory(id, period));
    }

    /**
     * POST /listings/refresh - Rucno osvezavanje cena
     * Samo za aktuare/admine.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refreshPrices() {
        listingService.refreshPrices();
        return ResponseEntity.ok().build();
    }
}
