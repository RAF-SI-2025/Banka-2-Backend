package rs.raf.banka2_bek.savings.service;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.savings.entity.SavingsDeposit;
import rs.raf.banka2_bek.savings.entity.SavingsDepositStatus;
import rs.raf.banka2_bek.savings.repository.SavingsDepositRepository;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * CRON + startup ulaz tacka za stedne deposit-e. Sve transakcione operacije
 * delegira na {@link SavingsDepositProcessor} (drugi bean) tako da @Transactional
 * prolazi kroz Spring AOP proxy. Intra-class @Transactional pozivi bi bili
 * silently ignorisani (CGLib proxy ne presreca direktne `this.method()` pozive).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SavingsScheduler {

    private final SavingsDepositRepository depositRepo;
    private final SavingsDepositProcessor processor;

    @Scheduled(cron = "0 0 2 * * *")
    public void processSavingsDeposits() {
        runSavingsCycle();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("SavingsScheduler: startup catch-up run");
        runSavingsCycle();
    }

    public void runSavingsCycle() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<SavingsDeposit> dueForInterest = depositRepo.findByStatusAndNextInterestPaymentDateLessThanEqual(
                SavingsDepositStatus.ACTIVE, today);
        log.info("SavingsScheduler: {} deposit-a za isplatu kamate za {}", dueForInterest.size(), today);
        for (SavingsDeposit d : dueForInterest) {
            try {
                processor.payMonthlyInterest(d, today);
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException oole) {
                log.debug("Scheduler: skipped deposit {} (locked by another worker)", d.getId());
            } catch (Exception e) {
                log.error("Scheduler: failed interest for deposit {}: {}", d.getId(), e.getMessage(), e);
            }
        }

        List<SavingsDeposit> matured = depositRepo.findByStatusAndMaturityDateLessThanEqual(
                SavingsDepositStatus.ACTIVE, today);
        log.info("SavingsScheduler: {} deposit-a za dospece za {}", matured.size(), today);
        for (SavingsDeposit d : matured) {
            try {
                if (Boolean.TRUE.equals(d.getAutoRenew())) {
                    processor.renewDeposit(d, today);
                } else {
                    processor.returnPrincipal(d, today);
                }
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException oole) {
                log.debug("Scheduler: skipped maturity {} (locked)", d.getId());
            } catch (Exception e) {
                log.error("Scheduler: failed maturity for deposit {}: {}", d.getId(), e.getMessage(), e);
            }
        }
    }
}
