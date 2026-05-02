package rs.raf.banka2_bek.investmentfund.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.*;
import rs.raf.banka2_bek.investmentfund.service.InvestmentFundService;

import java.time.LocalDate;
import java.util.List;

/*
================================================================================
 TODO — REST ENDPOINTI ZA INVESTICIONE FONDOVE
 Zaduzen: BE tim
--------------------------------------------------------------------------------
 ENDPOINTI I SECURITY:
   GET   /funds                      authenticated (klijenti + aktuari; discovery)
   GET   /funds/{id}                 authenticated (detalj)
   GET   /funds/{id}/performance     authenticated
   GET   /funds/{id}/transactions    authenticated (audit; supervizori svi, klijenti samo svoj fond-id pair)
   POST  /funds                      ADMIN, SUPERVISOR (create)
   POST  /funds/{id}/invest          authenticated (klijent iz svog racuna; supervizor iz bankinog)
   POST  /funds/{id}/withdraw        authenticated (klijent sa svoje pozicije; supervizor sa bankine)
   GET   /funds/my-positions         authenticated (moji udeli)
   GET   /funds/bank-positions       ADMIN, SUPERVISOR (za Profit Banke portal)

 SECURITY napomena:
  - "/funds" i "/funds/**" dodati u GlobalSecurityConfig pod authenticated.
  - POST /funds ima @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')") dodatno
    proveri u service da je SUPERVISOR po ActuaryInfo.

 ERROR HANDLING:
  - @RestControllerAdvice InvestmentFundExceptionHandler u istoj paketi.
  - EntityNotFoundException -> 404
  - IllegalArgumentException -> 400
  - InsufficientFundsException -> 400
  - AccessDeniedException -> 403
================================================================================
*/
@RestController
@RequestMapping("/funds")
@RequiredArgsConstructor
public class InvestmentFundController {

    private final InvestmentFundService investmentFundService;
    private final EmployeeRepository employeeRepository;

    @GetMapping
    public ResponseEntity<List<InvestmentFundSummaryDto>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return ResponseEntity.ok(investmentFundService.listDiscovery(search, sort, direction));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvestmentFundDetailDto> details(@PathVariable Long id) {
        return ResponseEntity.ok(investmentFundService.getFundDetails(id));
    }

    @GetMapping("/{id}/performance")
    public ResponseEntity<List<FundPerformancePointDto>> performance(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<ClientFundTransactionDto>> transactions(@PathVariable Long id) {
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'SUPERVISOR')")
    public ResponseEntity<InvestmentFundDetailDto> create(@Valid @RequestBody CreateFundDto dto) {
        Long supervisorId = getCurrentEmployeeId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(investmentFundService.createFund(dto, supervisorId));
    }

    @PostMapping("/{id}/invest")
    public ResponseEntity<ClientFundPositionDto> invest(
            @PathVariable Long id, @Valid @RequestBody InvestFundDto dto) {
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<ClientFundTransactionDto> withdraw(
            @PathVariable Long id, @Valid @RequestBody WithdrawFundDto dto) {
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/my-positions")
    public ResponseEntity<List<ClientFundPositionDto>> myPositions() {
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/bank-positions")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'SUPERVISOR')")
    public ResponseEntity<List<ClientFundPositionDto>> bankPositions() {
        throw new UnsupportedOperationException("TODO");
    }

    private Long getCurrentEmployeeId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        return employeeRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Employee with email " + email + " not found."))
                .getId();
    }
}
