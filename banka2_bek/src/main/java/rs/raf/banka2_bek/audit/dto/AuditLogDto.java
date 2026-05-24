package rs.raf.banka2_bek.audit.dto;

import lombok.*;

import java.time.LocalDateTime;

// ============================================================
// TODO [B7 - Audit log | Nosilac: Stasa Dragovic]
//
// DTO koji se vraca klijentu (ADMIN/SUPERVISOR) pri pregledu audit log-a.
// Nema osetljivih internih polja (nema raw DB id-eva entiteta koji nemaju smisla van konteksta).
//
// IMPLEMENTIRATI:
//   - id            Long           - primarni kljuc zapisa
//   - actorId       Long           - ID aktera (zaposleni ili klijent)
//   - actorType     String         - "EMPLOYEE" ili "CLIENT"
//   - actorName     String         - ime i prezime aktera (resolve-uje mapper ili service)
//   - actionType    String         - naziv enum vrednosti (npr. "LIMIT_CHANGED")
//   - description   String         - slobodan tekst opisa
//   - targetType    String         - tip resursa (moze biti null)
//   - targetId      Long           - ID resursa (moze biti null)
//   - oldValue      String         - stara vrednost (moze biti null)
//   - newValue      String         - nova vrednost (moze biti null)
//   - createdAt     LocalDateTime  - vreme zapisa
//
// Napomene:
//   - Koristiti @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder (Lombok)
//   - actorName se popunjava u AuditLogService.record()/query() — treba lookup ka EmployeeRepository
//     ili ClientRepository na osnovu actorType. Ako lookup ne uspe, koristiti fallback "ID:" + actorId.
//   - actionType vraca String (ne enum) da FE ne zavisi od Java enum klase.
//
// Konvencija: pratiti paket `savings` kao sablon (SavingsDepositDto).
// Spec: Zadaci_Backend.pdf, zadatak B7.
// ============================================================
// Napomena: skeleton je prazan pa nosi samo @NoArgsConstructor. Pri dodavanju
// polja vratiti @AllArgsConstructor i @Builder (videti Lombok napomenu u TODO-u).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDto {
    private Long id;
    private Long actorId;
    private String actorType;
    private String actorName;
    private String actionType;
    private String description;
    private String targetType;
    private Long targetId;
    private String oldValue;
    private String newValue;
    private LocalDateTime createdAt;
}
