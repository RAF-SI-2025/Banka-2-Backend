package rs.raf.banka2_bek.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRecipientRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentRecipientResponseDto;
import rs.raf.banka2_bek.payment.dto.UpdatePaymentRecipientRequestDto;
import rs.raf.banka2_bek.payment.service.PaymentRecipientService;

@Tag(name = "Payment recipients", description = "API for payment recipients (list, add, update, delete)")
@RestController
@RequestMapping("/payment-recipients")
@RequiredArgsConstructor
public class PaymentRecipientController {

    private final PaymentRecipientService paymentRecipientService;

    private static String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("User not authenticated.");
        }
        return auth.getName();
    }

    @Operation(summary = "List payment recipients (paginated)", description = "Returns a paginated list of payment recipients for the currently logged-in client.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of recipients (content, totalElements, totalPages, etc.)", content = @Content(schema = @Schema(implementation = PaymentRecipientResponseDto.class)))
    })
    @GetMapping
    public ResponseEntity<Page<PaymentRecipientResponseDto>> getPaymentRecipients(
            @Parameter(description = "Page index (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(paymentRecipientService.getPaymentRecipients(getCurrentUserEmail(), page, limit));
    }

    @Operation(summary = "Add payment recipient", description = "Creates a new payment recipient for the current client (name and account number).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Recipient created", content = @Content(schema = @Schema(implementation = PaymentRecipientResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    @PostMapping
    public ResponseEntity<PaymentRecipientResponseDto> createPaymentRecipient(
            @Valid @RequestBody CreatePaymentRecipientRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentRecipientService.createPaymentRecipient(request, getCurrentUserEmail()));
    }

    @Operation(summary = "Update payment recipient", description = "Updates an existing payment recipient by ID (only if it belongs to the current client).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipient updated", content = @Content(schema = @Schema(implementation = PaymentRecipientResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Recipient not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<PaymentRecipientResponseDto> updatePaymentRecipient(
            @Parameter(description = "Payment recipient ID") @PathVariable Long id,
            @Valid @RequestBody UpdatePaymentRecipientRequestDto request) {
        return ResponseEntity.ok(paymentRecipientService.updatePaymentRecipient(id, request, getCurrentUserEmail()));
    }

    @Operation(summary = "Delete payment recipient", description = "Deletes a payment recipient by ID (only if it belongs to the current client).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Recipient deleted"),
            @ApiResponse(responseCode = "404", description = "Recipient not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePaymentRecipient(
            @Parameter(description = "Payment recipient ID") @PathVariable Long id) {
        paymentRecipientService.deletePaymentRecipient(id, getCurrentUserEmail());
        return ResponseEntity.noContent().build();
    }
}
