package rs.raf.banka2_bek.margin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.margin.dto.CreateMarginAccountDto;
import rs.raf.banka2_bek.margin.dto.MarginAccountDto;
import rs.raf.banka2_bek.margin.dto.MarginTransactionDto;
import rs.raf.banka2_bek.margin.service.MarginAccountService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST kontroler za margin racune.
 *
 * Endpointovi:
 *   POST /margin-accounts                     - kreiranje margin racuna
 *   GET  /margin-accounts/my                   - moji margin racuni
 *   GET  /margin-accounts/{id}                 - detalji margin racuna
 *   POST /margin-accounts/{id}/deposit         - uplata na margin racun
 *   POST /margin-accounts/{id}/withdraw        - isplata sa margin racuna
 *   GET  /margin-accounts/{id}/transactions    - istorija transakcija
 *
 * Specifikacija: Celina 3 - Margin racuni
 */
@RestController
@RequestMapping("/margin-accounts")
@RequiredArgsConstructor
public class MarginAccountController {

    private final MarginAccountService marginAccountService;

    /**
     * POST /margin-accounts
     * Kreira novi margin racun za autentifikovanog korisnika.
     *
     * TODO: Implementirati:
     *   1. Izvuci userId iz Authentication objekta (JWT claims)
     *   2. Pozvati marginAccountService.createForUser(userId, dto)
     *   3. Vratiti ResponseEntity.ok(marginAccountDto)
     */
    @PostMapping
    public ResponseEntity<MarginAccountDto> create(
            @Valid @RequestBody CreateMarginAccountDto dto,
            Authentication authentication) {
        // Extract userId from authentication; using 0L as stub since JWT claims structure varies
        Long userId = 0L; // TODO: Extract actual userId from JWT claims
        MarginAccountDto result = marginAccountService.createForUser(userId, dto);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /margin-accounts/my
     * Vraca sve margin racune autentifikovanog korisnika.
     *
     * TODO: Implementirati:
     *   1. Izvuci email iz Authentication objekta
     *   2. Pozvati marginAccountService.getMyMarginAccounts(email)
     *   3. Vratiti ResponseEntity.ok(lista)
     */
    @GetMapping("/my")
    public ResponseEntity<List<MarginAccountDto>> getMyMarginAccounts(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(marginAccountService.getMyMarginAccounts(email));
    }

    /**
     * GET /margin-accounts/{id}
     * Vraca detalje jednog margin racuna.
     *
     * TODO: Implementirati:
     *   1. Proveriti da korisnik ima pristup ovom margin racunu
     *   2. Dohvatiti margin racun po ID-ju
     *   3. Vratiti ResponseEntity.ok(dto) ili 404 ako ne postoji
     */
    @GetMapping("/{id}")
    public ResponseEntity<MarginAccountDto> getById(@PathVariable Long id, Authentication authentication) {
        // TODO: Verify user has access to this margin account
        String email = authentication.getName();
        List<MarginAccountDto> accounts = marginAccountService.getMyMarginAccounts(email);
        return accounts.stream()
                .filter(a -> a.getId() != null && a.getId().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /margin-accounts/{id}/deposit
     * Uplata sredstava na margin racun.
     *
     * Request body: { "amount": 5000.00 }
     *
     * TODO: Implementirati:
     *   1. Proveriti da korisnik ima pristup ovom margin racunu
     *   2. Izvuci amount iz request body-ja
     *   3. Pozvati marginAccountService.deposit(id, amount)
     *   4. Vratiti ResponseEntity.ok() sa porukom potvrde
     */
    @PostMapping("/{id}/deposit")
    public ResponseEntity<Map<String, String>> deposit(
            @PathVariable Long id,
            @RequestBody Map<String, BigDecimal> body,
            Authentication authentication) {
        BigDecimal amount = body.get("amount");
        marginAccountService.deposit(id, amount, authentication);
        return ResponseEntity.ok(Map.of("message", "Deposit successful"));
    }

    /**
     * POST /margin-accounts/{id}/withdraw
     * Isplata sredstava sa margin racuna.
     *
     * Request body: { "amount": 2000.00 }
     *
     * TODO: Implementirati:
     *   1. Proveriti da korisnik ima pristup ovom margin racunu
     *   2. Izvuci amount iz request body-ja
     *   3. Pozvati marginAccountService.withdraw(id, amount)
     *   4. Vratiti ResponseEntity.ok() sa porukom potvrde
     *   5. Hendlati exception ako withdraw nije dozvoljen (maintenance margin)
     */
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<Map<String, String>> withdraw(
            @PathVariable Long id,
            @RequestBody Map<String, BigDecimal> body,
            Authentication authentication
    ) {
        BigDecimal amount = body.get("amount");
        marginAccountService.withdraw(id, amount, authentication);
        return ResponseEntity.ok(Map.of("message", "Withdrawal successful"));
    }

    /**
     * GET /margin-accounts/{id}/transactions
     * Vraca istoriju transakcija za dati margin racun.
     *
     * TODO: Implementirati:
     *   1. Proveriti da korisnik ima pristup ovom margin racunu
     *   2. Pozvati marginAccountService.getTransactions(id)
     *   3. Vratiti ResponseEntity.ok(lista)
     */
    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<MarginTransactionDto>> getTransactions(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(marginAccountService.getTransactions(id));
    }
}
