package rs.raf.banka2_bek.account.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.repository.AccountRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountSpendingResetScheduler {

    private final AccountRepository accountRepository;

    /**
     * Resets daily spending for all accounts every day at midnight (00:00).
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void resetDailySpending() {
        log.info("Starting daily spending reset for all accounts...");
        int updatedCount = accountRepository.resetDailySpending();
        log.info("Daily spending reset complete. Updated {} accounts.", updatedCount);
    }

    /**
     * Resets monthly spending for all accounts at midnight on the 1st of each month.
     */
    @Scheduled(cron = "0 0 0 1 * *")
    @Transactional
    public void resetMonthlySpending() {
        log.info("Starting monthly spending reset for all accounts...");
        int updatedCount = accountRepository.resetMonthlySpending();
        log.info("Monthly spending reset complete. Updated {} accounts.", updatedCount);
    }
}
