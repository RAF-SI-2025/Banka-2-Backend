package rs.raf.banka2_bek.account.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.repository.AccountRepository;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountSpendingResetSchedulerTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountSpendingResetScheduler scheduler;

    @Nested
    @DisplayName("resetDailySpending")
    class ResetDailySpending {

        @Test
        @DisplayName("calls repository resetDailySpending exactly once")
        void callsResetDailySpendingOnce() {
            when(accountRepository.resetDailySpending()).thenReturn(5);

            scheduler.resetDailySpending();

            verify(accountRepository, times(1)).resetDailySpending();
        }

        @Test
        @DisplayName("works when zero accounts are updated")
        void worksWhenZeroAccountsUpdated() {
            when(accountRepository.resetDailySpending()).thenReturn(0);

            scheduler.resetDailySpending();

            verify(accountRepository, times(1)).resetDailySpending();
        }

        @Test
        @DisplayName("works when many accounts are updated")
        void worksWhenManyAccountsUpdated() {
            when(accountRepository.resetDailySpending()).thenReturn(1000);

            scheduler.resetDailySpending();

            verify(accountRepository, times(1)).resetDailySpending();
        }
    }

    @Nested
    @DisplayName("resetMonthlySpending")
    class ResetMonthlySpending {

        @Test
        @DisplayName("calls repository resetMonthlySpending exactly once")
        void callsResetMonthlySpendingOnce() {
            when(accountRepository.resetMonthlySpending()).thenReturn(3);

            scheduler.resetMonthlySpending();

            verify(accountRepository, times(1)).resetMonthlySpending();
        }

        @Test
        @DisplayName("works when zero accounts are updated")
        void worksWhenZeroAccountsUpdated() {
            when(accountRepository.resetMonthlySpending()).thenReturn(0);

            scheduler.resetMonthlySpending();

            verify(accountRepository, times(1)).resetMonthlySpending();
        }

        @Test
        @DisplayName("works when many accounts are updated")
        void worksWhenManyAccountsUpdated() {
            when(accountRepository.resetMonthlySpending()).thenReturn(500);

            scheduler.resetMonthlySpending();

            verify(accountRepository, times(1)).resetMonthlySpending();
        }
    }
}
