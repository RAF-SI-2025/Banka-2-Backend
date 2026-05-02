package rs.raf.banka2_bek.investmentfund.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/*
================================================================================
 TODO — DTO-OVI ZA INVESTICIONE FONDOVE (GRUPISANO)
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 160-364
--------------------------------------------------------------------------------
 1. InvestmentFundSummaryDto — za Discovery tabelu (mali payload)
 2. InvestmentFundDetailDto — za Detaljan prikaz (sve info + holdings + perf)
 3. CreateFundDto — FE salje na POST /funds
 4. InvestFundDto — FE salje na POST /funds/{id}/invest (uplata)
 5. WithdrawFundDto — FE salje na POST /funds/{id}/withdraw (povlacenje)
 6. ClientFundPositionDto — jedna pozicija klijenta
 7. FundHoldingDto — jedna hartija u fondu (denormalizovano iz Portfolio)
 8. FundPerformancePointDto — tacka u grafiku performansi
 9. ClientFundTransactionDto — istorija transakcija
================================================================================
*/
public final class InvestmentFundDtos {

    private InvestmentFundDtos() {}

    /** TODO — Summary za Discovery stranicu */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class InvestmentFundSummaryDto {
        private Long id;
        private String name;
        private String description;
        private BigDecimal minimumContribution;
        private BigDecimal fundValue;
        private BigDecimal profit;
        private String managerName;
        private LocalDate inceptionDate;
    }

    /** TODO — Detaljan prikaz fonda */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class InvestmentFundDetailDto {
        private Long id;
        private String name;
        private String description;
        private String managerName;
        private Long managerEmployeeId;
        private BigDecimal fundValue;
        private BigDecimal liquidAmount;
        private BigDecimal profit;
        private BigDecimal minimumContribution;
        private String accountNumber;
        private List<FundHoldingDto> holdings;
        private List<FundPerformancePointDto> performance;
        private LocalDate inceptionDate;
    }

    /** TODO — Jedna hartija u fondu */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class FundHoldingDto {
        private Long listingId;
        private String ticker;
        private String name;
        private Integer quantity;
        private BigDecimal currentPrice;
        private BigDecimal change;
        private Long volume;
        private BigDecimal initialMarginCost;
        private LocalDate acquisitionDate;
    }

    /** TODO — Tacka u grafiku performansi */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class FundPerformancePointDto {
        private LocalDate date;
        private BigDecimal fundValue;
        private BigDecimal profit;
    }

    /** TODO — Create fund (samo supervizor) */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreateFundDto {
        @NotBlank @Size(min = 3, max = 128) private String name;
        @Size(max = 1024) private String description;
        @NotNull @Positive private BigDecimal minimumContribution;
    }

    /** TODO — Investicija u fond */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class InvestFundDto {
        @NotNull @Positive private BigDecimal amount;
        @NotBlank private String currency; // da bi BE mogao konvertovati u RSD
        @NotNull private Long sourceAccountId;
    }

    /** TODO — Povlacenje iz fonda */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class WithdrawFundDto {
        /** null znaci "sva moja pozicija" (vidi spec liniju 342) */
        private BigDecimal amount;
        @NotNull private Long destinationAccountId;
    }

    /** TODO — Pozicija jednog klijenta u jednom fondu (FE view) */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ClientFundPositionDto {
        private Long id;
        private Long fundId;
        private String fundName;
        private Long userId;
        private String userRole;
        private String userName;
        private BigDecimal totalInvested;
        private BigDecimal currentValue;      // izvedeno
        private BigDecimal percentOfFund;     // izvedeno
        private BigDecimal profit;            // izvedeno
        private LocalDateTime lastModifiedAt;
    }

    /** TODO — Istorijska transakcija */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ClientFundTransactionDto {
        private Long id;
        private Long fundId;
        private String fundName;
        private Long userId;
        private String userName;
        private BigDecimal amountRsd;
        private String sourceAccountNumber;
        private boolean inflow;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
        private String failureReason;
    }
    public enum Granularity {
        DAY, WEEK, MONTH, QUARTER, YEAR
    }
}
