package rs.raf.banka2_bek.audit.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// ============================================================
// TODO [B7 - Audit log | Nosilac: Stasa Dragovic]
//
// JPA entitet koji predstavlja jedan zapis u dnevniku revizije (audit log).
// Svaka administrativna akcija cuva ko je izvrsio (actor), sta je uradjeno
// (actionType, description), na kom resursu (targetType, targetId) i koje su
// bile stare i nove vrednosti (oldValue, newValue).
//
// IMPLEMENTIRATI:
//   - actorId       Long          NOT NULL  - ID zaposlenog ili klijenta koji je pokrenuo akciju
//   - actorType     String        NOT NULL, length=16  - vrednost "EMPLOYEE" ili "CLIENT"
//                                 (alternativa: zaseban enum ActorType u istom paketu)
//   - actionType    AuditActionType NOT NULL  - @Enumerated(EnumType.STRING), length=32
//   - description   String        NOT NULL, length=512  - slobodan tekst opisa akcije
//   - targetType    String        nullable,  length=64   - tip resursa (npr. "ORDER", "EMPLOYEE")
//   - targetId      Long          nullable              - ID resursa na kome je akcija izvrsena
//   - oldValue      String        nullable, columnDefinition="TEXT"  - stara vrednost u JSON ili plain tekstu
//   - newValue      String        nullable, columnDefinition="TEXT"  - nova vrednost
//   - createdAt     LocalDateTime NOT NULL, updatable=false  - automatski setuje @CreationTimestamp
//
// Napomene:
//   - Tabela: "audit_logs"
//   - @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder (Lombok)
//   - Ne dodavati @Version niti @UpdateTimestamp (audit zapisi su immutable po principu)
//   - oldValue/newValue mogu biti null kada nema "pre/posle" semantike (npr. TAX_RUN_TRIGGERED)
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B7.
// ============================================================
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "actor_type", nullable = false, length = 16)
    private String actorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32)
    private AuditActionType actionType;

    @Column(name = "description", nullable = false, length = 512)
    private String description;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
