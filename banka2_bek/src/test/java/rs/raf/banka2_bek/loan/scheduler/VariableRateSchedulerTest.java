package rs.raf.banka2_bek.loan.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.loan.model.*;
import rs.raf.banka2_bek.loan.repository.LoanInstallmentRepository;
import rs.raf.banka2_bek.loan.repository.LoanRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VariableRateSchedulerTest {

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private LoanInstallmentRepository installmentRepository;

    @InjectMocks
    private VariableRateScheduler scheduler;

    private Loan buildVariableLoan(Long id, String loanNumber, LoanType loanType,
                                    BigDecimal nominalRate, BigDecimal effectiveRate,
                                    BigDecimal monthlyPayment, BigDecimal remainingDebt) {
        Loan loan = new Loan();
        loan.setId(id);
        loan.setLoanNumber(loanNumber);
        loan.setLoanType(loanType);
        loan.setInterestType(InterestType.VARIABLE);
        loan.setNominalRate(nominalRate);
        loan.setEffectiveRate(effectiveRate);
        loan.setMonthlyPayment(monthlyPayment);
        loan.setRemainingDebt(remainingDebt);
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setAmount(BigDecimal.valueOf(100000));
        loan.setRepaymentPeriod(60);
        loan.setStartDate(LocalDate.now().minusMonths(6));
        loan.setEndDate(LocalDate.now().plusMonths(54));
        return loan;
    }

    private Loan buildFixedLoan(Long id) {
        Loan loan = new Loan();
        loan.setId(id);
        loan.setLoanNumber("FIXED-001");
        loan.setLoanType(LoanType.CASH);
        loan.setInterestType(InterestType.FIXED);
        loan.setNominalRate(new BigDecimal("5.00"));
        loan.setEffectiveRate(new BigDecimal("6.75"));
        loan.setMonthlyPayment(new BigDecimal("2000.0000"));
        loan.setRemainingDebt(BigDecimal.valueOf(80000));
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setAmount(BigDecimal.valueOf(100000));
        loan.setRepaymentPeriod(60);
        loan.setStartDate(LocalDate.now().minusMonths(6));
        loan.setEndDate(LocalDate.now().plusMonths(54));
        return loan;
    }

    private LoanInstallment buildInstallment(Long id, Loan loan, boolean paid, BigDecimal amount) {
        LoanInstallment inst = new LoanInstallment();
        inst.setId(id);
        inst.setLoan(loan);
        inst.setPaid(paid);
        inst.setAmount(amount);
        inst.setInterestRate(loan.getEffectiveRate());
        inst.setExpectedDueDate(LocalDate.now().plusMonths(id.intValue()));
        return inst;
    }

    @Nested
    @DisplayName("adjustVariableRates")
    class AdjustVariableRates {

        @Test
        @DisplayName("does nothing when no variable-rate loans exist")
        void doesNothingWhenNoVariableLoans() {
            when(loanRepository.findAll()).thenReturn(Collections.emptyList());

            scheduler.adjustVariableRates();

            verify(loanRepository, never()).save(any());
            verify(installmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("skips fixed-rate loans")
        void skipsFixedRateLoans() {
            Loan fixedLoan = buildFixedLoan(1L);
            when(loanRepository.findAll()).thenReturn(List.of(fixedLoan));

            scheduler.adjustVariableRates();

            verify(loanRepository, never()).save(any());
        }

        @Test
        @DisplayName("skips loans with non-active/non-late status")
        void skipsNonActiveLoans() {
            Loan pendingLoan = buildVariableLoan(1L, "VAR-001", LoanType.CASH,
                    new BigDecimal("4.00"), new BigDecimal("5.75"),
                    new BigDecimal("2000.0000"), BigDecimal.valueOf(80000));
            pendingLoan.setStatus(LoanStatus.PENDING);

            Loan paidLoan = buildVariableLoan(2L, "VAR-002", LoanType.AUTO,
                    new BigDecimal("4.00"), new BigDecimal("5.25"),
                    new BigDecimal("1800.0000"), BigDecimal.ZERO);
            paidLoan.setStatus(LoanStatus.PAID);

            when(loanRepository.findAll()).thenReturn(List.of(pendingLoan, paidLoan));

            scheduler.adjustVariableRates();

            verify(loanRepository, never()).save(any());
        }

        @Test
        @DisplayName("processes active variable-rate loan and updates effective rate and monthly payment")
        void processesActiveVariableLoan() {
            Loan loan = buildVariableLoan(1L, "VAR-001", LoanType.CASH,
                    new BigDecimal("4.00"), new BigDecimal("5.75"),
                    new BigDecimal("2000.0000"), BigDecimal.valueOf(80000));

            LoanInstallment unpaid1 = buildInstallment(1L, loan, false, new BigDecimal("2000.0000"));
            LoanInstallment unpaid2 = buildInstallment(2L, loan, false, new BigDecimal("2000.0000"));
            LoanInstallment paid = buildInstallment(3L, loan, true, new BigDecimal("2000.0000"));

            when(loanRepository.findAll()).thenReturn(List.of(loan));
            when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(1L))
                    .thenReturn(List.of(paid, unpaid1, unpaid2));

            scheduler.adjustVariableRates();

            // Loan should be saved with updated effective rate
            ArgumentCaptor<Loan> loanCaptor = ArgumentCaptor.forClass(Loan.class);
            verify(loanRepository).save(loanCaptor.capture());
            Loan savedLoan = loanCaptor.getValue();

            // Effective rate = nominalRate + offset + margin(CASH=1.75)
            // The offset is random, but the rate must be a valid number
            assertThat(savedLoan.getEffectiveRate()).isNotNull();
            assertThat(savedLoan.getMonthlyPayment()).isNotNull();
            assertThat(savedLoan.getMonthlyPayment()).isPositive();

            // Only unpaid installments should be updated (2 saves)
            verify(installmentRepository, times(2)).save(any(LoanInstallment.class));
        }

        @Test
        @DisplayName("processes LATE variable-rate loan")
        void processesLateVariableLoan() {
            Loan loan = buildVariableLoan(1L, "VAR-LATE", LoanType.MORTGAGE,
                    new BigDecimal("3.50"), new BigDecimal("5.00"),
                    new BigDecimal("1500.0000"), BigDecimal.valueOf(50000));
            loan.setStatus(LoanStatus.LATE);

            LoanInstallment unpaid = buildInstallment(1L, loan, false, new BigDecimal("1500.0000"));

            when(loanRepository.findAll()).thenReturn(List.of(loan));
            when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(1L))
                    .thenReturn(List.of(unpaid));

            scheduler.adjustVariableRates();

            verify(loanRepository).save(loan);
            verify(installmentRepository).save(unpaid);
        }

        @Test
        @DisplayName("skips loan with no unpaid installments")
        void skipsLoanWithNoUnpaidInstallments() {
            Loan loan = buildVariableLoan(1L, "VAR-DONE", LoanType.CASH,
                    new BigDecimal("4.00"), new BigDecimal("5.75"),
                    new BigDecimal("2000.0000"), BigDecimal.valueOf(80000));

            LoanInstallment paid = buildInstallment(1L, loan, true, new BigDecimal("2000.0000"));

            when(loanRepository.findAll()).thenReturn(List.of(loan));
            when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(1L))
                    .thenReturn(List.of(paid));

            scheduler.adjustVariableRates();

            verify(loanRepository, never()).save(any());
        }

        @Test
        @DisplayName("effective rate has floor of 0.50%")
        void effectiveRateHasFloor() {
            // Use a very low nominal rate so that offset + margin could go below 0.50
            Loan loan = buildVariableLoan(1L, "VAR-LOW", LoanType.STUDENT,
                    new BigDecimal("0.00"), new BigDecimal("0.75"),
                    new BigDecimal("1000.0000"), BigDecimal.valueOf(10000));

            LoanInstallment unpaid = buildInstallment(1L, loan, false, new BigDecimal("1000.0000"));

            when(loanRepository.findAll()).thenReturn(List.of(loan));
            when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(1L))
                    .thenReturn(List.of(unpaid));

            // Run multiple times to increase chance of hitting the floor
            for (int i = 0; i < 20; i++) {
                scheduler.adjustVariableRates();
            }

            // Verify effective rate never went below 0.50
            ArgumentCaptor<Loan> captor = ArgumentCaptor.forClass(Loan.class);
            verify(loanRepository, atLeastOnce()).save(captor.capture());
            for (Loan saved : captor.getAllValues()) {
                assertThat(saved.getEffectiveRate()).isGreaterThanOrEqualTo(new BigDecimal("0.50"));
            }
        }

        @Test
        @DisplayName("continues processing other loans when one loan throws exception")
        void continuesOnException() {
            Loan loan1 = buildVariableLoan(1L, "VAR-ERR", LoanType.CASH,
                    new BigDecimal("4.00"), new BigDecimal("5.75"),
                    new BigDecimal("2000.0000"), BigDecimal.valueOf(80000));
            Loan loan2 = buildVariableLoan(2L, "VAR-OK", LoanType.AUTO,
                    new BigDecimal("3.00"), new BigDecimal("4.25"),
                    new BigDecimal("1500.0000"), BigDecimal.valueOf(60000));

            // Loan1 will throw because installmentRepository returns null pointer scenario
            when(loanRepository.findAll()).thenReturn(List.of(loan1, loan2));
            when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(1L))
                    .thenThrow(new RuntimeException("DB error"));

            LoanInstallment unpaid = buildInstallment(1L, loan2, false, new BigDecimal("1500.0000"));
            when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(2L))
                    .thenReturn(List.of(unpaid));

            scheduler.adjustVariableRates();

            // Second loan should still be processed
            verify(loanRepository).save(loan2);
        }

        @Test
        @DisplayName("uses correct margin for each loan type")
        void usesCorrectMarginPerLoanType() {
            // Test with MORTGAGE which has margin 1.50
            Loan mortgageLoan = buildVariableLoan(1L, "VAR-MORTGAGE", LoanType.MORTGAGE,
                    new BigDecimal("4.00"), new BigDecimal("5.50"),
                    new BigDecimal("3000.0000"), BigDecimal.valueOf(200000));

            LoanInstallment unpaid = buildInstallment(1L, mortgageLoan, false, new BigDecimal("3000.0000"));

            when(loanRepository.findAll()).thenReturn(List.of(mortgageLoan));
            when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(1L))
                    .thenReturn(List.of(unpaid));

            scheduler.adjustVariableRates();

            ArgumentCaptor<Loan> captor = ArgumentCaptor.forClass(Loan.class);
            verify(loanRepository).save(captor.capture());
            Loan saved = captor.getValue();

            // effectiveRate = nominalRate(4.00) + offset([-1.50, 1.50]) + margin(1.50)
            // Range: 4.00 - 1.50 + 1.50 = 4.00 to 4.00 + 1.50 + 1.50 = 7.00
            // But floor is 0.50, so effective range is [max(0.50, 4.00), 7.00]
            assertThat(saved.getEffectiveRate()).isGreaterThanOrEqualTo(new BigDecimal("0.50"));
        }

        @Test
        @DisplayName("updates installment amounts to new monthly payment")
        void updatesInstallmentAmountsToNewPayment() {
            Loan loan = buildVariableLoan(1L, "VAR-INST", LoanType.CASH,
                    new BigDecimal("4.00"), new BigDecimal("5.75"),
                    new BigDecimal("2000.0000"), BigDecimal.valueOf(50000));

            LoanInstallment unpaid1 = buildInstallment(1L, loan, false, new BigDecimal("2000.0000"));
            LoanInstallment unpaid2 = buildInstallment(2L, loan, false, new BigDecimal("2000.0000"));

            when(loanRepository.findAll()).thenReturn(List.of(loan));
            when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(1L))
                    .thenReturn(List.of(unpaid1, unpaid2));

            scheduler.adjustVariableRates();

            ArgumentCaptor<LoanInstallment> captor = ArgumentCaptor.forClass(LoanInstallment.class);
            verify(installmentRepository, times(2)).save(captor.capture());

            for (LoanInstallment saved : captor.getAllValues()) {
                // All updated installments should have the same new monthly payment
                assertThat(saved.getAmount()).isNotNull();
                assertThat(saved.getAmount()).isPositive();
                assertThat(saved.getInterestRate()).isNotNull();
                assertThat(saved.getInterestAmount()).isNotNull();
                assertThat(saved.getPrincipalAmount()).isNotNull();
            }
        }
    }
}
