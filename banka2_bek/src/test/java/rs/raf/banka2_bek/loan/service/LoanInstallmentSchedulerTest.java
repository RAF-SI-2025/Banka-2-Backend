package rs.raf.banka2_bek.loan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.loan.model.Loan;
import rs.raf.banka2_bek.loan.model.LoanInstallment;
import rs.raf.banka2_bek.loan.model.LoanStatus;
import rs.raf.banka2_bek.loan.repository.LoanInstallmentRepository;
import rs.raf.banka2_bek.loan.repository.LoanRepository;
import rs.raf.banka2_bek.notification.service.MailNotificationService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanInstallmentSchedulerTest {

    private static final String BANK_REG_NUMBER = "1234567890";

    @Mock
    private LoanInstallmentRepository installmentRepository;

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private MailNotificationService mailNotificationService;

    private LoanInstallmentScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new LoanInstallmentScheduler(
                installmentRepository, loanRepository, accountRepository,
                mailNotificationService, BANK_REG_NUMBER);
    }

    private Currency buildCurrency() {
        Currency currency = new Currency();
        currency.setId(1L);
        currency.setCode("RSD");
        return currency;
    }

    private Client buildClient() {
        Client client = mock(Client.class);
        lenient().when(client.getEmail()).thenReturn("client@banka.rs");
        return client;
    }

    private Account buildAccount(Long id, BigDecimal balance) {
        Account account = new Account();
        account.setId(id);
        account.setBalance(balance);
        account.setAvailableBalance(balance);
        return account;
    }

    private Loan buildLoan(Long id, Account account, Client client, Currency currency,
                            BigDecimal remainingDebt) {
        Loan loan = new Loan();
        loan.setId(id);
        loan.setLoanNumber("LOAN-" + id);
        loan.setAccount(account);
        loan.setClient(client);
        loan.setCurrency(currency);
        loan.setRemainingDebt(remainingDebt);
        loan.setStatus(LoanStatus.ACTIVE);
        return loan;
    }

    private LoanInstallment buildInstallment(Long id, Loan loan, BigDecimal amount,
                                              LocalDate dueDate) {
        LoanInstallment inst = new LoanInstallment();
        inst.setId(id);
        inst.setLoan(loan);
        inst.setAmount(amount);
        inst.setPrincipalAmount(amount.multiply(new BigDecimal("0.80")));
        inst.setInterestAmount(amount.multiply(new BigDecimal("0.20")));
        inst.setExpectedDueDate(dueDate);
        inst.setPaid(false);
        return inst;
    }

    @Nested
    @DisplayName("processInstallments")
    class ProcessInstallments {

        @Test
        @DisplayName("does nothing when no installments are due")
        void doesNothingWhenNoDueInstallments() {
            when(installmentRepository.findByExpectedDueDateLessThanEqualAndPaidFalse(any()))
                    .thenReturn(Collections.emptyList());

            scheduler.processInstallments();

            verify(accountRepository, never()).findForUpdateById(any());
            verify(loanRepository, never()).save(any());
        }

        @Test
        @DisplayName("successfully pays installment when account has sufficient funds")
        void paysInstallmentWithSufficientFunds() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account account = buildAccount(1L, BigDecimal.valueOf(50000));
            Loan loan = buildLoan(1L, account, client, currency, BigDecimal.valueOf(100000));
            LoanInstallment installment = buildInstallment(1L, loan, BigDecimal.valueOf(5000),
                    LocalDate.now());

            when(installmentRepository.findByExpectedDueDateLessThanEqualAndPaidFalse(any()))
                    .thenReturn(List.of(installment));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency(any(), eq("RSD")))
                    .thenReturn(Optional.empty());

            scheduler.processInstallments();

            // Account balance should decrease
            assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(45000));
            assertThat(account.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(45000));

            // Installment should be marked as paid
            assertThat(installment.getPaid()).isTrue();
            assertThat(installment.getActualDueDate()).isEqualTo(LocalDate.now());

            verify(installmentRepository).save(installment);
            verify(loanRepository).save(loan);
        }

        @Test
        @DisplayName("reschedules installment for 72h later when insufficient funds")
        void reschedulesWhenInsufficientFunds() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account account = buildAccount(1L, BigDecimal.valueOf(1000));
            Loan loan = buildLoan(1L, account, client, currency, BigDecimal.valueOf(100000));
            LoanInstallment installment = buildInstallment(1L, loan, BigDecimal.valueOf(5000),
                    LocalDate.now());

            when(installmentRepository.findByExpectedDueDateLessThanEqualAndPaidFalse(any()))
                    .thenReturn(List.of(installment));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

            scheduler.processInstallments();

            // Installment should NOT be paid
            assertThat(installment.getPaid()).isFalse();
            // Should be rescheduled to 3 days later
            assertThat(installment.getExpectedDueDate()).isEqualTo(LocalDate.now().plusDays(3));

            // Loan should be marked as LATE
            assertThat(loan.getStatus()).isEqualTo(LoanStatus.LATE);

            verify(installmentRepository).save(installment);
            verify(loanRepository).save(loan);
        }

        @Test
        @DisplayName("does not change loan status to LATE if already LATE")
        void doesNotChangeLateStatusAgain() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account account = buildAccount(1L, BigDecimal.valueOf(100));
            Loan loan = buildLoan(1L, account, client, currency, BigDecimal.valueOf(50000));
            loan.setStatus(LoanStatus.LATE);
            LoanInstallment installment = buildInstallment(1L, loan, BigDecimal.valueOf(5000),
                    LocalDate.now());

            when(installmentRepository.findByExpectedDueDateLessThanEqualAndPaidFalse(any()))
                    .thenReturn(List.of(installment));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

            scheduler.processInstallments();

            assertThat(loan.getStatus()).isEqualTo(LoanStatus.LATE);
            // loanRepository.save should only be called once for rescheduling, not for status change
            verify(loanRepository, never()).save(loan);
        }

        @Test
        @DisplayName("skips installment when account not found")
        void skipsWhenAccountNotFound() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account account = buildAccount(99L, BigDecimal.valueOf(50000));
            Loan loan = buildLoan(1L, account, client, currency, BigDecimal.valueOf(100000));
            LoanInstallment installment = buildInstallment(1L, loan, BigDecimal.valueOf(5000),
                    LocalDate.now());

            when(installmentRepository.findByExpectedDueDateLessThanEqualAndPaidFalse(any()))
                    .thenReturn(List.of(installment));
            when(accountRepository.findForUpdateById(99L)).thenReturn(Optional.empty());

            scheduler.processInstallments();

            verify(installmentRepository, never()).save(any());
            verify(loanRepository, never()).save(any());
        }

        @Test
        @DisplayName("sets loan status to PAID when remaining debt reaches zero")
        void setsStatusToPaidWhenDebtCleared() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account account = buildAccount(1L, BigDecimal.valueOf(50000));
            BigDecimal installmentAmount = BigDecimal.valueOf(5000);
            BigDecimal principalAmount = installmentAmount.multiply(new BigDecimal("0.80")); // 4000
            Loan loan = buildLoan(1L, account, client, currency, principalAmount);
            LoanInstallment installment = buildInstallment(1L, loan, installmentAmount,
                    LocalDate.now());

            when(installmentRepository.findByExpectedDueDateLessThanEqualAndPaidFalse(any()))
                    .thenReturn(List.of(installment));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency(any(), eq("RSD")))
                    .thenReturn(Optional.empty());

            scheduler.processInstallments();

            assertThat(loan.getStatus()).isEqualTo(LoanStatus.PAID);
            assertThat(loan.getRemainingDebt()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("credits bank account when found")
        void creditsBankAccount() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account clientAccount = buildAccount(1L, BigDecimal.valueOf(50000));
            Account bankAccount = buildAccount(2L, BigDecimal.valueOf(1000000));
            Loan loan = buildLoan(1L, clientAccount, client, currency, BigDecimal.valueOf(100000));
            LoanInstallment installment = buildInstallment(1L, loan, BigDecimal.valueOf(5000),
                    LocalDate.now());

            when(installmentRepository.findByExpectedDueDateLessThanEqualAndPaidFalse(any()))
                    .thenReturn(List.of(installment));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(clientAccount));
            when(accountRepository.findBankAccountForUpdateByCurrency(any(), eq("RSD")))
                    .thenReturn(Optional.of(bankAccount));

            scheduler.processInstallments();

            // Bank account should increase by installment amount
            assertThat(bankAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1005000));
            assertThat(bankAccount.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(1005000));
            verify(accountRepository).save(bankAccount);
        }

        @Test
        @DisplayName("email failure does not rollback installment processing on successful payment")
        void emailFailureDoesNotRollbackPayment() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account account = buildAccount(1L, BigDecimal.valueOf(50000));
            Loan loan = buildLoan(1L, account, client, currency, BigDecimal.valueOf(100000));
            LoanInstallment installment = buildInstallment(1L, loan, BigDecimal.valueOf(5000),
                    LocalDate.now());

            when(installmentRepository.findByExpectedDueDateLessThanEqualAndPaidFalse(any()))
                    .thenReturn(List.of(installment));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency(any(), eq("RSD")))
                    .thenReturn(Optional.empty());
            doThrow(new RuntimeException("SMTP error"))
                    .when(mailNotificationService).sendInstallmentPaidMail(
                            anyString(), anyString(), any(), anyString(), any());

            scheduler.processInstallments();

            // Payment should still be processed despite email failure
            assertThat(installment.getPaid()).isTrue();
            verify(installmentRepository).save(installment);
        }

        @Test
        @DisplayName("email failure does not rollback installment rescheduling")
        void emailFailureDoesNotRollbackRescheduling() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account account = buildAccount(1L, BigDecimal.valueOf(100));
            Loan loan = buildLoan(1L, account, client, currency, BigDecimal.valueOf(100000));
            LoanInstallment installment = buildInstallment(1L, loan, BigDecimal.valueOf(5000),
                    LocalDate.now());

            when(installmentRepository.findByExpectedDueDateLessThanEqualAndPaidFalse(any()))
                    .thenReturn(List.of(installment));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            doThrow(new RuntimeException("SMTP error"))
                    .when(mailNotificationService).sendInstallmentFailedMail(
                            anyString(), anyString(), any(), anyString(), any());

            scheduler.processInstallments();

            // Rescheduling should still proceed
            assertThat(installment.getExpectedDueDate()).isEqualTo(LocalDate.now().plusDays(3));
            verify(installmentRepository).save(installment);
        }

        @Test
        @DisplayName("processes multiple installments in one run")
        void processesMultipleInstallments() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account account1 = buildAccount(1L, BigDecimal.valueOf(50000));
            Account account2 = buildAccount(2L, BigDecimal.valueOf(50000));
            Loan loan1 = buildLoan(1L, account1, client, currency, BigDecimal.valueOf(100000));
            Loan loan2 = buildLoan(2L, account2, client, currency, BigDecimal.valueOf(80000));
            LoanInstallment inst1 = buildInstallment(1L, loan1, BigDecimal.valueOf(5000), LocalDate.now());
            LoanInstallment inst2 = buildInstallment(2L, loan2, BigDecimal.valueOf(3000), LocalDate.now());

            when(installmentRepository.findByExpectedDueDateLessThanEqualAndPaidFalse(any()))
                    .thenReturn(List.of(inst1, inst2));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account1));
            when(accountRepository.findForUpdateById(2L)).thenReturn(Optional.of(account2));
            when(accountRepository.findBankAccountForUpdateByCurrency(any(), eq("RSD")))
                    .thenReturn(Optional.empty());

            scheduler.processInstallments();

            verify(installmentRepository, times(2)).save(any(LoanInstallment.class));
            verify(loanRepository, times(2)).save(any(Loan.class));
        }
    }
}
