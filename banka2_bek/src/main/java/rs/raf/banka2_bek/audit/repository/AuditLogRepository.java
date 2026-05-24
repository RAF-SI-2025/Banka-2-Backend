package rs.raf.banka2_bek.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.audit.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rs.raf.banka2_bek.audit.model.AuditActionType;


import java.time.LocalDateTime;
import java.util.List;

// ============================================================
// TODO [B7 - Audit log | Nosilac: Stasa Dragovic]
//
// Spring Data JPA repozitorijum za entitet AuditLog.
// Sluzi za upis i pretragu zapisa u dnevniku revizije.
//
// IMPLEMENTIRATI:
//   - findByActorId(Long actorId, Pageable pageable) -> Page<AuditLog>
//       Svi zapisi za datog actora (zaposleni/klijent), sortirani po createdAt DESC.
//
//   - @Query JPQL metoda za filtrirani pregled (preporuka: nazvati je findFiltered ili audit):
//       Parametri: actionType (nullable AuditActionType), actorId (nullable Long),
//                  from (nullable LocalDateTime), to (nullable LocalDateTime)
//       Koristiti "(:param IS NULL OR d.field = :param)" pattern kao u SavingsDepositRepository.
//       Vracati Page<AuditLog> sa Pageable argumentom.
//
//   - findByTargetTypeAndTargetId(String targetType, Long targetId) -> List<AuditLog>
//       Svi zapisi vezani za konkretan resurs (npr. sve akcije nad ORDER id=42).
//
// Konvencija: pratiti paket `savings` kao sablon (SavingsDepositRepository).
// Spec: Zadaci_Backend.pdf, zadatak B7.
// ============================================================
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByActorId(Long actorId, Pageable pageable);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:actionType IS NULL OR a.actionType = :actionType)
          AND (:actorId IS NULL OR a.actorId = :actorId)
          AND (:from IS NULL OR a.createdAt >= :from)
          AND (:to IS NULL OR a.createdAt <= :to)
        ORDER BY a.createdAt DESC
        """)
    Page<AuditLog> findFiltered(
            @Param("actionType") AuditActionType actionType,
            @Param("actorId") Long actorId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    List<AuditLog> findByTargetTypeAndTargetId(String targetType, Long targetId);
}
