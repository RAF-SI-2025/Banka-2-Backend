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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.otp.service.OtpService;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentListItemDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.payment.service.PaymentService;
import rs.raf.banka2_bek.transaction.dto.TransactionListItemDto;
import rs.raf.banka2_bek.transaction.dto.TransactionType;

import java.math.BigDecimal;

@Tag(name = "Payments", description = "API for creating and browsing payments and payment history")
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final OtpService otpService;

    @Operation(summary = "Create payment", description = "Creates a new payment for the authenticated client.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment created", content = @Content(schema = @Schema(implementation = PaymentResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation or JSON parse error"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Business validation failed (e.g. account not found)")
    })
    @PostMapping
    public ResponseEntity<?> createPayment(
            @Valid @RequestBody CreatePaymentRequestDto request,
            org.springframework.security.core.Authentication auth) {
        String email = auth != null ? auth.getName() : null;
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // OTP verifikacija: verify atomicno proverava kod, inkrementuje attempts i markira used
        String otpCode = request.getOtpCode();
        if (otpCode == null || otpCode.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        java.util.Map<String, Object> verifyResult = otpService.verify(email, otpCode);
        if (!Boolean.TRUE.equals(verifyResult.get("verified"))) {
            // T2-012: ako je verifikacija otkazana zbog 3 neuspela pokusaja ili isteka,
            // perzistuj ABORTED placanje radi audit trail-a (spec §C2 Sc 14).
            if (Boolean.TRUE.equals(verifyResult.get("blocked"))) {
                String reason = String.valueOf(verifyResult.getOrDefault("message", "OTP otkazano"));
                Long abortedId = paymentService.recordAbortedPayment(request, reason);
                if (abortedId != null) {
                    java.util.Map<String, Object> withAudit = new java.util.LinkedHashMap<>(verifyResult);
                    withAudit.put("abortedPaymentId", abortedId);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(withAudit);
                }
            }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(verifyResult);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPayment(request));
    }

    @Operation(summary = "List payments", description = "Returns paginated payments for the authenticated client with optional filters.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated payments", content = @Content(schema = @Schema(implementation = PaymentListItemDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filter values"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping
    public ResponseEntity<Page<PaymentListItemDto>> getPayments(
            @Parameter(description = "Pagination and sorting")
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "Filter by createdAt >= fromDate (ISO-8601 date-time)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate fromDate,
            @Parameter(description = "Filter by createdAt <= toDate (ISO-8601 date-time)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate toDate,
            @Parameter(description = "Filter by account (matches fromAccount or toAccount)")
            @RequestParam(required = false) String accountNumber,
            @Parameter(description = "Minimum amount filter")
            @RequestParam(required = false) BigDecimal minAmount,
            @Parameter(description = "Maximum amount filter")
            @RequestParam(required = false) BigDecimal maxAmount,
            @Parameter(description = "Filter by payment status")
            @RequestParam(required = false) PaymentStatus status
    ) {
        java.time.LocalDateTime fromDateTime = fromDate != null ? fromDate.atStartOfDay() : null;
        java.time.LocalDateTime toDateTime = toDate != null ? toDate.atTime(23, 59, 59) : null;
        return ResponseEntity.ok(paymentService.getPayments(pageable, fromDateTime, toDateTime, accountNumber, minAmount, maxAmount, status));
    }

    @Operation(summary = "Get payment by ID", description = "Returns a payment if it belongs to the authenticated client.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment found", content = @Content(schema = @Schema(implementation = PaymentResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponseDto> getPaymentById(
            @Parameter(description = "Payment ID") @PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.getPaymentById(paymentId));
    }

    @Operation(summary = "Download transaction receipt PDF", description = "Generates and downloads a PDF receipt for the transaction identified by ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF receipt", content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Transaction not found or not owned by client")
    })
    @GetMapping(value = "/{paymentId}/receipt", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getPaymentReceipt(
            @Parameter(description = "Transaction ID used to generate receipt") @PathVariable Long paymentId) {
        byte[] pdf = paymentService.getPaymentReceipt(paymentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"transaction-receipt-" + paymentId + ".pdf\"")
                .body(pdf);
    }

    @Operation(summary = "List transaction history", description = "Returns paginated transaction history for the authenticated client with optional filters.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated transaction history", content = @Content(schema = @Schema(implementation = TransactionListItemDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filter values"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/history")
    public ResponseEntity<Page<TransactionListItemDto>> getPaymentHistory(
            @Parameter(description = "Pagination and sorting")
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "Filter by createdAt >= fromDate (ISO-8601 date-time)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate fromDate,
            @Parameter(description = "Filter by createdAt <= toDate (ISO-8601 date-time)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate toDate,
            @Parameter(description = "Minimum amount filter")
            @RequestParam(required = false) BigDecimal minAmount,
            @Parameter(description = "Maximum amount filter")
            @RequestParam(required = false) BigDecimal maxAmount,
            @Parameter(description = "Filter by transaction type")
            @RequestParam(required = false) TransactionType type
    ) {
        java.time.LocalDateTime fromDateTime = fromDate != null ? fromDate.atStartOfDay() : null;
        java.time.LocalDateTime toDateTime = toDate != null ? toDate.atTime(23, 59, 59) : null;
        return ResponseEntity.ok(paymentService.getPaymentHistory(pageable, fromDateTime, toDateTime, minAmount, maxAmount, type));
    }

    @Operation(summary = "Request OTP code", description = "Generates and sends a verification code to the authenticated user's email. If a payment payload is provided, BE preflights validacija (balans, limiti, primalac) PRE generisanja OTP-a — T2-009 fix.")
    @PostMapping("/request-otp")
    public ResponseEntity<java.util.Map<String, Object>> requestOtp(
            @RequestBody(required = false) CreatePaymentRequestDto request,
            org.springframework.security.core.Authentication auth) {
        String email = auth != null ? auth.getName() : null;
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // T2-009: ako FE salje payment payload, preflight balans/limiti/primalac
        // PRE generisanja OTP-a, da ne zatraži kod za nevazece placanje.
        if (request != null && request.getFromAccount() != null && request.getToAccount() != null
                && request.getAmount() != null) {
            try {
                paymentService.validatePayment(request);
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(java.util.Map.of(
                        "sent", false,
                        "message", ex.getMessage()));
            }
        }
        otpService.generateAndSend(email);
        return ResponseEntity.ok(java.util.Map.of(
                "sent", true,
                "message", "Verifikacioni kod je poslat na vas email"));
    }

    @Operation(summary = "Request OTP via email", description = "Generates OTP and sends it to the user's email as fallback when mobile app is not available.")
    @PostMapping("/request-otp-email")
    public ResponseEntity<java.util.Map<String, Object>> requestOtpViaEmail(
            org.springframework.security.core.Authentication auth) {
        String email = auth != null ? auth.getName() : null;
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        otpService.generateAndSendViaEmail(email);
        return ResponseEntity.ok(java.util.Map.of(
                "sent", true,
                "message", "Verifikacioni kod je poslat na vas email"));
    }

    @Operation(summary = "Get active OTP", description = "Returns the active OTP code for the authenticated user (used by mobile app).")
    @GetMapping("/my-otp")
    public ResponseEntity<java.util.Map<String, Object>> getMyOtp(
            org.springframework.security.core.Authentication auth) {
        String email = auth != null ? auth.getName() : null;
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(otpService.getActiveOtp(email));
    }

    @Operation(summary = "Verify OTP code", description = "Verifies the OTP code for payment confirmation.")
    @PostMapping("/verify")
    public ResponseEntity<java.util.Map<String, Object>> verifyPayment(
            @RequestBody java.util.Map<String, Object> request,
            org.springframework.security.core.Authentication auth) {
        String email = auth != null ? auth.getName() : null;
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String code = String.valueOf(request.getOrDefault("code", ""));
        return ResponseEntity.ok(otpService.verify(email, code));
    }
}
