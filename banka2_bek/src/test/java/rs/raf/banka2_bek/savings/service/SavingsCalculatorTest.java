package rs.raf.banka2_bek.savings.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class SavingsCalculatorTest {

    @Test
    void monthlyInterest_rsd_4percent_200k() {
        BigDecimal result = SavingsCalculator.monthlyInterest(
                new BigDecimal("200000"), new BigDecimal("4.00"));
        // 200000 * 4.00 / 1200 = 666.6667
        assertThat(result).isEqualByComparingTo("666.6667");
    }

    @Test
    void monthlyInterest_eur_2percent_1000() {
        BigDecimal result = SavingsCalculator.monthlyInterest(
                new BigDecimal("1000"), new BigDecimal("2.00"));
        // 1000 * 2.00 / 1200 = 1.6667
        assertThat(result).isEqualByComparingTo("1.6667");
    }

    @Test
    void totalInterestOverTerm_12months_4percent_200k() {
        BigDecimal result = SavingsCalculator.totalInterestOverTerm(
                new BigDecimal("200000"), new BigDecimal("4.00"), 12);
        // 666.6667 * 12 = 8000.0004
        assertThat(result).isEqualByComparingTo("8000.0004");
    }

    @Test
    void earlyWithdrawalPenalty_1percent() {
        BigDecimal result = SavingsCalculator.earlyWithdrawalPenalty(new BigDecimal("200000"));
        // 200000 * 0.01 = 2000.0000
        assertThat(result).isEqualByComparingTo("2000.0000");
    }

    @Test
    void nextMonthlyAnniversary_endOfMonth31to28() {
        LocalDate result = SavingsCalculator.nextMonthlyAnniversary(LocalDate.of(2026, 1, 31));
        assertThat(result).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void nextMonthlyAnniversary_regularMonth() {
        LocalDate result = SavingsCalculator.nextMonthlyAnniversary(LocalDate.of(2026, 5, 12));
        assertThat(result).isEqualTo(LocalDate.of(2026, 6, 12));
    }
}
