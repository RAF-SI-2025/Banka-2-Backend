package rs.raf.banka2_bek.recurringorder.model;

// ============================================================
// [B8 - Trajni nalozi (DCA / RecurringOrder) | Nosilac: Nikola Djurovic] - DONE
//
// Enum kadence (ucestalosti) izvrsavanja trajnog naloga.
//
// IMPLEMENTIRATI:
//   - Koristiti ovu vrednost u RecurringOrderScheduler.advanceNextRun() da se
//     izracuna sledeci datum izvrsavanja:
//       DAILY   -> nextRun.plusDays(1)
//       WEEKLY  -> nextRun.plusWeeks(1)
//       MONTHLY -> nextRun.plusMonths(1)
//   - Enum je persistovan kao STRING u bazi (@Enumerated(EnumType.STRING) u entitetu).
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B8.
// ============================================================
public enum RecurringCadence {
    DAILY,
    WEEKLY,
    MONTHLY
}
