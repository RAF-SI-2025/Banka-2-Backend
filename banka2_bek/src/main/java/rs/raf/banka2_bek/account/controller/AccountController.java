package rs.raf.banka2_bek.account.controller;

import rs.raf.banka2_bek.account.dto.*;
import rs.raf.banka2_bek.account.model.AccountRequest;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.model.AccountSubtype;
import rs.raf.banka2_bek.account.repository.AccountRequestRepository;
import rs.raf.banka2_bek.account.service.AccountService;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.List;

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
import org.springframework.web.bind.annotation.*;

@Tag(name = "Account", description = "Client account viewing API")
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AccountRequestRepository accountRequestRepository;
    private final ClientRepository clientRepository;
    private final CurrencyRepository currencyRepository;

    @Operation(summary = "Create account", description = "Employee creates a new checking or foreign currency account for a client or company")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping
    public ResponseEntity<AccountResponseDto> createAccount(@Valid @RequestBody CreateAccountDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }

    @Operation(summary = "Get all accounts (employee portal)", description = "Returns paginated list of all accounts with optional owner filter")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated accounts")
    })
    @GetMapping("/all")
    public ResponseEntity<Page<AccountResponseDto>> getAllAccounts(
            @Parameter(description = "Page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Filter by owner name") @RequestParam(required = false) String ownerName) {
        return ResponseEntity.ok(accountService.getAllAccounts(page, limit, ownerName));
    }

    @Operation(summary = "Get accounts by client ID (employee portal)")
    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<AccountResponseDto>> getAccountsByClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(accountService.getAccountsByClient(clientId));
    }

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

    // name change
    @Operation(
            summary = "Update account name",
            description = "Changes the display name of an account. "
                    + "New name must differ from the current one and be unique among the client's accounts."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account name updated",
                    content = @Content(schema = @Schema(implementation = AccountResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "403", description = "User is not the owner or name is invalid"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @PatchMapping("/{id}/name")
    public ResponseEntity<AccountResponseDto> updateAccountName(
            @Parameter(description = "Account ID") @PathVariable Long id,
            @Valid @RequestBody AccountNameUpdateDto request) {
        return ResponseEntity.ok(accountService.updateAccountName(id, request.getName()));
    }

    // limit change
    @Operation(
            summary = "Update account limits",
            description = "Changes the daily and/or monthly spending limits for an account. "
                    + "Only the account owner can change limits. "
                    + "Requires transaction verification (mobile app) — currently not implemented."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Limits updated",
                    content = @Content(schema = @Schema(implementation = AccountResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "403", description = "User is not the owner of this account"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @PatchMapping("/{id}/limits")
    public ResponseEntity<AccountResponseDto> updateAccountLimits(
            @Parameter(description = "Account ID") @PathVariable Long id,
            @Valid @RequestBody AccountLimitsUpdateDto request) {
        return ResponseEntity.ok(accountService.updateAccountLimits(
                id, request.getDailyLimit(), request.getMonthlyLimit()));
    }

    // ===== Account Requests (klijent podnosi zahtev, admin odobrava) =====

    @PostMapping("/requests")
    public ResponseEntity<AccountRequestResponseDto> submitAccountRequest(@RequestBody AccountRequestDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Client client = clientRepository.findByEmail(email).orElse(null);
        String clientName = client != null ? client.getFirstName() + " " + client.getLastName() : email;

        var currency = currencyRepository.findByCode(dto.getCurrency() != null ? dto.getCurrency() : "RSD")
                .orElseThrow(() -> new IllegalArgumentException("Nepoznata valuta: " + dto.getCurrency()));

        AccountRequest req = AccountRequest.builder()
                .accountType(AccountType.valueOf(dto.getAccountType()))
                .accountSubtype(dto.getAccountSubtype() != null ? AccountSubtype.valueOf(dto.getAccountSubtype()) : null)
                .currency(currency)
                .initialDeposit(dto.getInitialDeposit())
                .createCard(dto.getCreateCard())
                .clientEmail(email)
                .clientName(clientName)
                .status("PENDING")
                .build();
        req = accountRequestRepository.save(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(toRequestResponse(req));
    }

    @GetMapping("/requests/my")
    public ResponseEntity<Page<AccountRequestResponseDto>> getMyAccountRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        var pageable = PageRequest.of(page, limit, Sort.by("createdAt").descending());
        return ResponseEntity.ok(accountRequestRepository.findByClientEmail(email, pageable).map(this::toRequestResponse));
    }

    @GetMapping("/requests")
    public ResponseEntity<Page<AccountRequestResponseDto>> getAllAccountRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit) {
        var pageable = PageRequest.of(page, limit, Sort.by("createdAt").descending());
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(accountRequestRepository.findByStatus(status, pageable).map(this::toRequestResponse));
        }
        return ResponseEntity.ok(accountRequestRepository.findAll(pageable).map(this::toRequestResponse));
    }

    @PatchMapping("/requests/{id}/approve")
    public ResponseEntity<AccountRequestResponseDto> approveAccountRequest(@PathVariable Long id) {
        AccountRequest req = accountRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zahtev nije pronadjen"));
        if (!"PENDING".equals(req.getStatus())) {
            throw new IllegalStateException("Zahtev je vec obradjen");
        }
        String employeeEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        // Kreiraj racun
        CreateAccountDto createDto = new CreateAccountDto();
        createDto.setAccountType(req.getAccountType());
        createDto.setAccountSubtype(req.getAccountSubtype());
        createDto.setCurrency(req.getCurrency().getCode());
        createDto.setInitialDeposit(req.getInitialDeposit() != null ? req.getInitialDeposit().doubleValue() : 0.0);
        createDto.setOwnerEmail(req.getClientEmail());
        createDto.setCreateCard(req.getCreateCard() != null && req.getCreateCard());
        accountService.createAccount(createDto);

        req.setStatus("APPROVED");
        req.setProcessedAt(LocalDateTime.now());
        req.setProcessedBy(employeeEmail);
        accountRequestRepository.save(req);
        return ResponseEntity.ok(toRequestResponse(req));
    }

    @PatchMapping("/requests/{id}/reject")
    public ResponseEntity<AccountRequestResponseDto> rejectAccountRequest(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        AccountRequest req = accountRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zahtev nije pronadjen"));
        if (!"PENDING".equals(req.getStatus())) {
            throw new IllegalStateException("Zahtev je vec obradjen");
        }
        String employeeEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        req.setStatus("REJECTED");
        req.setRejectionReason(body != null ? body.getOrDefault("reason", null) : null);
        req.setProcessedAt(LocalDateTime.now());
        req.setProcessedBy(employeeEmail);
        accountRequestRepository.save(req);
        return ResponseEntity.ok(toRequestResponse(req));
    }

    private AccountRequestResponseDto toRequestResponse(AccountRequest req) {
        return AccountRequestResponseDto.builder()
                .id(req.getId())
                .accountType(req.getAccountType().name())
                .accountSubtype(req.getAccountSubtype() != null ? req.getAccountSubtype().name() : null)
                .currency(req.getCurrency().getCode())
                .initialDeposit(req.getInitialDeposit())
                .createCard(req.getCreateCard())
                .clientEmail(req.getClientEmail())
                .clientName(req.getClientName())
                .status(req.getStatus())
                .rejectionReason(req.getRejectionReason())
                .createdAt(req.getCreatedAt())
                .processedAt(req.getProcessedAt())
                .processedBy(req.getProcessedBy())
                .build();
    }
}
