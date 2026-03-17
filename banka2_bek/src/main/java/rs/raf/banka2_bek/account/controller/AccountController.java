package rs.raf.banka2_bek.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rs.raf.banka2_bek.account.dto.AccountResponseDto;
import rs.raf.banka2_bek.account.service.AccountService;

import java.util.List;

@Tag(name = "Account", description = "Client account viewing API")
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;


    @Operation(summary = "Get my accounts", description = "Returns a list of active accounts for the currently authenticated client, "
                    + "sorted by available balance in descending order."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of active accounts"),
            @ApiResponse(responseCode = "401", description = "User not authenticated"),
            @ApiResponse(responseCode = "404", description = "Client not found")
    })
    @GetMapping("/my")
    public ResponseEntity<List<AccountResponseDto>> getMyAccounts() {
        return ResponseEntity.ok(accountService.getMyAccounts());
    }

    @Operation(summary = "Get account by ID", description = "Returns detailed information about a single account. Only account owner can access it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account details", content = @Content(schema = @Schema(implementation = AccountResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "User not authenticated"),
            @ApiResponse(responseCode = "403", description = "User is not the owner of this account"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponseDto> getAccountById(
            @Parameter(description = "Account ID") @PathVariable Long id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }
}
