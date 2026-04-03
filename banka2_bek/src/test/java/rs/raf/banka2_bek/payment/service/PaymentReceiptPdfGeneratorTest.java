package rs.raf.banka2_bek.payment.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rs.raf.banka2_bek.transaction.dto.TransactionResponseDto;
import rs.raf.banka2_bek.transaction.dto.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PaymentReceiptPdfGeneratorTest {

    private PaymentReceiptPdfGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new PaymentReceiptPdfGenerator();
    }

    // ---- Helper to extract text from generated PDF bytes ----
    private String extractPdfText(byte[] pdfBytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    // ================================================================
    // 1. Completed outgoing payment
    // ================================================================
    @Test
    void generate_completedOutgoingPayment_containsAllFields() throws Exception {
        TransactionResponseDto dto = TransactionResponseDto.builder()
                .id(101L)
                .accountNumber("265-0000000012345-78")
                .toAccountNumber("265-0000000098765-43")
                .currencyCode("RSD")
                .description("Uplata za racun 2025-03")
                .type(TransactionType.PAYMENT)
                .debit(new BigDecimal("15000.50"))
                .credit(BigDecimal.ZERO)
                .createdAt(LocalDateTime.of(2026, 3, 15, 14, 30, 0))
                .build();

        byte[] pdf = generator.generate(dto);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);

        String text = extractPdfText(pdf);
        assertTrue(text.contains("Transaction Receipt"));
        assertTrue(text.contains("101"));
        assertTrue(text.contains("2026-03-15 14:30:00"));
        assertTrue(text.contains("PAYMENT"));
        assertTrue(text.contains("OUTGOING"));
        assertTrue(text.contains("265-0000000012345-78"));
        assertTrue(text.contains("265-0000000098765-43"));
        assertTrue(text.contains("15000.5"));
        assertTrue(text.contains("RSD"));
        assertTrue(text.contains("Uplata za racun 2025-03"));
    }

    // ================================================================
    // 2. Incoming payment (credit > 0, debit = 0)
    // ================================================================
    @Test
    void generate_incomingPayment_directionIsIncoming() throws Exception {
        TransactionResponseDto dto = TransactionResponseDto.builder()
                .id(202L)
                .accountNumber("265-0000000098765-43")
                .toAccountNumber("265-0000000012345-78")
                .currencyCode("EUR")
                .description("Incoming transfer")
                .type(TransactionType.TRANSFER)
                .debit(BigDecimal.ZERO)
                .credit(new BigDecimal("500.00"))
                .createdAt(LocalDateTime.of(2026, 1, 10, 9, 0, 0))
                .build();

        byte[] pdf = generator.generate(dto);
        String text = extractPdfText(pdf);

        assertTrue(text.contains("INCOMING"));
        assertTrue(text.contains("TRANSFER"));
        assertTrue(text.contains("500"));
        assertTrue(text.contains("EUR"));
    }

    // ================================================================
    // 3. Cross-currency payment (different from/to currency noted in description)
    // ================================================================
    @Test
    void generate_crossCurrencyPayment_containsCurrencyAndAmounts() throws Exception {
        TransactionResponseDto dto = TransactionResponseDto.builder()
                .id(303L)
                .accountNumber("265-1111111111111-11")
                .toAccountNumber("265-2222222222222-22")
                .currencyCode("USD")
                .description("Exchange RSD -> USD")
                .type(TransactionType.PAYMENT)
                .debit(new BigDecimal("117.25"))
                .credit(BigDecimal.ZERO)
                .createdAt(LocalDateTime.of(2026, 2, 20, 16, 45, 30))
                .build();

        byte[] pdf = generator.generate(dto);
        String text = extractPdfText(pdf);

        assertTrue(text.contains("303"));
        assertTrue(text.contains("USD"));
        assertTrue(text.contains("117.25"));
        assertTrue(text.contains("Exchange RSD -> USD"));
    }

    // ================================================================
    // 4. Null fields: null date, null type, null description, null debit/credit
    // ================================================================
    @Test
    void generate_nullFields_usesDefaultPlaceholders() throws Exception {
        TransactionResponseDto dto = TransactionResponseDto.builder()
                .id(404L)
                .accountNumber(null)
                .toAccountNumber(null)
                .currencyCode(null)
                .description(null)
                .type(null)
                .debit(null)
                .credit(null)
                .createdAt(null)
                .build();

        byte[] pdf = generator.generate(dto);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);

        String text = extractPdfText(pdf);
        // Null fields should produce "-" placeholders
        assertTrue(text.contains("Transaction Receipt"));
        assertTrue(text.contains("404"));
        // Date, type, account, toAccount, currency, description should all be "-"
        // Direction: debit is null -> not positive -> "INCOMING"
        assertTrue(text.contains("INCOMING"));
        // Amount: debit not positive, credit is null -> "0"
        assertTrue(text.contains("0"));
    }

    // ================================================================
    // 5. Blank/empty string fields → should produce "-"
    // ================================================================
    @Test
    void generate_blankStringFields_usesPlaceholder() throws Exception {
        TransactionResponseDto dto = TransactionResponseDto.builder()
                .id(505L)
                .accountNumber("   ")
                .toAccountNumber("")
                .currencyCode("")
                .description("  ")
                .type(TransactionType.PAYMENT)
                .debit(new BigDecimal("100"))
                .credit(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();

        byte[] pdf = generator.generate(dto);
        String text = extractPdfText(pdf);

        // All blank strings should resolve to "-"
        // From account, to account, currency, description should all be "-"
        assertTrue(text.contains("-"));
        assertTrue(text.contains("505"));
        assertTrue(text.contains("OUTGOING"));
    }

    // ================================================================
    // 6. Very long description text
    // ================================================================
    @Test
    void generate_veryLongDescription_doesNotThrow() throws Exception {
        String longDesc = "A".repeat(500);
        TransactionResponseDto dto = TransactionResponseDto.builder()
                .id(606L)
                .accountNumber("265-0000000000001-01")
                .toAccountNumber("265-0000000000002-02")
                .currencyCode("RSD")
                .description(longDesc)
                .type(TransactionType.PAYMENT)
                .debit(new BigDecimal("1.00"))
                .credit(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();

        byte[] pdf = assertDoesNotThrow(() -> generator.generate(dto));
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    // ================================================================
    // 7. Special characters in description
    // ================================================================
    @Test
    void generate_specialCharactersInDescription_doesNotThrow() throws Exception {
        TransactionResponseDto dto = TransactionResponseDto.builder()
                .id(707L)
                .accountNumber("265-0000000000001-01")
                .toAccountNumber("265-0000000000002-02")
                .currencyCode("RSD")
                .description("Payment for invoice #123 & receipt @2026 <test>")
                .type(TransactionType.PAYMENT)
                .debit(new BigDecimal("250.00"))
                .credit(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();

        byte[] pdf = assertDoesNotThrow(() -> generator.generate(dto));
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);

        String text = extractPdfText(pdf);
        assertTrue(text.contains("707"));
    }

    // ================================================================
    // 8. Debit with zero value → direction should be INCOMING
    // ================================================================
    @Test
    void generate_debitZero_creditPositive_directionIncoming() throws Exception {
        TransactionResponseDto dto = TransactionResponseDto.builder()
                .id(808L)
                .accountNumber("265-0000000000001-01")
                .toAccountNumber("265-0000000000002-02")
                .currencyCode("RSD")
                .description("Refund")
                .type(TransactionType.PAYMENT)
                .debit(BigDecimal.ZERO)
                .credit(new BigDecimal("9999.99"))
                .createdAt(LocalDateTime.now())
                .build();

        byte[] pdf = generator.generate(dto);
        String text = extractPdfText(pdf);

        assertTrue(text.contains("INCOMING"));
        assertTrue(text.contains("9999.99"));
    }

    // ================================================================
    // 9. Large amount with trailing zeros stripped
    // ================================================================
    @Test
    void generate_trailingZerosStripped() throws Exception {
        TransactionResponseDto dto = TransactionResponseDto.builder()
                .id(909L)
                .accountNumber("265-0000000000001-01")
                .toAccountNumber("265-0000000000002-02")
                .currencyCode("RSD")
                .description("Test")
                .type(TransactionType.PAYMENT)
                .debit(new BigDecimal("1000.0000"))
                .credit(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();

        byte[] pdf = generator.generate(dto);
        String text = extractPdfText(pdf);

        // stripTrailingZeros on 1000.0000 -> "1E+3" toPlainString -> "1000"
        assertTrue(text.contains("1000"));
    }
}
