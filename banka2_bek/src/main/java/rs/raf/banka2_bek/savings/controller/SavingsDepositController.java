package rs.raf.banka2_bek.savings.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.savings.dto.*;
import rs.raf.banka2_bek.savings.service.SavingsDepositService;
import rs.raf.banka2_bek.savings.service.SavingsInterestRateService;

import java.util.List;

@RestController
@RequestMapping("/savings")
@RequiredArgsConstructor
public class SavingsDepositController {

    private final SavingsDepositService depositService;
    private final SavingsInterestRateService rateService;

    @PostMapping("/deposits")
    public ResponseEntity<SavingsDepositDto> openDeposit(@Valid @RequestBody OpenDepositDto dto) {
        return ResponseEntity.ok(depositService.openDeposit(dto));
    }

    @GetMapping("/deposits/my")
    public ResponseEntity<List<SavingsDepositDto>> listMy() {
        return ResponseEntity.ok(depositService.listMyDeposits());
    }

    @GetMapping("/deposits/{id}")
    public ResponseEntity<SavingsDepositDto> getDeposit(@PathVariable Long id) {
        return ResponseEntity.ok(depositService.getDeposit(id));
    }

    @GetMapping("/deposits/{id}/transactions")
    public ResponseEntity<List<SavingsTransactionDto>> getTransactions(@PathVariable Long id) {
        return ResponseEntity.ok(depositService.listDepositTransactions(id));
    }

    @PatchMapping("/deposits/{id}/auto-renew")
    public ResponseEntity<SavingsDepositDto> toggleAutoRenew(@PathVariable Long id,
                                                              @Valid @RequestBody ToggleAutoRenewDto dto) {
        return ResponseEntity.ok(depositService.toggleAutoRenew(id, dto));
    }

    @PostMapping("/deposits/{id}/withdraw-early")
    public ResponseEntity<SavingsDepositDto> withdrawEarly(@PathVariable Long id,
                                                            @Valid @RequestBody WithdrawEarlyDto dto) {
        return ResponseEntity.ok(depositService.withdrawEarly(id, dto));
    }

    @GetMapping("/rates")
    public ResponseEntity<List<SavingsRateDto>> getRates(
            @RequestParam(required = false) String currency) {
        return ResponseEntity.ok(rateService.listActive(currency));
    }
}
