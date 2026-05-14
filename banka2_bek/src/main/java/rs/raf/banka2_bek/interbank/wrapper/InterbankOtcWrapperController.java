package rs.raf.banka2_bek.interbank.wrapper;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserResolver;
import rs.raf.banka2_bek.interbank.wrapper.InterbankOtcWrapperDtos.CounterOtcInterbankOfferRequest;
import rs.raf.banka2_bek.interbank.wrapper.InterbankOtcWrapperDtos.CreateOtcInterbankOfferRequest;
import rs.raf.banka2_bek.interbank.wrapper.InterbankOtcWrapperDtos.OtcInterbankContract;
import rs.raf.banka2_bek.interbank.wrapper.InterbankOtcWrapperDtos.OtcInterbankListing;
import rs.raf.banka2_bek.interbank.wrapper.InterbankOtcWrapperDtos.OtcInterbankOffer;

import java.util.List;

/**
 * FE-facing rute za inter-bank OTC ({@code /interbank/otc/*}). Ovi endpoint-i
 * NE komuniciraju direktno sa partner bankama — to radi
 * {@link rs.raf.banka2_bek.interbank.service.OtcNegotiationService} (outbound)
 * preko {@link rs.raf.banka2_bek.interbank.service.InterbankClient}-a koji koristi
 * X-Api-Key autentifikaciju (§2.10). Ove rute koriste standardni JWT auth nase
 * banke (UserResolver).
 */
@RestController
@RequestMapping("/interbank/otc")
@RequiredArgsConstructor
public class InterbankOtcWrapperController {

    private final InterbankOtcWrapperService wrapperService;
    private final UserResolver userResolver;

    @GetMapping("/listings")
    public ResponseEntity<List<OtcInterbankListing>> listListings() {
        return ResponseEntity.ok(wrapperService.listRemoteListings());
    }

    @PostMapping("/offers")
    public ResponseEntity<OtcInterbankOffer> createOffer(
            @Valid @RequestBody CreateOtcInterbankOfferRequest request) {
        UserContext ctx = userResolver.resolveCurrent();
        return ResponseEntity.ok(wrapperService.createOffer(request,
                ctx.userId(), normalizeRole(ctx.userRole())));
    }

    @GetMapping("/offers/my")
    public ResponseEntity<List<OtcInterbankOffer>> listMyOffers() {
        UserContext ctx = userResolver.resolveCurrent();
        return ResponseEntity.ok(wrapperService.listMyOffers(ctx.userId(), normalizeRole(ctx.userRole())));
    }

    @PatchMapping("/offers/{offerId}/counter")
    public ResponseEntity<OtcInterbankOffer> counterOffer(
            @PathVariable String offerId,
            @Valid @RequestBody CounterOtcInterbankOfferRequest request) {
        UserContext ctx = userResolver.resolveCurrent();
        return ResponseEntity.ok(wrapperService.counterOffer(offerId, request,
                ctx.userId(), normalizeRole(ctx.userRole())));
    }

    @PatchMapping("/offers/{offerId}/decline")
    public ResponseEntity<OtcInterbankOffer> declineOffer(@PathVariable String offerId) {
        UserContext ctx = userResolver.resolveCurrent();
        return ResponseEntity.ok(wrapperService.declineOffer(offerId,
                ctx.userId(), normalizeRole(ctx.userRole())));
    }

    @PatchMapping("/offers/{offerId}/accept")
    public ResponseEntity<OtcInterbankOffer> acceptOffer(
            @PathVariable String offerId,
            @RequestParam(name = "accountId", required = false) Long accountId) {
        UserContext ctx = userResolver.resolveCurrent();
        return ResponseEntity.ok(wrapperService.acceptOffer(offerId, accountId,
                ctx.userId(), normalizeRole(ctx.userRole())));
    }

    @DeleteMapping("/offers/{offerId}")
    public ResponseEntity<OtcInterbankOffer> deleteOffer(@PathVariable String offerId) {
        UserContext ctx = userResolver.resolveCurrent();
        return ResponseEntity.ok(wrapperService.declineOffer(offerId,
                ctx.userId(), normalizeRole(ctx.userRole())));
    }

    @GetMapping("/contracts/my")
    public ResponseEntity<List<OtcInterbankContract>> listMyContracts(
            @RequestParam(name = "status", required = false) String status) {
        UserContext ctx = userResolver.resolveCurrent();
        return ResponseEntity.ok(wrapperService.listMyContracts(
                ctx.userId(), normalizeRole(ctx.userRole()), status));
    }

    @PostMapping("/contracts/{contractId}/exercise")
    public ResponseEntity<OtcInterbankContract> exerciseContract(
            @PathVariable String contractId,
            @RequestParam(name = "buyerAccountId", required = false) Long buyerAccountId) {
        UserContext ctx = userResolver.resolveCurrent();
        return ResponseEntity.ok(wrapperService.exerciseContract(contractId, buyerAccountId,
                ctx.userId(), normalizeRole(ctx.userRole())));
    }

    /**
     * JWT role je "CLIENT" / "ADMIN" / "EMPLOYEE". Za nasu konvenciju u
     * InterbankOtc* entitetima delimo na "CLIENT" / "EMPLOYEE" — admini i
     * supervizori se tretiraju kao EMPLOYEE.
     */
    private static String normalizeRole(String role) {
        if (role == null) {
            throw new AccessDeniedException("Korisnicka rola nije postavljena");
        }
        if ("CLIENT".equalsIgnoreCase(role)) return "CLIENT";
        return "EMPLOYEE";
    }
}
