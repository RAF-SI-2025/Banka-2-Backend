package rs.raf.banka2_bek.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rs.raf.banka2_bek.account.dto.CreateAccountDto;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.service.AccountService;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@Tag(name = "Account Controller", description = "Endpointi za upravljanje računima klijenata")
public class AccountController {

    private final AccountService accountService;

    @Operation(
            summary = "Kreiranje novog računa",
            description = "Zaposleni kreira tekući ili devizni račun za klijenta ili firmu. " +
                    "Ako je račun poslovni, obavezno je poslati podatke o firmi."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Račun uspešno kreiran"),
            @ApiResponse(responseCode = "400", description = "Neispravni podaci (npr. nedostaje valuta ili vlasnik)"),
            @ApiResponse(responseCode = "401", description = "Niste autorizovani (nedostaje ili je neispravan JWT)"),
            @ApiResponse(responseCode = "403", description = "Nemate dozvolu za ovu operaciju")
    })
    @PostMapping
    public ResponseEntity<Account> createAccount(@Valid @RequestBody CreateAccountDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(dto));
    }
}
