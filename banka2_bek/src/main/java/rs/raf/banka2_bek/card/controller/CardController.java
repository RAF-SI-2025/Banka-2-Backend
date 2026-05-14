package rs.raf.banka2_bek.card.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.card.dto.CardLimitUpdateDto;
import rs.raf.banka2_bek.card.dto.CardResponseDto;
import rs.raf.banka2_bek.card.dto.CreateCardRequestDto;
import rs.raf.banka2_bek.card.model.CardRequest;
import rs.raf.banka2_bek.card.model.CardType;
import rs.raf.banka2_bek.card.repository.CardRequestRepository;
import rs.raf.banka2_bek.card.service.CardService;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "Cards", description = "Card management API")
@RestController
@RequestMapping("/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;
    private final CardRequestRepository cardRequestRepository;
    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;

    @Operation(summary = "Create card", description = "Client requests a new card for their account")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Card created"),
            @ApiResponse(responseCode = "400", description = "Max cards reached or invalid account")
    })
    @PostMapping
    public ResponseEntity<CardResponseDto> createCard(@Valid @RequestBody CreateCardRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cardService.createCard(request));
    }

    @Operation(summary = "Get my cards", description = "Returns all cards for authenticated client")
    @GetMapping
    public ResponseEntity<List<CardResponseDto>> getMyCards() {
        return ResponseEntity.ok(cardService.getMyCards());
    }

    @Operation(summary = "Get cards by account", description = "Returns all cards for a specific account (employee portal)")
    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<CardResponseDto>> getCardsByAccount(@PathVariable Long accountId) {
        return ResponseEntity.ok(cardService.getCardsByAccount(accountId));
    }

    @Operation(summary = "Block card", description = "Client blocks their own card")
    @PatchMapping("/{id}/block")
    public ResponseEntity<CardResponseDto> blockCard(@PathVariable Long id) {
        return ResponseEntity.ok(cardService.blockCard(id));
    }

    @Operation(summary = "Unblock card", description = "Employee unblocks a blocked card. Spec C2: klijent NE moze odblokirati, samo zaposleni.")
    @PatchMapping("/{id}/unblock")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<CardResponseDto> unblockCard(@PathVariable Long id) {
        return ResponseEntity.ok(cardService.unblockCard(id));
    }

    @Operation(summary = "Deactivate card", description = "Employee permanently deactivates a card")
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<CardResponseDto> deactivateCard(@PathVariable Long id) {
        return ResponseEntity.ok(cardService.deactivateCard(id));
    }

    @Operation(summary = "Update card limit")
    @PatchMapping("/{id}/limit")
    public ResponseEntity<CardResponseDto> updateCardLimit(
            @PathVariable Long id,
            @Valid @RequestBody CardLimitUpdateDto request) {
        return ResponseEntity.ok(cardService.updateCardLimit(id, request.getCardLimit()));
    }

    // ===== Card Requests (klijent podnosi zahtev, admin odobrava) =====

    @PostMapping("/requests")
    @Transactional
    public ResponseEntity<Map<String, Object>> submitCardRequest(@RequestBody Map<String, Object> body) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Client client = clientRepository.findByEmail(email).orElse(null);
        String clientName = client != null ? client.getFirstName() + " " + client.getLastName() : email;

        Long accountId = Long.valueOf(String.valueOf(body.get("accountId")));
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Racun nije pronadjen"));

        BigDecimal limit = body.get("cardLimit") != null ? new BigDecimal(String.valueOf(body.get("cardLimit"))) : BigDecimal.valueOf(100000);

        CardType requestedCardType = CardType.VISA;
        if (body.get("cardType") != null) {
            try {
                requestedCardType = CardType.valueOf(String.valueOf(body.get("cardType")));
            } catch (IllegalArgumentException ignored) {
                // default to VISA if invalid value
            }
        }

        CardRequest req = CardRequest.builder()
                .account(account)
                .cardLimit(limit)
                .cardType(requestedCardType)
                .clientEmail(email)
                .clientName(clientName)
                .status("PENDING")
                .build();
        req = cardRequestRepository.save(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", req.getId(), "accountId", accountId, "status", "PENDING",
                "clientName", clientName, "createdAt", req.getCreatedAt().toString()));
    }

    @GetMapping("/requests/my")
    public ResponseEntity<Page<Map<String, Object>>> getMyCardRequests(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int limit) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        var pageable = PageRequest.of(page, limit, Sort.by("createdAt").descending());
        return ResponseEntity.ok(cardRequestRepository.findByClientEmail(email, pageable).map(this::toCardRequestMap));
    }

    @GetMapping("/requests")
    public ResponseEntity<Page<Map<String, Object>>> getAllCardRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int limit) {
        var pageable = PageRequest.of(page, limit, Sort.by("createdAt").descending());
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(cardRequestRepository.findByStatus(status, pageable).map(this::toCardRequestMap));
        }
        return ResponseEntity.ok(cardRequestRepository.findAll(pageable).map(this::toCardRequestMap));
    }

    @PatchMapping("/requests/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @Transactional
    public ResponseEntity<Map<String, Object>> approveCardRequest(@PathVariable Long id) {
        CardRequest req = cardRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zahtev nije pronadjen"));
        if (!"PENDING".equals(req.getStatus())) throw new IllegalStateException("Zahtev je vec obradjen");

        Account account = req.getAccount();
        Client owner = account.getClient();
        if (owner == null) throw new IllegalStateException("Racun nema vlasnika");
        CardType cardType = req.getCardType() != null ? req.getCardType() : CardType.VISA;
        cardService.createCardForAccount(account.getId(), owner.getId(), req.getCardLimit(), cardType);

        String employeeEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        req.setStatus("APPROVED");
        req.setProcessedAt(LocalDateTime.now());
        req.setProcessedBy(employeeEmail);
        cardRequestRepository.save(req);
        return ResponseEntity.ok(toCardRequestMap(req));
    }

    @PatchMapping("/requests/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @Transactional
    public ResponseEntity<Map<String, Object>> rejectCardRequest(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        CardRequest req = cardRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zahtev nije pronadjen"));
        if (!"PENDING".equals(req.getStatus())) throw new IllegalStateException("Zahtev je vec obradjen");

        String employeeEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        req.setStatus("REJECTED");
        req.setRejectionReason(body != null ? body.getOrDefault("reason", null) : null);
        req.setProcessedAt(LocalDateTime.now());
        req.setProcessedBy(employeeEmail);
        cardRequestRepository.save(req);
        return ResponseEntity.ok(toCardRequestMap(req));
    }

    private Map<String, Object> toCardRequestMap(CardRequest req) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", req.getId());
        map.put("accountId", req.getAccount().getId());
        map.put("accountNumber", req.getAccount().getAccountNumber());
        map.put("cardLimit", req.getCardLimit());
        map.put("cardType", req.getCardType() != null ? req.getCardType().name() : null);
        map.put("clientEmail", req.getClientEmail());
        map.put("clientName", req.getClientName());
        map.put("status", req.getStatus());
        map.put("createdAt", req.getCreatedAt().toString());
        if (req.getProcessedAt() != null) {
            map.put("processedAt", req.getProcessedAt().toString());
        }
        if (req.getProcessedBy() != null) {
            map.put("processedBy", req.getProcessedBy());
        }
        if (req.getRejectionReason() != null) {
            map.put("rejectionReason", req.getRejectionReason());
        }
        return map;
    }
}
