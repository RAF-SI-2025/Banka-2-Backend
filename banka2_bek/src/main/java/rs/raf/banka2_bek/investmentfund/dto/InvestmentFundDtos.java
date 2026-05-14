package rs.raf.banka2_bek.investmentfund.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class InvestmentFundDtos {

    private InvestmentFundDtos() {}

    /** Summary za Discovery stranicu */
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

    /** Detaljan prikaz fonda */
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
        private Long accountId;
        private String accountNumber;
        private List<FundHoldingDto> holdings;
        private List<FundPerformancePointDto> performance;
        private LocalDate inceptionDate;
    }

    /** Jedna hartija u fondu */
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

    /** Tacka u grafiku performansi */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class FundPerformancePointDto {
        private LocalDate date;
        private BigDecimal fundValue;
        private BigDecimal profit;
    }

    /** Create fund (samo supervizor) */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreateFundDto {
        @NotBlank @Size(min = 3, max = 128) private String name;
        @Size(max = 1024) private String description;
        @NotNull @Positive private BigDecimal minimumContribution;
    }

    /** Investicija u fond */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class InvestFundDto {
        @NotNull @Positive private BigDecimal amount;
        @NotBlank private String currency; // da bi BE mogao konvertovati u RSD
        @NotNull private Long sourceAccountId;
    }

    /** Povlacenje iz fonda */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class WithdrawFundDto {
        /** null znaci "sva moja pozicija" (vidi spec liniju 342) */
        private BigDecimal amount;
        @NotNull private Long destinationAccountId;
    }

    /** Pozicija jednog klijenta u jednom fondu (FE view) */
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

    /** Istorijska transakcija */
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

    /** Ad-hoc prebacivanje vlasnistva fonda (samo admin, Celina 4 §324). */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ReassignFundManagerDto {
        @NotNull
        @Positive
        private Long newManagerEmployeeId;
    }
}
