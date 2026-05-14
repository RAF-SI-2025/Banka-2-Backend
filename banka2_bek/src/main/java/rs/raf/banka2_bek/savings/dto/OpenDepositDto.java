package rs.raf.banka2_bek.savings.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OpenDepositDto {

    @NotNull
    private Long sourceAccountId;

    @NotNull
    private Long linkedAccountId;

    @NotNull
    @DecimalMin(value = "0.01", message = "Iznos mora biti pozitivan")
    private BigDecimal principalAmount;

    @NotNull
    @Min(value = 3, message = "Rok mora biti najmanje 3 meseca")
    @Max(value = 36, message = "Rok mora biti najvise 36 meseci")
    private Integer termMonths;

    @NotNull
    private Boolean autoRenew;

    @NotBlank(message = "OTP kod je obavezan")
    @Size(min = 6, max = 6, message = "OTP kod mora imati 6 cifara")
    private String otpCode;
}
