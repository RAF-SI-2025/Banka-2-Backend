package rs.raf.banka2_bek.savings.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.savings.entity.SavingsDeposit;
import rs.raf.banka2_bek.savings.entity.SavingsDepositStatus;
import rs.raf.banka2_bek.savings.entity.SavingsInterestRate;
import rs.raf.banka2_bek.savings.entity.SavingsTransaction;
import rs.raf.banka2_bek.savings.entity.SavingsTransactionType;
import rs.raf.banka2_bek.savings.repository.SavingsDepositRepository;
import rs.raf.banka2_bek.savings.repository.SavingsTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Procesor pojedinacnih scheduler operacija. Izdvojen iz {@link SavingsScheduler}
 * tako da {@code @Transactional} prolazi kroz CGLib proxy (intra-class pozivi unutar
 * istog @Service bean-a ne prolaze kroz proxy, pa ce @Transactional biti ignorisan).
 *
 * Tri metode su public i sve sa @Transactional. Scheduler ih poziva preko ovog
 * bean-a (proxy poziv), tako da svaki invocation ima nezavisnu transakciju.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SavingsDepositProcessor {

    private final SavingsDepositRepository depositRepo;
    private final SavingsTransactionRepository txRepo;
    private final SavingsInterestRateService rateService;
    private final AccountRepository accountRepo;

    @Transactional
    public void payMonthlyInterest(SavingsDeposit d, LocalDate today) {
        BigDecimal monthlyInterest = SavingsCalculator.monthlyInterest(
                d.getPrincipalAmount(), d.getAnnualInterestRate());

        Account linked = accountRepo.findForUpdateById(d.getLinkedAccountId())
                .orElseThrow(() -> new IllegalStateException("Povezani racun ne postoji"));
        linked.setBalance(linked.getBalance().add(monthlyInterest));
        linked.setAvailableBalance(linked.getAvailableBalance().add(monthlyInterest));
        accountRepo.save(linked);

        d.setTotalInterestPaid(d.getTotalInterestPaid().add(monthlyInterest));
        d.setNextInterestPaymentDate(SavingsCalculator.nextMonthlyAnniversary(d.getNextInterestPaymentDate()));
        depositRepo.save(d);

        txRepo.save(SavingsTransaction.builder()
                .deposit(d).type(SavingsTransactionType.INTEREST_PAYMENT)
                .amount(monthlyInterest).currency(d.getCurrency()).processedDate(today)
                .description("Mesecna kamata depozita #" + d.getId())
                .build());

        log.info("Scheduler: isplacena kamata {} {} za deposit {}",
                monthlyInterest, d.getCurrency().getCode(), d.getId());
    }

    @Transactional
    public void returnPrincipal(SavingsDeposit d, LocalDate today) {
        Account linked = accountRepo.findForUpdateById(d.getLinkedAccountId())
                .orElseThrow(() -> new IllegalStateException("Povezani racun ne postoji"));
        linked.setBalance(linked.getBalance().add(d.getPrincipalAmount()));
        linked.setAvailableBalance(linked.getAvailableBalance().add(d.getPrincipalAmount()));
        accountRepo.save(linked);

        d.setStatus(SavingsDepositStatus.MATURED);
        depositRepo.save(d);

        txRepo.save(SavingsTransaction.builder()
                .deposit(d).type(SavingsTransactionType.PRINCIPAL_RETURN)
                .amount(d.getPrincipalAmount()).currency(d.getCurrency()).processedDate(today)
                .description("Glavnica dospelog depozita #" + d.getId())
                .build());

        log.info("Scheduler: vracena glavnica {} {} za deposit {}",
                d.getPrincipalAmount(), d.getCurrency().getCode(), d.getId());
    }

    @Transactional
    public void renewDeposit(SavingsDeposit d, LocalDate today) {
        BigDecimal currentRate = rateService.findActive(d.getCurrency().getId(), d.getTermMonths())
                .map(SavingsInterestRate::getAnnualRate)
                .orElseThrow(() -> new IllegalStateException(
                    "Stopa za auto-obnovu nije dostupna za " + d.getCurrency().getCode()));

        d.setStatus(SavingsDepositStatus.RENEWED);
        depositRepo.save(d);

        SavingsDeposit renewed = SavingsDeposit.builder()
                .clientId(d.getClientId())
                .linkedAccountId(d.getLinkedAccountId())
                .principalAmount(d.getPrincipalAmount())
                .currency(d.getCurrency())
                .termMonths(d.getTermMonths())
                .annualInterestRate(currentRate)
                .startDate(today)
                .maturityDate(today.plusMonths(d.getTermMonths()))
                .nextInterestPaymentDate(today.plusMonths(1))
                .totalInterestPaid(BigDecimal.ZERO)
                .autoRenew(true)
                .status(SavingsDepositStatus.ACTIVE)
                .build();
        renewed = depositRepo.save(renewed);

        txRepo.save(SavingsTransaction.builder()
                .deposit(renewed).type(SavingsTransactionType.RENEWAL_OPEN)
                .amount(d.getPrincipalAmount()).currency(d.getCurrency()).processedDate(today)
                .description("Auto-obnova dospelog depozita #" + d.getId())
                .build());

        log.info("Scheduler: auto-obnova {} -> {} za client {}",
                d.getId(), renewed.getId(), d.getClientId());
    }
}
