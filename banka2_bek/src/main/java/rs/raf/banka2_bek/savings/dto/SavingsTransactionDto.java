package rs.raf.banka2_bek.savings.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SavingsTransactionDto {
    private Long id;
    private Long depositId;
    private String type;
    private BigDecimal amount;
    private String currencyCode;
    private LocalDate processedDate;
    private Long resultingTransactionId;
    private String description;
    private LocalDateTime createdAt;
}
