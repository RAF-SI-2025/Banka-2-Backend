package rs.raf.banka2_bek.recurringorder.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.recurringorder.model.RecurringOrder;
import rs.raf.banka2_bek.recurringorder.repository.RecurringOrderRepository;
import rs.raf.banka2_bek.recurringorder.service.RecurringOrderService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

// ============================================================
// [B8 - Trajni nalozi (DCA / RecurringOrder) | Nosilac: Nikola Djurovic] - DONE
//
// Scheduler koji periodicno provera i izvrsava dospele trajne naloge.
// Prati obrazac iz SavingsScheduler — transakcione operacije delegira na
// RecurringOrderService da bi @Transactional(REQUIRES_NEW) Spring AOP proxy
// ispravno presreo pozive (direktni `this.method()` pozivi bi bili ignorisani).
//
// IMPLEMENTIRANO — dodata @Scheduled metoda i helper:
//
//   @Scheduled(fixedRate = 60_000)     // svakih 60 sekundi
//   public void processRecurringOrders()
//       -> Poziva runCycle()
//       -> Raspon 60s je dovoljan jer je najfrekventnija kadenca DAILY;
//          za demonstraciju/test okruzenje moze se smanjiti na 10_000
//
//   public void runCycle()
//       -> LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC)
//       -> List<RecurringOrder> due = recurringOrderRepo.findDue(now)
//          (ili findByActiveTrue() pa filter na nextRun <= now)
//       -> log.info("RecurringOrderScheduler: {} dospelih naloga za {}", due.size(), now)
//       -> Za svaki RecurringOrder r u due:
//            try {
//                recurringOrderService.executeOne(r);
//            } catch (Exception e) {
//                log.error("Scheduler: greska pri izvrsavanju trajnog naloga id={}: {}",
//                          r.getId(), e.getMessage(), e);
//                // nastaviti sa sledecim — jedna greska ne sme blokirati ostatak
//            }
//
// Injectovati kao final polja:
//   - RecurringOrderRepository recurringOrderRepo
//   - RecurringOrderService recurringOrderService
//
// NAPOMENA: @Scheduled zahteva @EnableScheduling na konfiguracionoj klasi ili
// na glavnoj @SpringBootApplication klasi. Proveriti da li je vec omoguceno u
// projektu pre aktiviranja — ne dodavati duplikat.
//
// Konvencija: prati paket `savings` (SavingsScheduler) kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B8.
// ============================================================
@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringOrderScheduler {

    private final RecurringOrderRepository recurringOrderRepo;
    private final RecurringOrderService recurringOrderService;

    @Scheduled(fixedRate = 60_000)
    public void processRecurringOrders() {
        runCycle();
    }

    public void runCycle() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<RecurringOrder> due = recurringOrderRepo.findDue(now);

        log.info("RecurringOrderScheduler: {} dospelih naloga za {}", due.size(), now);

        for (RecurringOrder order : due) {
            try {
                recurringOrderService.executeOne(order);
            } catch (Exception e) {
                log.error("Scheduler: greška pri izvršavanju trajnog naloga id={}: {}",
                        order.getId(), e.getMessage(), e);
            }
        }
    }
}
