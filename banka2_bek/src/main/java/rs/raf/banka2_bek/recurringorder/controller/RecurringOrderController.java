package rs.raf.banka2_bek.recurringorder.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.recurringorder.dto.CreateRecurringOrderDto;
import rs.raf.banka2_bek.recurringorder.dto.RecurringOrderDto;
import rs.raf.banka2_bek.recurringorder.service.RecurringOrderService;

import java.util.List;

// ============================================================
// TODO [B8 - Trajni nalozi (DCA / RecurringOrder) | Nosilac: Nikola Djurovic]
//
// REST kontroler za upravljanje trajnim nalozima.
// Base path: /recurring-orders
//
// IMPLEMENTIRATI — dodati sledece endpoint metode:
//
//   POST /recurring-orders
//       @Valid @RequestBody CreateRecurringOrderDto dto
//       -> ResponseEntity<RecurringOrderDto> (HTTP 201 Created)
//       -> Poziva RecurringOrderService.create(dto)
//       -> Dostupno: autentifikovani klijenti i zaposleni (actuary)
//       -> Dodati u GlobalSecurityConfig:
//            .requestMatchers(HttpMethod.POST, "/recurring-orders").authenticated()
//
//   GET /recurring-orders
//       -> ResponseEntity<List<RecurringOrderDto>> (HTTP 200)
//       -> Poziva RecurringOrderService.listMy()
//       -> Vraca samo naloge trenutno ulogovanog korisnika
//
//   GET /recurring-orders/{id}
//       -> ResponseEntity<RecurringOrderDto> (HTTP 200)
//       -> Poziva RecurringOrderService.getById(id)
//       -> Service baca 404 ako nalog ne postoji, 403 ako nije vlasnik
//
//   PATCH /recurring-orders/{id}/pause
//       -> ResponseEntity<RecurringOrderDto> (HTTP 200)
//       -> Poziva RecurringOrderService.pause(id)
//       -> Pauzira aktivni nalog (active = false), ne brise ga
//
//   PATCH /recurring-orders/{id}/resume
//       -> ResponseEntity<RecurringOrderDto> (HTTP 200)
//       -> Poziva RecurringOrderService.resume(id)
//       -> Reaktivira pauzirani nalog (active = true);
//          nextRun se postavlja na now + 1 cadence korak kako se ne bi
//          odmah izvrsio u sledecem scheduler prolazu
//
//   DELETE /recurring-orders/{id}
//       -> ResponseEntity<Void> (HTTP 204 No Content)
//       -> Poziva RecurringOrderService.cancel(id)
//       -> Otkazuje i uklanja nalog
//
// Injectovati RecurringOrderService kao final polje.
// Koristiti @PathVariable Long id i ResponseEntity<> konzistentno sa
// ostalim kontrolerima u projektu (videti SavingsDepositController).
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B8.
// ============================================================
@RestController
@RequestMapping("/recurring-orders")
@RequiredArgsConstructor
public class RecurringOrderController {

    private final RecurringOrderService recurringOrderService;

    @PostMapping
    public ResponseEntity<RecurringOrderDto> create(@Valid @RequestBody CreateRecurringOrderDto dto) {
        RecurringOrderDto created = recurringOrderService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<RecurringOrderDto>> listMy() {
        List<RecurringOrderDto> orders = recurringOrderService.listMy();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecurringOrderDto> getById(@PathVariable Long id) {
        RecurringOrderDto order = recurringOrderService.getById(id);
        return ResponseEntity.ok(order);
    }

    @PatchMapping("/{id}/pause")
    public ResponseEntity<RecurringOrderDto> pause(@PathVariable Long id) {
        RecurringOrderDto paused = recurringOrderService.pause(id);
        return ResponseEntity.ok(paused);
    }

    @PatchMapping("/{id}/resume")
    public ResponseEntity<RecurringOrderDto> resume(@PathVariable Long id) {
        RecurringOrderDto resumed = recurringOrderService.resume(id);
        return ResponseEntity.ok(resumed);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        recurringOrderService.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
