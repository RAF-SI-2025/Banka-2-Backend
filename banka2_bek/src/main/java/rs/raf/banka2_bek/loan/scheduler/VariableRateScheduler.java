package rs.raf.banka2_bek.loan.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.loan.model.InterestType;
import rs.raf.banka2_bek.loan.model.Loan;
import rs.raf.banka2_bek.loan.model.LoanInstallment;
import rs.raf.banka2_bek.loan.model.LoanStatus;
import rs.raf.banka2_bek.loan.model.LoanType;
import rs.raf.banka2_bek.loan.repository.LoanInstallmentRepository;
import rs.raf.banka2_bek.loan.repository.LoanRepository;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Monthly scheduler that simulates variable interest rate changes.
 * Per specification (Celina 4 - Simulacija promenljive kamatne stope):
 * - Generates a random offset in [-1.50%, +1.50%]
 * - Recalculates effectiveRate = nominalRate + offset + margin (by loan type)
 * - Recalculates monthly payment using the annuity formula for remaining debt/period
 * - Updates all future (unpaid) installments accordingly
 */
@Slf4j
@Component
public class VariableRateScheduler {

    private final LoanRepository loanRepository;
    private final LoanInstallmentRepository installmentRepository;

    public VariableRateScheduler(LoanRepository loanRepository,
                                 LoanInstallmentRepository installmentRepository) {
        this.loanRepository = loanRepository;
        this.installmentRepository = installmentRepository;
    }

    /**
     * Runs at 01:00 AM on the 1st of each month.
     * Cron: second minute hour day-of-month month day-of-week
     */
    @Scheduled(cron = "0 0 1 1 * *")
    @Transactional
    public void adjustVariableRates() {
        log.info("=== Variable rate adjustment started ===");

        List<Loan> variableLoans = loanRepository.findAll().stream()
                .filter(loan -> loan.getInterestType() == InterestType.VARIABLE)
                .filter(loan -> loan.getStatus() == LoanStatus.ACTIVE || loan.getStatus() == LoanStatus.LATE)
                .toList();

        if (variableLoans.isEmpty()) {
            log.info("No active variable-rate loans found. Skipping.");
            return;
        }

        // Generate a single random offset for this month (shared across all loans)
        double rawOffset = ThreadLocalRandom.current().nextDouble(-1.50, 1.50);
        BigDecimal offset = BigDecimal.valueOf(rawOffset).setScale(2, RoundingMode.HALF_UP);
        log.info("Generated monthly interest rate offset: {}%", offset);

        int updatedCount = 0;

        for (Loan loan : variableLoans) {
            try {
                adjustLoan(loan, offset);
                updatedCount++;
            } catch (Exception e) {
                log.error("Failed to adjust rate for loan {}: {}", loan.getLoanNumber(), e.getMessage(), e);
            }
        }

        log.info("=== Variable rate adjustment completed: {}/{} loans updated ===", updatedCount, variableLoans.size());
    }

    private void adjustLoan(Loan loan, BigDecimal offset) {
        BigDecimal oldEffectiveRate = loan.getEffectiveRate();
        BigDecimal oldMonthlyPayment = loan.getMonthlyPayment();

        // effectiveRate = nominalRate + offset + margin(loanType)
        BigDecimal margin = getMargin(loan.getLoanType());
        BigDecimal newEffectiveRate = loan.getNominalRate().add(offset).add(margin);

        // Ensure effective rate doesn't go below 0.50% (safety floor)
        if (newEffectiveRate.compareTo(new BigDecimal("0.50")) < 0) {
            newEffectiveRate = new BigDecimal("0.50");
        }

        // Count remaining (unpaid) installments
        List<LoanInstallment> unpaidInstallments = installmentRepository
                .findByLoanIdOrderByExpectedDueDateAsc(loan.getId())
                .stream()
                .filter(i -> !Boolean.TRUE.equals(i.getPaid()))
                .toList();

        int remainingMonths = unpaidInstallments.size();
        if (remainingMonths == 0) {
            log.info("Loan {} has no unpaid installments, skipping", loan.getLoanNumber());
            return;
        }

        // Recalculate monthly payment: A = P * r * (1+r)^n / ((1+r)^n - 1)
        // where P = remainingDebt, r = monthly rate, n = remaining months
        BigDecimal monthlyRate = newEffectiveRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
        BigDecimal newMonthlyPayment;

        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            // Edge case: 0% rate means equal principal payments
            newMonthlyPayment = loan.getRemainingDebt()
                    .divide(BigDecimal.valueOf(remainingMonths), 4, RoundingMode.HALF_UP);
        } else {
            BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
            BigDecimal onePlusRn = onePlusR.pow(remainingMonths, MathContext.DECIMAL128);
            newMonthlyPayment = loan.getRemainingDebt()
                    .multiply(monthlyRate)
                    .multiply(onePlusRn)
                    .divide(onePlusRn.subtract(BigDecimal.ONE), 4, RoundingMode.HALF_UP);
        }

        // Update loan entity
        loan.setEffectiveRate(newEffectiveRate);
        loan.setMonthlyPayment(newMonthlyPayment);
        loanRepository.save(loan);

        // Recalculate and update unpaid installments with new principal/interest breakdown
        BigDecimal remainingPrincipal = loan.getRemainingDebt();
        for (int i = 0; i < unpaidInstallments.size(); i++) {
            LoanInstallment installment = unpaidInstallments.get(i);
            BigDecimal interestPortion = remainingPrincipal.multiply(monthlyRate).setScale(4, RoundingMode.HALF_UP);
            BigDecimal principalPortion = newMonthlyPayment.subtract(interestPortion);

            // Last installment covers the remaining principal exactly
            if (i == unpaidInstallments.size() - 1) {
                principalPortion = remainingPrincipal;
                interestPortion = newMonthlyPayment.subtract(principalPortion).max(BigDecimal.ZERO);
            }

            remainingPrincipal = remainingPrincipal.subtract(principalPortion);

            installment.setAmount(newMonthlyPayment);
            installment.setInterestRate(newEffectiveRate);
            installment.setInterestAmount(interestPortion);
            installment.setPrincipalAmount(principalPortion);
            installmentRepository.save(installment);
        }

        log.info("Loan {} adjusted: effectiveRate {}% -> {}%, monthlyPayment {} -> {}, remaining installments: {}",
                loan.getLoanNumber(),
                oldEffectiveRate, newEffectiveRate,
                oldMonthlyPayment, newMonthlyPayment,
                remainingMonths);
    }

    /**
     * Bank margin per loan type - mirrors LoanServiceImpl.getMargin().
     */
    private BigDecimal getMargin(LoanType type) {
        return switch (type) {
            case CASH -> new BigDecimal("1.75");
            case MORTGAGE -> new BigDecimal("1.50");
            case AUTO -> new BigDecimal("1.25");
            case REFINANCING -> new BigDecimal("1.00");
            case STUDENT -> new BigDecimal("0.75");
        };
    }
}
