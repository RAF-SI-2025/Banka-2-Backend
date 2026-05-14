package rs.raf.banka2_bek.savings.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.savings.entity.SavingsDeposit;
import rs.raf.banka2_bek.savings.entity.SavingsDepositStatus;
import rs.raf.banka2_bek.savings.repository.SavingsDepositRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavingsSchedulerTest {

    @Mock SavingsDepositRepository depositRepo;
    @Mock SavingsDepositProcessor processor;
    @InjectMocks SavingsScheduler scheduler;

    private Currency rsd;
    private SavingsDeposit deposit;

    @BeforeEach
    void setUp() {
        rsd = new Currency();
        rsd.setId(1L);
        rsd.setCode("RSD");

        deposit = SavingsDeposit.builder()
                .id(1L).clientId(1L).linkedAccountId(10L)
                .principalAmount(new BigDecimal("100000"))
                .currency(rsd).termMonths(12)
                .annualInterestRate(new BigDecimal("4.00"))
                .startDate(LocalDate.of(2026, 5, 12))
                .maturityDate(LocalDate.of(2027, 5, 12))
                .nextInterestPaymentDate(LocalDate.of(2026, 6, 12))
                .totalInterestPaid(BigDecimal.ZERO)
                .autoRenew(false)
                .status(SavingsDepositStatus.ACTIVE).build();
    }

    @Test
    void runSavingsCycle_processesNoDeposits() {
        when(depositRepo.findByStatusAndNextInterestPaymentDateLessThanEqual(
                any(SavingsDepositStatus.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(depositRepo.findByStatusAndMaturityDateLessThanEqual(
                any(SavingsDepositStatus.class), any(LocalDate.class)))
                .thenReturn(List.of());

        scheduler.runSavingsCycle();

        verify(processor, never()).payMonthlyInterest(any(), any());
        verify(processor, never()).returnPrincipal(any(), any());
        verify(processor, never()).renewDeposit(any(), any());
    }

    @Test
    void runSavingsCycle_invokesPayMonthlyInterestForDueDeposits() {
        when(depositRepo.findByStatusAndNextInterestPaymentDateLessThanEqual(
                eq(SavingsDepositStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(deposit));
        when(depositRepo.findByStatusAndMaturityDateLessThanEqual(
                eq(SavingsDepositStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of());

        scheduler.runSavingsCycle();

        verify(processor, times(1)).payMonthlyInterest(eq(deposit), any(LocalDate.class));
        verify(processor, never()).returnPrincipal(any(), any());
        verify(processor, never()).renewDeposit(any(), any());
    }

    @Test
    void runSavingsCycle_autoRenew_callsRenewOnProcessor() {
        deposit.setAutoRenew(true);
        when(depositRepo.findByStatusAndNextInterestPaymentDateLessThanEqual(
                any(SavingsDepositStatus.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(depositRepo.findByStatusAndMaturityDateLessThanEqual(
                eq(SavingsDepositStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(deposit));

        scheduler.runSavingsCycle();

        verify(processor, times(1)).renewDeposit(eq(deposit), any(LocalDate.class));
        verify(processor, never()).returnPrincipal(any(), any());
    }

    @Test
    void runSavingsCycle_notAutoRenew_callsReturnPrincipalOnProcessor() {
        deposit.setAutoRenew(false);
        when(depositRepo.findByStatusAndNextInterestPaymentDateLessThanEqual(
                any(SavingsDepositStatus.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(depositRepo.findByStatusAndMaturityDateLessThanEqual(
                eq(SavingsDepositStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(deposit));

        scheduler.runSavingsCycle();

        verify(processor, times(1)).returnPrincipal(eq(deposit), any(LocalDate.class));
        verify(processor, never()).renewDeposit(any(), any());
    }

    @Test
    void runSavingsCycle_swallowsProcessorExceptions() {
        when(depositRepo.findByStatusAndNextInterestPaymentDateLessThanEqual(
                any(SavingsDepositStatus.class), any(LocalDate.class)))
                .thenReturn(List.of(deposit));
        when(depositRepo.findByStatusAndMaturityDateLessThanEqual(
                any(SavingsDepositStatus.class), any(LocalDate.class)))
                .thenReturn(List.of());

        // throw runtime exception — scheduler treba da log-uje i nastavi
        doThrowOnPayInterest();

        scheduler.runSavingsCycle();

        verify(processor, times(1)).payMonthlyInterest(eq(deposit), any(LocalDate.class));
    }

    private void doThrowOnPayInterest() {
        org.mockito.Mockito.doThrow(new IllegalStateException("test"))
                .when(processor).payMonthlyInterest(any(SavingsDeposit.class), any(LocalDate.class));
    }
}
