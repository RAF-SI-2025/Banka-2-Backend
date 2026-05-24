package rs.raf.banka2_bek.audit.service;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.audit.dto.AuditLogDto;
import rs.raf.banka2_bek.audit.model.AuditActionType;
import rs.raf.banka2_bek.audit.model.AuditLog;
import rs.raf.banka2_bek.audit.repository.AuditLogRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// ============================================================
// TODO [B7 - Audit log | Nosilac: Stasa Dragovic]
//
// Servis za kreiranje i pretragu zapisa u dnevniku revizije.
// Injektovati: AuditLogRepository (upis i citanje).
// Opciono: EmployeeRepository + ClientRepository za resolve actorName u DTO-u.
//
// IMPLEMENTIRATI:
//
//   1. record(actorId: Long, actorType: String, action: AuditActionType,
//             description: String, targetType: String, targetId: Long,
//             oldValue: String, newValue: String) -> void
//       Kreira i cuva novi AuditLog red u bazi.
//       @Transactional (writable).
//       Koristiti ovu metodu kao tacku za poziv iz ActuaryManagement, OrderService, itd.
//       Preporuka: napraviti i overload metodu bez oldValue/newValue za akcije bez "pre/posle"
//       semantike (npr. TAX_RUN_TRIGGERED).
//
//   2. query(actionType: AuditActionType, actorId: Long,
//            from: LocalDateTime, to: LocalDateTime,
//            pageable: Pageable) -> Page<AuditLogDto>
//       Filtrirani pregled audit log-a za ADMIN/SUPERVISOR.
//       @Transactional(readOnly = true).
//       Mapira AuditLog entitete u AuditLogDto (popuniti actorName lookup).
//       Svi parametri su nullable — ako je null, filter se ignorise (kao u SavingsAdminService).
//
//   3. (opciono) findByResource(targetType: String, targetId: Long) -> List<AuditLogDto>
//       Sve akcije vezane za konkretan resurs (ORDER, EMPLOYEE, ACTUARY, ...).
//       Korisno za detalj prikaz entiteta na FE-u.
//
// Napomene:
//   - Metoda record() treba da bude @Transactional(propagation = REQUIRES_NEW) ako zelimo
//     da upis u audit log uspe cak i ako pozivajuca transakcija bude rollback-ovana.
//     Ovo je bitno za logovanje neuspelih akcija — razmotriti sa nosiocem.
//   - Ne brisati AuditLog zapise — audit log je append-only.
//
// Konvencija: pratiti paket `savings` kao sablon (SavingsDepositService, SavingsAdminService).
// Spec: Zadaci_Backend.pdf, zadatak B7.
// ============================================================
@Service
@RequiredArgsConstructor
public class AuditLogService {


    private final AuditLogRepository auditLogRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, String actorType, AuditActionType action,
                       String description, String targetType, Long targetId,
                       String oldValue, String newValue) {
        AuditLog log = AuditLog.builder()
                .actorId(actorId)
                .actorType(actorType)
                .actionType(action)
                .description(description)
                .targetType(targetType)
                .targetId(targetId)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();
        auditLogRepository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, String actorType, AuditActionType action,
                       String description, String targetType, Long targetId) {
        record(actorId, actorType, action, description, targetType, targetId, null, null);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> query(AuditActionType actionType, Long actorId,
                                   LocalDateTime from, LocalDateTime to,
                                   Pageable pageable) {
        return auditLogRepository
                .findFiltered(actionType, actorId, from, to, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<AuditLogDto> findById(Long id) {
        return auditLogRepository.findById(id).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> findByResource(String targetType, Long targetId) {
        return auditLogRepository.findByTargetTypeAndTargetId(targetType, targetId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private AuditLogDto toDto(AuditLog log) {
        String actorName = resolveActorName(log.getActorId(), log.getActorType());
        return AuditLogDto.builder()
                .id(log.getId())
                .actorId(log.getActorId())
                .actorType(log.getActorType())
                .actorName(actorName)
                .actionType(log.getActionType().name())
                .description(log.getDescription())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String resolveActorName(Long actorId, String actorType) {
        if ("EMPLOYEE".equals(actorType)) {
            return employeeRepository.findById(actorId)
                    .map(e -> e.getFirstName() + " " + e.getLastName())
                    .orElse("ID:" + actorId);
        }
        return "ID:" + actorId;
    }
}
