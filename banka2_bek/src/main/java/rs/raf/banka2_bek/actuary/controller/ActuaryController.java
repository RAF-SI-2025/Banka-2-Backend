package rs.raf.banka2_bek.actuary.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.actuary.dto.ActuaryInfoDto;
import rs.raf.banka2_bek.actuary.dto.UpdateActuaryLimitDto;
import rs.raf.banka2_bek.actuary.service.ActuaryService;

import java.util.List;

/**
 * Controller za upravljanje aktuarima.
 * Pristup: samo supervizori (i admini koji su automatski supervizori).
 *
 * TODO: Dodati u GlobalSecurityConfig:
 *   .requestMatchers("/actuaries/**").hasAnyRole("ADMIN", "SUPERVISOR")
 */
@RestController
@RequestMapping("/actuaries")
@RequiredArgsConstructor
public class ActuaryController {

    private final ActuaryService actuaryService;

    /**
     * GET /actuaries/agents - Lista svih agenata
     * Filtriranje po email, firstName, lastName
     */
    @GetMapping("/agents")
    public ResponseEntity<List<ActuaryInfoDto>> getAgents(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String position) {
        return ResponseEntity.ok(actuaryService.getAgents(email, firstName, lastName, position));
    }

    /**
     * GET /actuaries/{employeeId} - Aktuarski podaci za zaposlenog
     */
    @GetMapping("/{employeeId}")
    public ResponseEntity<ActuaryInfoDto> getActuaryInfo(@PathVariable Long employeeId) {
        return ResponseEntity.ok(actuaryService.getActuaryInfo(employeeId));
    }

    /**
     * PATCH /actuaries/{employeeId}/limit - Promena limita i needApproval
     * Samo supervizor moze da menja, samo za agente.
     */
    @PatchMapping("/{employeeId}/limit")
    public ResponseEntity<ActuaryInfoDto> updateAgentLimit(
            @PathVariable Long employeeId,
            @RequestBody UpdateActuaryLimitDto dto) {
        return ResponseEntity.ok(actuaryService.updateAgentLimit(employeeId, dto));
    }

    /**
     * PATCH /actuaries/{employeeId}/reset-limit - Reset usedLimit na 0
     * Supervizor rucno resetuje dnevni limit agenta.
     */
    @PatchMapping("/{employeeId}/reset-limit")
    public ResponseEntity<ActuaryInfoDto> resetUsedLimit(@PathVariable Long employeeId) {
        return ResponseEntity.ok(actuaryService.resetUsedLimit(employeeId));
    }
}
