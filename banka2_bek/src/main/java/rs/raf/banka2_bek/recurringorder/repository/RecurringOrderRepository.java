package rs.raf.banka2_bek.recurringorder.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rs.raf.banka2_bek.recurringorder.model.RecurringOrder;

import java.time.LocalDateTime;
import java.util.List;

// ============================================================
// [B8 - Trajni nalozi (DCA / RecurringOrder) | Nosilac: Nikola Djurovic] - DONE
//
// JPA repozitorijum za trajne naloge.
//
// IMPLEMENTIRANO — dodate sledece metode:
//   - List<RecurringOrder> findByActiveTrue()
//       -> koristi ga scheduler da dobije sve aktivne naloge za tek izvrsavanje;
//          nije potrebna dodatna JPQL anotacija, Spring Data derivira upit automatski
//   - List<RecurringOrder> findByOwnerIdAndOwnerTypeOrderByCreatedAtDesc(Long ownerId, String ownerType)
//       -> vraca sve naloge jednog vlasnika (klijenta ili zaposlenog) sortirane
//          od najnovijeg ka najstarijem; koristi RecurringOrderService.listMy()
//   - @Query JPQL metoda findDue(LocalDateTime now) koja vraca sve aktivne naloge
//     ciji je nextRun <= :now; efikasnije od iteriranja svih aktivnih u scheduleru
//     kada baza ima veliki broj zapisa:
//         @Query("SELECT r FROM RecurringOrder r WHERE r.active = true AND r.nextRun <= :now")
//         List<RecurringOrder> findDue(@Param("now") LocalDateTime now);
//
// Konvencija: prati paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B8.
// ============================================================
public interface RecurringOrderRepository extends JpaRepository<RecurringOrder, Long> {

    List<RecurringOrder> findByActiveTrue();

    List<RecurringOrder> findByOwnerIdAndOwnerTypeOrderByCreatedAtDesc(Long ownerId, String ownerType);

    @Query("SELECT r FROM RecurringOrder r WHERE r.active = true AND r.nextRun <= :now")
    List<RecurringOrder> findDue(@Param("now") LocalDateTime now);
}
