package rs.raf.banka2_bek.stock.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.auth.dto.MessageResponseDto;
import rs.raf.banka2_bek.stock.dto.ListingDailyPriceDto;
import rs.raf.banka2_bek.stock.dto.ListingDto;
import rs.raf.banka2_bek.stock.service.ListingService;

import java.util.List;

@Tag(name = "Listings", description = "Hartije od vrednosti — pretraga, detalji i osvezavanje cena")
@RestController
@RequestMapping("/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;

    @Operation(summary = "Paged listing search by type and optional keyword",
               description = "Filtrira hartije po tipu (STOCK, FOREX, FUTURES) i opcionalnom pojmu koji se poklapa sa ticker-om ili nazivom (case-insensitive). Klijenti ne mogu da pristupe FOREX hartijama.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Uspesno vracena stranica hartija"),
            @ApiResponse(responseCode = "400", description = "Nepoznat tip hartije",
                    content = @Content(schema = @Schema(implementation = MessageResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Klijent pokusao da pristupi FOREX hartijama, message: \"Klijenti nemaju pristup FOREX hartijama.\" ",
                    content = @Content(schema = @Schema(implementation = MessageResponseDto.class)))
    })
    @GetMapping
    public ResponseEntity<Page<ListingDto>> getListings(
            @RequestParam(defaultValue = "STOCK") String type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(listingService.getListings(type, search, page, size));
    }


    @Operation(summary = "Get listing by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hartija pronadjena"),
            @ApiResponse(responseCode = "404", description = "Hartija nije pronadjena",
                    content = @Content(schema = @Schema(implementation = MessageResponseDto.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ListingDto> getListingById(@PathVariable Long id) {
        return ResponseEntity.ok(listingService.getListingById(id));
    }

    @Operation(summary = "Get price history for a listing",
               description = "Vraca istorijske dnevne cene za grafik. Parametar period: DAY, WEEK, MONTH, YEAR, FIVE_YEARS, ALL")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Istorija cena uspesno vracena"),
            @ApiResponse(responseCode = "404", description = "Hartija nije pronadjena",
                    content = @Content(schema = @Schema(implementation = MessageResponseDto.class)))
    })
    @GetMapping("/{id}/history")
    public ResponseEntity<List<ListingDailyPriceDto>> getListingHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "MONTH") String period) {
        return ResponseEntity.ok(listingService.getListingHistory(id, period));
    }

    @Operation(summary = "Manually refresh all listing prices",
               description = "Rucno osvezava cene svih hartija. Dostupno samo zaposlenima.")
    @ApiResponse(responseCode = "200", description = "Cene uspesno osvezene")
    @PostMapping("/refresh")
    public ResponseEntity<Void> refreshPrices() {
        listingService.refreshPrices();
        return ResponseEntity.ok().build();
    }
}
