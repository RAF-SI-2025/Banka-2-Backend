package rs.raf.banka2_bek.berza.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.berza.dto.ExchangeDto;
import rs.raf.banka2_bek.berza.service.ExchangeManagementService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST kontroler za upravljanje berzama.
 *
 * Endpointovi:
 *   GET  /exchanges              - lista svih aktivnih berzi sa statusom
 *   GET  /exchanges/{acronym}    - detalji jedne berze
 *   PATCH /exchanges/{acronym}/test-mode - ukljuci/iskljuci test mode (samo admin)
 *
 * Specifikacija: Celina 3 - Berza
 */
@RestController
@RequestMapping("/exchanges")
@RequiredArgsConstructor
public class ExchangeManagementController {

    private final ExchangeManagementService exchangeManagementService;

    /**
     * GET /exchanges
     * Vraca listu svih aktivnih berzi sa computed statusom (isOpen, currentLocalTime, nextOpenTime).
     *
     */
    @GetMapping
    public ResponseEntity<List<ExchangeDto>> getAllExchanges() {
        return ResponseEntity.ok(exchangeManagementService.getAllExchanges());
    }

    @GetMapping("/{acronym}")
    public ResponseEntity<ExchangeDto> getByAcronym(@PathVariable String acronym) {
        return ResponseEntity.ok(exchangeManagementService.getByAcronym(acronym));
    }

    @PatchMapping("/{acronym}/test-mode")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> setTestMode(
            @PathVariable String acronym,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", false);
        exchangeManagementService.setTestMode(acronym, enabled);
        return ResponseEntity.ok(Map.of("message", "Test mode set to " + enabled + " for " + acronym));
    }

    /**
     * GET /exchanges/{acronym}/holidays
     * Vraca listu praznika za berzu.
     */
    @GetMapping("/{acronym}/holidays")
    public ResponseEntity<Set<LocalDate>> getHolidays(@PathVariable String acronym) {
        return ResponseEntity.ok(exchangeManagementService.getHolidays(acronym));
    }

    /**
     * PUT /exchanges/{acronym}/holidays
     * Postavlja kompletnu listu praznika za berzu (zamenjuje postojece).
     */
    @PutMapping("/{acronym}/holidays")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> setHolidays(
            @PathVariable String acronym,
            @RequestBody Set<LocalDate> holidays) {
        exchangeManagementService.setHolidays(acronym, holidays);
        return ResponseEntity.ok(Map.of("message", "Set " + holidays.size() + " holidays for " + acronym));
    }

    /**
     * POST /exchanges/{acronym}/holidays
     * Dodaje pojedinacni praznik za berzu.
     */
    @PostMapping("/{acronym}/holidays")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> addHoliday(
            @PathVariable String acronym,
            @RequestBody Map<String, String> body) {
        LocalDate date = LocalDate.parse(body.get("date"));
        exchangeManagementService.addHoliday(acronym, date);
        return ResponseEntity.ok(Map.of("message", "Added holiday " + date + " for " + acronym));
    }

    /**
     * DELETE /exchanges/{acronym}/holidays/{date}
     * Uklanja praznik za berzu.
     */
    @DeleteMapping("/{acronym}/holidays/{date}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> removeHoliday(
            @PathVariable String acronym,
            @PathVariable LocalDate date) {
        exchangeManagementService.removeHoliday(acronym, date);
        return ResponseEntity.ok(Map.of("message", "Removed holiday " + date + " for " + acronym));
    }
}
