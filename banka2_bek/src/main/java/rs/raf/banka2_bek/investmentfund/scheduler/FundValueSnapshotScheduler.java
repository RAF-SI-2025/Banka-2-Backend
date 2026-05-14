package rs.raf.banka2_bek.investmentfund.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.investmentfund.model.ClientFundPosition;
import rs.raf.banka2_bek.investmentfund.model.FundValueSnapshot;
import rs.raf.banka2_bek.investmentfund.model.InvestmentFund;
import rs.raf.banka2_bek.investmentfund.repository.ClientFundPositionRepository;
import rs.raf.banka2_bek.investmentfund.repository.FundValueSnapshotRepository;
import rs.raf.banka2_bek.investmentfund.repository.InvestmentFundRepository;
import rs.raf.banka2_bek.investmentfund.service.FundValueCalculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FundValueSnapshotScheduler {

    private final InvestmentFundRepository investmentFundRepository;
    private final FundValueCalculator fundValueCalculator;
    private final FundValueSnapshotRepository fundValueSnapshotRepository;
    private final AccountRepository accountRepository;
    private final ClientFundPositionRepository clientFundPositionRepository;

    /**
     * Bag prijavljen 10.05.2026: FundDetailsPage prikazuje "Performanse fonda"
     * graf prazan dok god u {@code fund_value_snapshots} ne postoji ijedan red
     * (FE filtruje per-period — ako nema snapshot za period, prikaze placeholder
     * "Nema podataka o performansama"). Cron-trigger pravi snapshot u 23:45,
     * ali svaki novi fond i svaki podizajan stack pre tog vremena pokazuje
     * graf prazan, sto deluje kao bag iako je samo "rano".
     *
     * Resenje sa dva sloja:
     *  1) {@link #onStartupSnapshotAllFunds} — odmah po ApplicationReady-u.
     *     U Docker stack-u BE startuje pre seed-a, pa u tom trenutku jos
     *     nema fondova, ali ovo i dalje pokriva slucajeve gde se BE
     *     restart-uje na vec popunjenoj bazi.
     *  2) {@link #onPostSeedSnapshotAllFunds} — okida se 90s posle startupa
     *     (jednom). To je dovoljno vremena da seed servis (depends_on:
     *     backend healthy) zavrsi insertovanje fondova kroz psql, pa
     *     snapshot moze sve da pokrije pre nego sto klijent otvori
     *     FundDetailsPage.
     *
     * Oba poziva su idempotentna preko {@code existsByFundIdAndSnapshotDate}
     * guard-a — nema duplikata cak i kad se stack restartuje vise puta u
     * toku istog dana.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartupSnapshotAllFunds() {
        try {
            snapshotAllFunds();
        } catch (Exception e) {
            log.warn("Fund snapshot init pri startup-u nije uspeo: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = Long.MAX_VALUE, initialDelay = 90_000)
    public void onPostSeedSnapshotAllFunds() {
        try {
            snapshotAllFunds();
        } catch (Exception e) {
            log.warn("Fund snapshot 90s post-seed nije uspeo: {}", e.getMessage());
        }
    }

    /**
     * Idempotent helper koji InvestmentFundService.invest/withdraw zove
     * posle uspesne operacije — garantuje da fond koji je upravo imao
     * cash flow ima makar 1 snapshot za danas, cak i ako post-seed
     * scheduler nije stigao da se okine (recimo pri brz BE restart).
     */
    public void snapshotFundIfMissing(InvestmentFund fund) {
        if (fund == null || fund.getId() == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        if (fundValueSnapshotRepository.existsByFundIdAndSnapshotDate(fund.getId(), today)) {
            return;
        }
        try {
            BigDecimal fundValue = fundValueCalculator.computeFundValue(fund);
            BigDecimal profit = fundValueCalculator.computeProfit(fund);
            BigDecimal liquidAmount = accountRepository.findById(fund.getAccountId())
                    .map(Account::getBalance)
                    .orElse(BigDecimal.ZERO);
            BigDecimal investedTotal = clientFundPositionRepository.findByFundId(fund.getId()).stream()
                    .map(ClientFundPosition::getTotalInvested)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            FundValueSnapshot snapshot = new FundValueSnapshot();
            snapshot.setFundId(fund.getId());
            snapshot.setSnapshotDate(today);
            snapshot.setFundValue(fundValue);
            snapshot.setLiquidAmount(liquidAmount);
            snapshot.setInvestedTotal(investedTotal);
            snapshot.setProfit(profit);
            fundValueSnapshotRepository.save(snapshot);
        } catch (Exception e) {
            log.warn("Inline snapshot za fond #{} nije uspeo: {}", fund.getId(), e.getMessage());
        }
    }

    @Scheduled(cron = "0 45 23 * * *")
    public void snapshotAllFunds() {
        LocalDate today = LocalDate.now();
        List<InvestmentFund> funds = investmentFundRepository.findByActiveTrueOrderByNameAsc();
        log.info("Fund snapshot: {} active funds for {}", funds.size(), today);

        for (InvestmentFund fund : funds) {
            try {
                if (fundValueSnapshotRepository.existsByFundIdAndSnapshotDate(fund.getId(), today)) {
                    continue;
                }
                BigDecimal fundValue = fundValueCalculator.computeFundValue(fund);
                BigDecimal profit = fundValueCalculator.computeProfit(fund);
                BigDecimal liquidAmount = accountRepository.findById(fund.getAccountId())
                        .map(Account::getBalance)
                        .orElse(BigDecimal.ZERO);
                BigDecimal investedTotal = clientFundPositionRepository.findByFundId(fund.getId()).stream()
                        .map(ClientFundPosition::getTotalInvested)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                FundValueSnapshot snapshot = new FundValueSnapshot();
                snapshot.setFundId(fund.getId());
                snapshot.setSnapshotDate(today);
                snapshot.setFundValue(fundValue);
                snapshot.setLiquidAmount(liquidAmount);
                snapshot.setInvestedTotal(investedTotal);
                snapshot.setProfit(profit);
                fundValueSnapshotRepository.save(snapshot);
            } catch (Exception e) {
                log.error("Failed to snapshot fund #{}: {}", fund.getId(), e.getMessage());
            }
        }
    }
}
