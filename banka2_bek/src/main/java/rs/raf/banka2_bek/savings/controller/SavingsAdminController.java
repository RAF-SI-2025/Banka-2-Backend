package rs.raf.banka2_bek.savings.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.savings.dto.*;
import rs.raf.banka2_bek.savings.entity.SavingsDepositStatus;
import rs.raf.banka2_bek.savings.service.SavingsAdminService;
import rs.raf.banka2_bek.savings.service.SavingsInterestRateService;

import java.util.List;

@RestController
@RequestMapping("/admin/savings")
@RequiredArgsConstructor
public class SavingsAdminController {

    private final SavingsAdminService adminService;
    private final SavingsInterestRateService rateService;

    @GetMapping("/deposits")
    public ResponseEntity<Page<SavingsDepositDto>> listAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SavingsDepositStatus statusEnum = status != null ? SavingsDepositStatus.valueOf(status) : null;
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(adminService.listAll(statusEnum, clientId, pageable));
    }

    @GetMapping("/rates")
    public ResponseEntity<List<SavingsRateDto>> listAllRates() {
        return ResponseEntity.ok(rateService.listAll());
    }

    @PostMapping("/rates")
    public ResponseEntity<SavingsRateDto> upsertRate(@Valid @RequestBody UpsertSavingsRateDto dto) {
        return ResponseEntity.ok(rateService.upsert(dto));
    }
}
