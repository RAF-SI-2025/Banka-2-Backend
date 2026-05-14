package rs.raf.banka2_bek.savings.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UpsertSavingsRateDto {
    @NotBlank
    private String currencyCode;

    @NotNull @Min(3) @Max(36)
    private Integer termMonths;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax(value = "50.0", message = "Maksimalna stopa je 50% p.a.")
    private BigDecimal annualRate;
}
