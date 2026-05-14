package rs.raf.banka2_bek.savings.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.savings.entity.SavingsDeposit;
import rs.raf.banka2_bek.savings.entity.SavingsDepositStatus;
import rs.raf.banka2_bek.savings.entity.SavingsInterestRate;
import rs.raf.banka2_bek.savings.entity.SavingsTransaction;
import rs.raf.banka2_bek.savings.repository.SavingsDepositRepository;
import rs.raf.banka2_bek.savings.repository.SavingsTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavingsDepositProcessorTest {

    @Mock SavingsDepositRepository depositRepo;
    @Mock SavingsTransactionRepository txRepo;
    @Mock SavingsInterestRateService rateService;
    @Mock AccountRepository accountRepo;
    @InjectMocks SavingsDepositProcessor processor;

    private Currency rsd;
    private SavingsDeposit deposit;
    private Account linked;

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

        linked = new Account();
        linked.setId(10L);
        linked.setBalance(new BigDecimal("1000"));
        linked.setAvailableBalance(new BigDecimal("1000"));
        linked.setCurrency(rsd);
    }

    @Test
    void payMonthlyInterest_creditsLinkedAndUpdatesNextDate() {
        when(accountRepo.findForUpdateById(10L)).thenReturn(Optional.of(linked));
        when(accountRepo.save(linked)).thenReturn(linked);
        when(depositRepo.save(deposit)).thenReturn(deposit);

        processor.payMonthlyInterest(deposit, LocalDate.of(2026, 6, 12));

        // 100000 * 4.00 / 1200 = 333.3333
        assertThat(linked.getBalance()).isEqualByComparingTo("1333.3333");
        assertThat(linked.getAvailableBalance()).isEqualByComparingTo("1333.3333");
        assertThat(deposit.getNextInterestPaymentDate()).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(deposit.getTotalInterestPaid()).isEqualByComparingTo("333.3333");
        verify(txRepo).save(any(SavingsTransaction.class));
    }

    @Test
    void returnPrincipal_setsStatusMatured() {
        when(accountRepo.findForUpdateById(10L)).thenReturn(Optional.of(linked));
        when(accountRepo.save(linked)).thenReturn(linked);
        when(depositRepo.save(deposit)).thenReturn(deposit);

        processor.returnPrincipal(deposit, LocalDate.of(2027, 5, 12));

        assertThat(deposit.getStatus()).isEqualTo(SavingsDepositStatus.MATURED);
        assertThat(linked.getBalance()).isEqualByComparingTo("101000");
        verify(txRepo).save(any(SavingsTransaction.class));
    }

    @Test
    void renewDeposit_createsNewWithCurrentRate() {
        SavingsInterestRate currentRate = SavingsInterestRate.builder()
                .id(2L).currency(rsd).termMonths(12).annualRate(new BigDecimal("4.50"))
                .active(true).effectiveFrom(LocalDate.now()).build();
        when(rateService.findActive(1L, 12)).thenReturn(Optional.of(currentRate));
        when(depositRepo.save(any(SavingsDeposit.class))).thenAnswer(inv -> inv.getArgument(0));

        processor.renewDeposit(deposit, LocalDate.of(2027, 5, 12));

        assertThat(deposit.getStatus()).isEqualTo(SavingsDepositStatus.RENEWED);
        verify(depositRepo, times(2)).save(any(SavingsDeposit.class));
        verify(txRepo).save(any(SavingsTransaction.class));
    }
}
