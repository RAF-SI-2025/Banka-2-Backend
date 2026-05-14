package rs.raf.banka2_bek.otc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.otc.dto.CounterOtcOfferDto;
import rs.raf.banka2_bek.otc.dto.CreateOtcOfferDto;
import rs.raf.banka2_bek.otc.dto.OtcContractDto;
import rs.raf.banka2_bek.otc.dto.OtcListingDto;
import rs.raf.banka2_bek.otc.dto.OtcOfferDto;
import rs.raf.banka2_bek.otc.service.OtcService;

import java.util.List;

/**
 * Endpoint-i za OTC trgovinu (Celina 4 - intra-bank).
 *
 * Svi endpointi zahtevaju autentifikaciju. Pristup pojedinacnoj
 * ponudi/ugovoru proverava da li je trenutni korisnik ucesnik.
 */
@RestController
@RequestMapping("/otc")
@RequiredArgsConstructor
public class OtcController {

    private final OtcService otcService;

    @GetMapping("/listings")
    public ResponseEntity<List<OtcListingDto>> listDiscoveryListings() {
        return ResponseEntity.ok(otcService.listDiscoveryListings());
    }

    /**
     * Moje sopstvene javne akcije — portfolio item-i koje sam stavio u
     * javni rezim (publicQuantity > 0). User ne vidi svoje akcije u
     * Discovery-ju (linije 106-107 listDiscoveryListings filtriraju
     * `me.userId()`), pa ovaj endpoint daje vidljivost tom rezimu —
     * sta sam ja objavio za druge.
     */
    @GetMapping("/listings/my")
    public ResponseEntity<List<OtcListingDto>> listMyPublicListings() {
        return ResponseEntity.ok(otcService.listMyPublicListings());
    }

    @GetMapping("/offers/active")
    public ResponseEntity<List<OtcOfferDto>> listMyActiveOffers() {
        return ResponseEntity.ok(otcService.listMyActiveOffers());
    }

    @PostMapping("/offers")
    public ResponseEntity<OtcOfferDto> createOffer(@Valid @RequestBody CreateOtcOfferDto dto) {
        return ResponseEntity.ok(otcService.createOffer(dto));
    }

    @PostMapping("/offers/{offerId}/counter")
    public ResponseEntity<OtcOfferDto> counterOffer(@PathVariable Long offerId,
                                                    @Valid @RequestBody CounterOtcOfferDto dto) {
        return ResponseEntity.ok(otcService.counterOffer(offerId, dto));
    }

    @PostMapping("/offers/{offerId}/decline")
    public ResponseEntity<OtcOfferDto> declineOffer(@PathVariable Long offerId) {
        return ResponseEntity.ok(otcService.declineOffer(offerId));
    }

    @PostMapping("/offers/{offerId}/accept")
    public ResponseEntity<OtcOfferDto> acceptOffer(@PathVariable Long offerId,
                                                   @RequestParam(required = false) Long buyerAccountId) {
        return ResponseEntity.ok(otcService.acceptOffer(offerId, buyerAccountId));
    }

    @GetMapping("/contracts")
    public ResponseEntity<List<OtcContractDto>> listMyContracts(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(otcService.listMyContracts(status));
    }

    @PostMapping("/contracts/{contractId}/exercise")
    public ResponseEntity<OtcContractDto> exerciseContract(@PathVariable Long contractId,
                                                           @RequestParam(required = false) Long buyerAccountId) {
        return ResponseEntity.ok(otcService.exerciseContract(contractId, buyerAccountId));
    }
}
