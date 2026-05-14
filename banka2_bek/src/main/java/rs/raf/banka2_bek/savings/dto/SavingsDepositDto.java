package rs.raf.banka2_bek.savings.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SavingsDepositDto {
    private Long id;
    private Long clientId;
    private String clientName;
    private Long linkedAccountId;
    private String linkedAccountNumber;
    private BigDecimal principalAmount;
    private String currencyCode;
    private Integer termMonths;
    private BigDecimal annualInterestRate;
    private LocalDate startDate;
    private LocalDate maturityDate;
    private LocalDate nextInterestPaymentDate;
    private BigDecimal totalInterestPaid;
    private Boolean autoRenew;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
