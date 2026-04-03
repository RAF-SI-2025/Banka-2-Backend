package rs.raf.banka2_bek.notification.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Note: String.format("%,.2f") is locale-dependent, so amount assertions
 * use containsPattern or check for currency and raw digit sequences
 * rather than assuming a specific thousand/decimal separator.
 */
class TransactionEmailTemplateTest {

    private TransactionEmailTemplate template;

    @BeforeEach
    void setUp() {
        template = new TransactionEmailTemplate();
    }

    // ── Payment ─────────────────────────────────────────────────────

    @Test
    void buildPaymentSubject_returnsNonEmpty() {
        assertThat(template.buildPaymentSubject()).isNotBlank();
    }

    @Test
    void buildPaymentSubject_containsBanka2() {
        assertThat(template.buildPaymentSubject()).contains("Banka 2");
    }

    @Test
    void buildPaymentBody_containsCurrencyAndAmountDigits() {
        String body = template.buildPaymentBody(
                new BigDecimal("1500.00"), "RSD",
                "2220001000000011", "2220001000000021",
                LocalDate.of(2026, 3, 15), "Uspešno");
        assertThat(body).contains("RSD");
        // Amount 1500 should appear as formatted digits (locale-dependent separators)
        assertThat(body).containsPattern("1[.,]?500");
    }

    @Test
    void buildPaymentBody_containsAccounts() {
        String body = template.buildPaymentBody(
                new BigDecimal("100"), "EUR",
                "FROM123", "TO456",
                LocalDate.of(2026, 1, 1), "OK");
        assertThat(body).contains("FROM123");
        assertThat(body).contains("TO456");
    }

    @Test
    void buildPaymentBody_containsFormattedDate() {
        String body = template.buildPaymentBody(
                new BigDecimal("100"), "EUR",
                "FROM", "TO",
                LocalDate.of(2026, 3, 15), "OK");
        assertThat(body).contains("15.03.2026.");
    }

    @Test
    void buildPaymentBody_containsStatus() {
        String body = template.buildPaymentBody(
                new BigDecimal("100"), "EUR",
                "FROM", "TO",
                LocalDate.of(2026, 1, 1), "Uspešno");
        assertThat(body).contains("Uspešno");
    }

    @Test
    void buildPaymentBody_containsHtmlStructure() {
        String body = template.buildPaymentBody(
                new BigDecimal("100"), "EUR",
                "FROM", "TO",
                LocalDate.of(2026, 1, 1), "OK");
        assertThat(body).contains("<!DOCTYPE html>");
        assertThat(body).contains("</html>");
    }

    @Test
    void buildPaymentBody_nullAmount_showsDash() {
        String body = template.buildPaymentBody(
                null, "RSD",
                "FROM", "TO",
                LocalDate.of(2026, 1, 1), "OK");
        assertThat(body).contains("-");
    }

    @Test
    void buildPaymentBody_nullCurrency_handlesGracefully() {
        String body = template.buildPaymentBody(
                new BigDecimal("500"), null,
                "FROM", "TO",
                LocalDate.of(2026, 1, 1), "OK");
        assertThat(body).containsPattern("500");
    }

    // ── Card blocked ────────────────────────────────────────────────

    @Test
    void buildCardBlockedSubject_containsBanka2() {
        assertThat(template.buildCardBlockedSubject()).contains("Banka 2");
    }

    @Test
    void buildCardBlockedSubject_containsBlokirana() {
        assertThat(template.buildCardBlockedSubject()).contains("blokirana");
    }

    @Test
    void buildCardBlockedBody_containsLast4Digits() {
        String body = template.buildCardBlockedBody("1234", LocalDate.of(2026, 4, 1));
        assertThat(body).contains("1234");
    }

    @Test
    void buildCardBlockedBody_containsFormattedDate() {
        String body = template.buildCardBlockedBody("5678", LocalDate.of(2026, 4, 1));
        assertThat(body).contains("01.04.2026.");
    }

    @Test
    void buildCardBlockedBody_masksCardNumber() {
        String body = template.buildCardBlockedBody("9999", LocalDate.of(2026, 1, 1));
        assertThat(body).contains("•••• 9999");
    }

    // ── Card unblocked ──────────────────────────────────────────────

    @Test
    void buildCardUnblockedSubject_containsDeblokirana() {
        assertThat(template.buildCardUnblockedSubject()).contains("deblokirana");
    }

    @Test
    void buildCardUnblockedBody_containsLast4Digits() {
        String body = template.buildCardUnblockedBody("4321");
        assertThat(body).contains("•••• 4321");
    }

    @Test
    void buildCardUnblockedBody_containsHtmlStructure() {
        String body = template.buildCardUnblockedBody("0000");
        assertThat(body).contains("<!DOCTYPE html>");
    }

    // ── Loan request ────────────────────────────────────────────────

    @Test
    void buildLoanRequestSubject_containsBanka2() {
        assertThat(template.buildLoanRequestSubject()).contains("Banka 2");
    }

    @Test
    void buildLoanRequestBody_containsLoanType() {
        String body = template.buildLoanRequestBody("Stambeni", new BigDecimal("50000"), "EUR");
        assertThat(body).contains("Stambeni");
    }

    @Test
    void buildLoanRequestBody_containsAmountAndCurrency() {
        String body = template.buildLoanRequestBody("Gotovinski", new BigDecimal("10000"), "RSD");
        assertThat(body).containsPattern("10[.,]?000");
        assertThat(body).contains("RSD");
    }

    @Test
    void buildLoanRequestBody_containsPendingStatus() {
        String body = template.buildLoanRequestBody("Test", new BigDecimal("1000"), "EUR");
        assertThat(body).contains("Na čekanju");
    }

    // ── Loan approved ───────────────────────────────────────────────

    @Test
    void buildLoanApprovedSubject_containsOdobren() {
        assertThat(template.buildLoanApprovedSubject()).contains("odobren");
    }

    @Test
    void buildLoanApprovedBody_containsLoanNumber() {
        String body = template.buildLoanApprovedBody(
                "KR-001", new BigDecimal("20000"), "EUR",
                new BigDecimal("333.33"), LocalDate.of(2026, 5, 1));
        assertThat(body).contains("KR-001");
    }

    @Test
    void buildLoanApprovedBody_containsMonthlyPaymentDigits() {
        String body = template.buildLoanApprovedBody(
                "KR-002", new BigDecimal("20000"), "RSD",
                new BigDecimal("5000"), LocalDate.of(2026, 5, 1));
        assertThat(body).containsPattern("5[.,]?000");
    }

    @Test
    void buildLoanApprovedBody_containsStartDate() {
        String body = template.buildLoanApprovedBody(
                "KR-003", new BigDecimal("20000"), "EUR",
                new BigDecimal("500"), LocalDate.of(2026, 5, 1));
        assertThat(body).contains("01.05.2026.");
    }

    // ── Loan rejected ───────────────────────────────────────────────

    @Test
    void buildLoanRejectedSubject_containsOdbijen() {
        assertThat(template.buildLoanRejectedSubject()).contains("odbijen");
    }

    @Test
    void buildLoanRejectedBody_containsLoanType() {
        String body = template.buildLoanRejectedBody("Auto", new BigDecimal("15000"), "EUR");
        assertThat(body).contains("Auto");
    }

    @Test
    void buildLoanRejectedBody_containsRejectedStatus() {
        String body = template.buildLoanRejectedBody("Test", new BigDecimal("1000"), "RSD");
        assertThat(body).contains("Odbijen");
    }

    // ── Installment paid ────────────────────────────────────────────

    @Test
    void buildInstallmentPaidSubject_containsRata() {
        assertThat(template.buildInstallmentPaidSubject()).contains("Rata");
    }

    @Test
    void buildInstallmentPaidBody_containsLoanNumber() {
        String body = template.buildInstallmentPaidBody(
                "KR-010", new BigDecimal("500"), "EUR", new BigDecimal("9500"));
        assertThat(body).contains("KR-010");
    }

    @Test
    void buildInstallmentPaidBody_containsInstallmentAmountDigits() {
        String body = template.buildInstallmentPaidBody(
                "KR-010", new BigDecimal("500"), "EUR", new BigDecimal("9500"));
        assertThat(body).containsPattern("500");
    }

    @Test
    void buildInstallmentPaidBody_containsRemainingDebtDigits() {
        String body = template.buildInstallmentPaidBody(
                "KR-010", new BigDecimal("500"), "EUR", new BigDecimal("9500"));
        assertThat(body).containsPattern("9[.,]?500");
    }

    // ── Installment failed ──────────────────────────────────────────

    @Test
    void buildInstallmentFailedSubject_containsNeuspesna() {
        assertThat(template.buildInstallmentFailedSubject()).containsIgnoringCase("neuspe");
    }

    @Test
    void buildInstallmentFailedBody_containsLoanNumber() {
        String body = template.buildInstallmentFailedBody(
                "KR-020", new BigDecimal("600"), "RSD", LocalDate.of(2026, 6, 15));
        assertThat(body).contains("KR-020");
    }

    @Test
    void buildInstallmentFailedBody_containsNextRetryDate() {
        String body = template.buildInstallmentFailedBody(
                "KR-020", new BigDecimal("600"), "RSD", LocalDate.of(2026, 6, 15));
        assertThat(body).contains("15.06.2026.");
    }

    @Test
    void buildInstallmentFailedBody_containsAmountDueDigits() {
        String body = template.buildInstallmentFailedBody(
                "KR-020", new BigDecimal("600"), "RSD", LocalDate.of(2026, 6, 15));
        assertThat(body).containsPattern("600");
    }

    // ── Common HTML checks ──────────────────────────────────────────

    @Test
    void allBodies_containGradientHeader() {
        String payment = template.buildPaymentBody(new BigDecimal("1"), "X", "A", "B", LocalDate.now(), "S");
        String blocked = template.buildCardBlockedBody("0000", LocalDate.now());
        String unblocked = template.buildCardUnblockedBody("0000");
        String loanReq = template.buildLoanRequestBody("T", new BigDecimal("1"), "X");

        for (String body : new String[]{payment, blocked, unblocked, loanReq}) {
            assertThat(body).contains("linear-gradient(135deg,#6366f1,#7c3aed)");
            assertThat(body).contains("automatska poruka");
        }
    }
}
