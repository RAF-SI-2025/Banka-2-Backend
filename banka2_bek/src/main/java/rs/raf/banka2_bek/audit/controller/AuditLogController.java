package rs.raf.banka2_bek.audit.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import rs.raf.banka2_bek.audit.dto.AuditLogDto;
import rs.raf.banka2_bek.audit.model.AuditActionType;
import rs.raf.banka2_bek.audit.service.AuditLogService;

import java.time.LocalDateTime;
import java.util.List;

// ============================================================
// TODO [B7 - Audit log | Nosilac: Stasa Dragovic]
//
// REST kontroler koji izlaze audit log ADMIN i SUPERVISOR korisnicima.
// Bazna putanja: /audit
// Pristup: ogranicen na ADMIN i SUPERVISOR — dodati u GlobalSecurityConfig:
//   .requestMatchers(GET, "/audit/**").hasAnyAuthority("ROLE_ADMIN","ADMIN","SUPERVISOR")
// (pratiti pattern iz GlobalSecurityConfig za /actuaries/** i /profit-bank/**)
//
// IMPLEMENTIRATI:
//
//   1. GET /audit
//       Paginiran pregled svih zapisa sa filterima.
//       Query parametri (svi optional):
//         - actionType (String, mapirati u AuditActionType enum, ignorisati ako null)
//         - actorId    (Long)
//         - from       (String ISO-8601 LocalDateTime, parsirati u LocalDateTime)
//         - to         (String ISO-8601 LocalDateTime)
//         - page       (int, default 0)
//         - size       (int, default 20)
//       Vraca: ResponseEntity<Page<AuditLogDto>>
//       Delegira: auditLogService.query(...)
//
//   2. GET /audit/{id}
//       Jedan zapis po ID-u.
//       Vraca: ResponseEntity<AuditLogDto>
//       Baca: 404 ResponseStatusException ako zapis ne postoji.
//
//   3. (opciono) GET /audit/resource/{targetType}/{targetId}
//       Svi zapisi za konkretan resurs (npr. /audit/resource/ORDER/42).
//       Vraca: ResponseEntity<List<AuditLogDto>>
//       Delegira: auditLogService.findByResource(targetType, targetId)
//
// Napomene:
//   - Injektovati samo AuditLogService (nije potreban direktan pristup repozitorijumu).
//   - Koristiti @RequiredArgsConstructor (Lombok) i final polja.
//   - Ne dodavati @PreAuthorize — security se konfigurise centralno u GlobalSecurityConfig.
//
// Konvencija: pratiti paket `savings` kao sablon (SavingsAdminController).
// Spec: Zadaci_Backend.pdf, zadatak B7.
// ============================================================
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<Page<AuditLogDto>> getAuditLogs(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        AuditActionType parsedActionType = null;
        if (actionType != null && !actionType.isBlank()) {
            try {
                parsedActionType = AuditActionType.valueOf(actionType);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown actionType: " + actionType);
            }
        }

        LocalDateTime parsedFrom = from != null ? LocalDateTime.parse(from) : null;
        LocalDateTime parsedTo = to != null ? LocalDateTime.parse(to) : null;

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(auditLogService.query(parsedActionType, actorId, parsedFrom, parsedTo, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(auditLogService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Audit log entry not found: " + id)));
    }

    @GetMapping("/resource/{targetType}/{targetId}")
    public ResponseEntity<List<AuditLogDto>> getByResource(
            @PathVariable String targetType,
            @PathVariable Long targetId) {
        return ResponseEntity.ok(auditLogService.findByResource(targetType, targetId));
    }
}
