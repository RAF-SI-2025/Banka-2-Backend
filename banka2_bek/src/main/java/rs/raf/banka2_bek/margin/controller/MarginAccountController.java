package rs.raf.banka2_bek.margin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.margin.dto.CreateMarginAccountDto;
import rs.raf.banka2_bek.margin.dto.MarginAccountDto;
import rs.raf.banka2_bek.margin.dto.MarginTransactionDto;
import rs.raf.banka2_bek.margin.service.MarginAccountService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST kontroler za margin racune.
 * Endpointovi:
 *   POST /margin-accounts                     - kreiranje margin racuna
 *   GET  /margin-accounts/my                   - moji margin racuni
 *   GET  /margin-accounts/{id}                 - detalji margin racuna
 *   POST /margin-accounts/{id}/deposit         - uplata na margin racun
 *   POST /margin-accounts/{id}/withdraw        - isplata sa margin racuna
 *   GET  /margin-accounts/{id}/transactions    - istorija transakcija
 * Specifikacija: Celina 3 - Margin racuni
 */
@Tag(name = "Margin Account", description = "Margin account management API")
@RestController
@RequestMapping("/margin-accounts")
@RequiredArgsConstructor
public class MarginAccountController {

    private final MarginAccountService marginAccountService;
    private final ClientRepository clientRepository;

    /**
     * POST /margin-accounts
     * Kreira novi margin racun za autentifikovanog korisnika.
     */
    @PostMapping
    public ResponseEntity<MarginAccountDto> create(
            @Valid @RequestBody CreateMarginAccountDto dto,
            Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Authenticated user is required.");
        }
        Long userId = clientRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Only clients can create margin accounts."))
                .getId();

        MarginAccountDto result = marginAccountService.createForUser(userId, dto);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /margin-accounts/my
     * Vraca sve margin racune autentifikovanog korisnika.
     */
    @GetMapping("/my")
    public ResponseEntity<List<MarginAccountDto>> getMyMarginAccounts(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(marginAccountService.getMyMarginAccounts(email));
    }

    /**
     * GET /margin-accounts/{id}
     * Vraca detalje jednog margin racuna.
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
     * Request body: { "amount": 5000.00 }
     * TODO: Implementirati:
     *   1. Proveriti da korisnik ima pristup ovom margin racunu
     *   2. Izvuci amount iz request body-ja
     *   3. Pozvati marginAccountService.deposit(id, amount)
     *   4. Vratiti ResponseEntity.ok() sa porukom potvrde
     */
    @Operation(summary = "Deposit funds", description = "Deposits funds into a margin account. Unblocks the account if it was blocked. Only the account owner can deposit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deposit successful"),
            @ApiResponse(responseCode = "400", description = "Amount is missing, zero, or negative"),
            @ApiResponse(responseCode = "403", description = "Not authenticated, caller is not a client, or not the account owner"),
            @ApiResponse(responseCode = "404", description = "Margin account not found")
    })
    @PostMapping("/{id}/deposit")
    public ResponseEntity<Map<String, String>> deposit(
            @Parameter(description = "Margin account ID") @PathVariable Long id,
            @RequestBody Map<String, BigDecimal> body,
            Authentication authentication) {
        BigDecimal amount = body.get("amount");
        marginAccountService.deposit(id, amount, authentication);
        return ResponseEntity.ok(Map.of("message", "Deposit successful"));
    }

    /**
     * POST /margin-accounts/{id}/withdraw
     * Isplata sredstava sa margin racuna.
     * Request body: { "amount": 2000.00 }
     * TODO: Implementirati:
     *   1. Proveriti da korisnik ima pristup ovom margin racunu
     *   2. Izvuci amount iz request body-ja
     *   3. Pozvati marginAccountService.withdraw(id, amount)
     *   4. Vratiti ResponseEntity.ok() sa porukom potvrde
     *   5. Hendlati exception ako withdraw nije dozvoljen (maintenance margin)
     */
    @Operation(summary = "Withdraw funds", description = "Withdraws funds from a margin account. The account must be active and the remaining balance must not fall below the maintenance margin. Only the account owner can withdraw.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Withdrawal successful"),
            @ApiResponse(responseCode = "400", description = "Amount is missing, zero, negative, or withdrawal would drop below maintenance margin"),
            @ApiResponse(responseCode = "403", description = "Not authenticated, caller is not a client, not the account owner, or account is not active"),
            @ApiResponse(responseCode = "404", description = "Margin account not found")
    })
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<Map<String, String>> withdraw(
            @Parameter(description = "Margin account ID") @PathVariable Long id,
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
     * TODO: Implementirati:
     *   1. Proveriti da korisnik ima pristup ovom margin racunu
     *   2. Pozvati marginAccountService.getTransactions(id)
     *   3. Vratiti ResponseEntity.ok(lista)
     */
    @Operation(summary = "Get transaction history", description = "Returns the transaction history for a margin account, sorted newest first. Only the account owner can view transactions.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of transactions",
                    content = @Content(schema = @Schema(implementation = MarginTransactionDto.class))),
            @ApiResponse(responseCode = "403", description = "Not authenticated, caller is not a client, or not the account owner"),
            @ApiResponse(responseCode = "404", description = "Margin account not found")
    })
    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<MarginTransactionDto>> getTransactions(
            @Parameter(description = "Margin account ID") @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(marginAccountService.getTransactions(id, authentication));
    }
}
