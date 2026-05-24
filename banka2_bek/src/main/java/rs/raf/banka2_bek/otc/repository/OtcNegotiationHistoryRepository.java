package rs.raf.banka2_bek.otc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rs.raf.banka2_bek.otc.model.OtcNegotiationHistory;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;

// ============================================================
// TODO [B10 - Istorija OTC pregovora | Nosilac: Aja Timotic]
//
// JPA repozitorijum za citanje i pisanje historije OTC pregovora.
//
// IMPLEMENTIRATI — dodati sledece metode:
//
//   - findByNegotiationIdOrderByCreatedAtAsc(Long negotiationId)
//       : List<OtcNegotiationHistory>
//       Vraca sve zapise za jednu ponudu sortirane od najstarijeg
//       do najnovijeg (hronoloski tok pregovora).
//
//   - findByModifiedByIdOrderByCreatedAtDesc(Long modifiedById)
//       : List<OtcNegotiationHistory>
//       Vraca sve izmene koje je napravio odredjeni korisnik.
//
//   - findByStatusOrderByCreatedAtDesc(String status)
//       : List<OtcNegotiationHistory>
//       Vraca historijske zapise filtrirane po statusu ponude
//       (npr. sve izmene koje su dovele do "ACCEPTED").
//
//   - findByCreatedAtBetweenOrderByCreatedAtDesc(
//         LocalDateTime from, LocalDateTime to)
//       : List<OtcNegotiationHistory>
//       Vraca sve zapise u zadatom vremenskom intervalu —
//       koristi se za filter po datumu u kontroleru.
//       Import: import java.time.LocalDateTime;
//
//   - @Query JPQL metoda za kombinovani filter (status + modifiedById
//       + vremenski interval) sa @Param anotacijama i IS NULL OR
//       obrascem za opcionalne parametre (prati adminFindAll u
//       SavingsDepositRepository kao sablon). Predlog potpisa:
//         Page<OtcNegotiationHistory> findWithFilters(
//             @Param("status") String status,
//             @Param("modifiedById") Long modifiedById,
//             @Param("from") LocalDateTime from,
//             @Param("to") LocalDateTime to,
//             Pageable pageable);
//       Importi: Page, Pageable, @Query, @Param
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B10.
// ============================================================

public interface OtcNegotiationHistoryRepository
        extends JpaRepository<OtcNegotiationHistory, Long> {

    List<OtcNegotiationHistory> findByNegotiationIdOrderByCreatedAtAsc(Long negotiationId);

    List<OtcNegotiationHistory> findByModifiedByIdOrderByCreatedAtDesc(Long modifiedById);

    List<OtcNegotiationHistory> findByStatusOrderByCreatedAtDesc(String status);

    List<OtcNegotiationHistory> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);


    @Query("SELECT h FROM OtcNegotiationHistory h " +
            "WHERE (:status IS NULL OR h.status = :status) " +
            "  AND (:modifiedById IS NULL OR h.modifiedById = :modifiedById) " +
            "  AND (:from IS NULL OR h.createdAt >= :from) " +
            "  AND (:to IS NULL OR h.createdAt <= :to) " +
            "ORDER BY h.createdAt DESC")
    Page<OtcNegotiationHistory> findWithFilters(
            @Param("status") String status,
            @Param("modifiedById") Long modifiedById,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
