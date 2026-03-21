package rs.raf.banka2_bek.order.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.order.dto.CreateOrderDto;
import rs.raf.banka2_bek.order.dto.OrderDto;
import rs.raf.banka2_bek.order.service.OrderService;

/**
 * Controller za kreiranje i upravljanje orderima.
 *
 * TODO: Dodati u GlobalSecurityConfig:
 *   .requestMatchers(HttpMethod.POST, "/orders").hasAnyRole("ADMIN", "CLIENT", "EMPLOYEE")
 *   .requestMatchers(HttpMethod.GET, "/orders/my").hasAnyRole("ADMIN", "CLIENT", "EMPLOYEE")
 *   .requestMatchers(HttpMethod.GET, "/orders").hasAnyRole("ADMIN")           // supervizor portal
 *   .requestMatchers("/orders/{id}/approve", "/orders/{id}/decline").hasAnyRole("ADMIN")
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /orders - Kreiranje novog ordera (BUY ili SELL)
     * Pristup: aktuari i klijenti sa permisijom za trgovinu.
     */
    @PostMapping
    public ResponseEntity<OrderDto> createOrder(@Valid @RequestBody CreateOrderDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(dto));
    }

    /**
     * GET /orders - Pregled svih ordera (supervizor portal)
     * Filtriranje po statusu: ALL, PENDING, APPROVED, DECLINED, DONE
     */
    @GetMapping
    public ResponseEntity<Page<OrderDto>> getAllOrders(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.getAllOrders(status, page, size));
    }

    /**
     * GET /orders/my - Moji orderi (za korisnika)
     */
    @GetMapping("/my")
    public ResponseEntity<Page<OrderDto>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.getMyOrders(page, size));
    }

    /**
     * GET /orders/{id} - Detalji jednog ordera
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    /**
     * PATCH /orders/{id}/approve - Supervizor odobrava order
     */
    @PatchMapping("/{id}/approve")
    public ResponseEntity<OrderDto> approveOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.approveOrder(id));
    }

    /**
     * PATCH /orders/{id}/decline - Supervizor odbija order
     */
    @PatchMapping("/{id}/decline")
    public ResponseEntity<OrderDto> declineOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.declineOrder(id));
    }
}
