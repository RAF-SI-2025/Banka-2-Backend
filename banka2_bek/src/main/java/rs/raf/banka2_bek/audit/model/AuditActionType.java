package rs.raf.banka2_bek.audit.model;

// ============================================================
// TODO [B7 - Audit log | Nosilac: Stasa Dragovic]
//
// Enum tipova administrativnih akcija koje se belezeZu audit log-u.
//
// IMPLEMENTIRATI:
//   - Svaki enum vrednost moze dobiti labelField (String) za prikaz u UI-u
//     ako bude potrebno (npr. LIMIT_CHANGED("Promena limita agenta")).
//   - Prosirivati ovaj enum kad se dodaju nove akcije koje treba logovati
//     (npr. FUND_CREATED, CARD_BLOCKED, EMPLOYEE_DEACTIVATED).
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B7.
// ============================================================
public enum AuditActionType {
    LIMIT_CHANGED,
    USED_LIMIT_RESET,
    ORDER_APPROVED,
    ORDER_DECLINED,
    PERMISSIONS_CHANGED,
    TAX_RUN_TRIGGERED
}
