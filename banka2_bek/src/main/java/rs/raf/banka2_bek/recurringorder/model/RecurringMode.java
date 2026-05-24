package rs.raf.banka2_bek.recurringorder.model;

// ============================================================
// [B8 - Trajni nalozi (DCA / RecurringOrder) | Nosilac: Nikola Djurovic] - DONE
//
// Enum nacina definisanja iznosa trajnog naloga.
//
// IMPLEMENTIRANO:
//   - Koristitimo ovu vrednost u RecurringOrderService.executeOne() da se
//     odredi sta `value` polje entiteta predstavlja:
//       BY_QUANTITY -> `value` je kolicina hartija (npr. 5 akcija)
//       BY_AMOUNT   -> `value` je novcani iznos u valuti racuna (npr. 100 EUR);
//                      kolicina se izracunava kao floor(value / currentPrice)
//                      neposredno pre kreiranja Market Order-a.
//   - Enum je persistovan kao STRING u bazi (@Enumerated(EnumType.STRING) u entitetu).
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B8.
// ============================================================
public enum RecurringMode {
    BY_QUANTITY,
    BY_AMOUNT
}
