package rs.raf.banka2_bek.investmentfund.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserResolver;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.*;
import rs.raf.banka2_bek.investmentfund.service.InvestmentFundService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/funds")
@RequiredArgsConstructor
public class InvestmentFundController {

    private final InvestmentFundService investmentFundService;
    private final UserResolver userResolver;

    @GetMapping
    public ResponseEntity<List<InvestmentFundSummaryDto>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) java.math.BigDecimal minContribution,
            @RequestParam(required = false) java.math.BigDecimal maxContribution,
            @RequestParam(required = false) java.math.BigDecimal minFundValue,
            @RequestParam(required = false) java.math.BigDecimal maxFundValue,
            @RequestParam(required = false) java.math.BigDecimal minProfit,
            @RequestParam(required = false) java.math.BigDecimal maxProfit) {
        return ResponseEntity.ok(investmentFundService.listDiscovery(
                search, sort, direction,
                minContribution, maxContribution,
                minFundValue, maxFundValue,
                minProfit, maxProfit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvestmentFundDetailDto> details(@PathVariable Long id) {
        return ResponseEntity.ok(investmentFundService.getFundDetails(id));
    }

    @GetMapping("/{id}/performance")
    public ResponseEntity<List<FundPerformancePointDto>> performance(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false, defaultValue = "MONTH") Granularity granularity) {
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusMonths(3);
        return ResponseEntity.ok(investmentFundService.getPerformance(id, effectiveFrom, effectiveTo, granularity));
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<ClientFundTransactionDto>> transactions(@PathVariable Long id) {
        UserContext current = userResolver.resolveCurrent();
        return ResponseEntity.ok(investmentFundService.listTransactions(id, current.userId(), current.userRole()));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'SUPERVISOR')")
    public ResponseEntity<InvestmentFundDetailDto> create(@Valid @RequestBody CreateFundDto dto) {
        UserContext current = userResolver.resolveCurrent();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(investmentFundService.createFund(dto, current.userId()));
    }

    @PostMapping("/{id}/invest")
    public ResponseEntity<ClientFundPositionDto> invest(
            @PathVariable Long id, @Valid @RequestBody InvestFundDto dto) {
        UserContext current = userResolver.resolveCurrent();
        return ResponseEntity.ok(investmentFundService.invest(id, dto, current.userId(), current.userRole()));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<ClientFundTransactionDto> withdraw(
            @PathVariable Long id, @Valid @RequestBody WithdrawFundDto dto) {
        UserContext current = userResolver.resolveCurrent();
        return ResponseEntity.ok(investmentFundService.withdraw(id, dto, current.userId(), current.userRole()));
    }

    @GetMapping("/my-positions")
    public ResponseEntity<List<ClientFundPositionDto>> myPositions() {
        UserContext current = userResolver.resolveCurrent();
        return ResponseEntity.ok(investmentFundService.listMyPositions(current.userId(), current.userRole()));
    }

    @GetMapping("/bank-positions")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'SUPERVISOR')")
    public ResponseEntity<List<ClientFundPositionDto>> bankPositions() {
        return ResponseEntity.ok(investmentFundService.listBankPositions());
    }

    /**
     * Ad-hoc prebacivanje vlasnistva fonda na drugog supervizora.
     * Samo admin (Celina 4 §324: "samo admini mogu da dodaju i uklanjaju
     * permisije isAgent, isSupervisor"). Bulk varijanta (kad admin oduzme
     * isSupervisor permisiju) se i dalje desava automatski kroz
     * {@code EmployeeServiceImpl.updateEmployee}.
     */
    @PostMapping("/{id}/reassign-manager")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    public ResponseEntity<InvestmentFundDetailDto> reassignManager(
            @PathVariable Long id,
            @Valid @RequestBody ReassignFundManagerDto dto) {
        return ResponseEntity.ok(
                investmentFundService.reassignSingleFundManager(id, dto.getNewManagerEmployeeId()));
    }
}