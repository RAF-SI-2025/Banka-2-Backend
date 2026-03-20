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
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentListItemDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.payment.service.PaymentService;
import rs.raf.banka2_bek.transaction.dto.TransactionListItemDto;
import rs.raf.banka2_bek.transaction.dto.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Tag(name = "Payments", description = "API for creating and browsing payments and payment history")
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Create payment", description = "Creates a new payment for the authenticated client.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment created", content = @Content(schema = @Schema(implementation = PaymentResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation or JSON parse error"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Business validation failed (e.g. account not found)")
    })
    @PostMapping
    public ResponseEntity<PaymentResponseDto> createPayment(@Valid @RequestBody CreatePaymentRequestDto request) {
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @Parameter(description = "Filter by createdAt <= toDate (ISO-8601 date-time)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @Parameter(description = "Minimum amount filter")
            @RequestParam(required = false) BigDecimal minAmount,
            @Parameter(description = "Maximum amount filter")
            @RequestParam(required = false) BigDecimal maxAmount,
            @Parameter(description = "Filter by payment status")
            @RequestParam(required = false) PaymentStatus status
    ) {
        return ResponseEntity.ok(paymentService.getPayments(pageable, fromDate, toDate, minAmount, maxAmount, status));
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @Parameter(description = "Filter by createdAt <= toDate (ISO-8601 date-time)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @Parameter(description = "Minimum amount filter")
            @RequestParam(required = false) BigDecimal minAmount,
            @Parameter(description = "Maximum amount filter")
            @RequestParam(required = false) BigDecimal maxAmount,
            @Parameter(description = "Filter by transaction type")
            @RequestParam(required = false) TransactionType type
    ) {
        return ResponseEntity.ok(paymentService.getPaymentHistory(pageable, fromDate, toDate, minAmount, maxAmount, type));
    }

    // FIXME: Ukloniti hardcoded OTP kod 1234 kada bude gotova Android aplikacija
    // Za sada prihvata bilo koji kod ili hardcoded 1234 za testiranje
    @PostMapping("/verify")
    public ResponseEntity<java.util.Map<String, Object>> verifyPayment(@RequestBody java.util.Map<String, Object> request) {
        String code = String.valueOf(request.getOrDefault("code", ""));
        if (!"1234".equals(code) && code.length() != 4) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "Neispravan verifikacioni kod"));
        }
        return ResponseEntity.ok(java.util.Map.of("verified", true, "message", "Transakcija uspesno verifikovana"));
    }
}
