package rs.raf.banka2_bek.savings.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Pure utility za izracunavanje kamata i datuma.
 * Bez Spring zavisnosti, lako se testira sa cistim JUnit testovima.
 */
public final class SavingsCalculator {

    private static final BigDecimal MONTHLY_DIVISOR = new BigDecimal("1200");
    private static final BigDecimal EARLY_WITHDRAWAL_PENALTY_RATE = new BigDecimal("0.01");

    private SavingsCalculator() {}

    /** Mesecna kamata = principal * (annualRate / 100 / 12). */
    public static BigDecimal monthlyInterest(BigDecimal principal, BigDecimal annualRate) {
        return principal.multiply(annualRate)
                .divide(MONTHLY_DIVISOR, 4, RoundingMode.HALF_UP);
    }

    /** Ukupna kamata tokom celokupnog roka. */
    public static BigDecimal totalInterestOverTerm(BigDecimal principal,
                                                    BigDecimal annualRate,
                                                    int termMonths) {
        return monthlyInterest(principal, annualRate).multiply(BigDecimal.valueOf(termMonths));
    }

    /** Penal kod razrocenja = 1% glavnice. */
    public static BigDecimal earlyWithdrawalPenalty(BigDecimal principal) {
        return principal.multiply(EARLY_WITHDRAWAL_PENALTY_RATE).setScale(4, RoundingMode.HALF_UP);
    }

    /** Sledeca mesecna isplata: trenutna + 1 mesec (LocalDate.plusMonths handluje 31->28/30 automatski). */
    public static LocalDate nextMonthlyAnniversary(LocalDate current) {
        return current.plusMonths(1);
    }
}
