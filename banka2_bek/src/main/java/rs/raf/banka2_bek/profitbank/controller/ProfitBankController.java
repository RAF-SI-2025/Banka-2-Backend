package rs.raf.banka2_bek.profitbank.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.ClientFundPositionDto;
import rs.raf.banka2_bek.investmentfund.service.InvestmentFundService;
import rs.raf.banka2_bek.profitbank.dto.ProfitBankDtos.ActuaryProfitDto;
import rs.raf.banka2_bek.profitbank.service.ActuaryProfitService;

import java.util.List;

/**
 * P6 — Spec Celina 4 (Nova) §4393-4645: Portal "Profit Banke" za supervizore.
 *
 * Dva endpointa:
 *   GET /profit-bank/actuary-performance  — spisak aktuara + profit u RSD
 *   GET /profit-bank/fund-positions       — fondovi u kojima banka ima udele
 *
 * Pristup samo supervizorima/adminima (vidi GlobalSecurityConfig).
 */
@RestController
@RequestMapping("/profit-bank")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ADMIN','SUPERVISOR')")
@RequiredArgsConstructor
public class ProfitBankController {

    private final ActuaryProfitService actuaryProfitService;
    private final InvestmentFundService investmentFundService;

    @GetMapping("/actuary-performance")
    public ResponseEntity<List<ActuaryProfitDto>> actuaryPerformance() {
        return ResponseEntity.ok(actuaryProfitService.listAllActuariesProfit());
    }

    @GetMapping("/fund-positions")
    public ResponseEntity<List<ClientFundPositionDto>> fundPositions() {
        // Banka kao klijent fonda (P9): ClientFundPosition gde je userId = ownerClientId
        // banke i userRole = "CLIENT" (vidi InvestmentFundService.listBankPositions).
        return ResponseEntity.ok(investmentFundService.listBankPositions());
    }

    // Buduci optional endpoint-i (Celina 4 (Nova) §4585-4628):
    //   POST /profit-bank/fund-positions/{fundId}/invest  — supervizor uplata u ime banke
    //   POST /profit-bank/fund-positions/{fundId}/withdraw — supervizor povlacenje u ime banke
    // InvestmentFundService.invest/withdraw vec pokriva ovaj scenario sa userRole=CLIENT i
    // userId=ownerClientId banke; dodatni endpointi su redundantni za trenutni demo flow.
}
