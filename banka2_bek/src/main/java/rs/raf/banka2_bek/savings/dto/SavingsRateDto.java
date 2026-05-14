package rs.raf.banka2_bek.savings.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SavingsRateDto {
    private Long id;
    private String currencyCode;
    private Integer termMonths;
    private BigDecimal annualRate;
    private Boolean active;
    private LocalDate effectiveFrom;
}
