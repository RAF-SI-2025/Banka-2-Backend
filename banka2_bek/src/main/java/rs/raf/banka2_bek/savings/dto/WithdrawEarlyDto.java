package rs.raf.banka2_bek.savings.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class WithdrawEarlyDto {
    @NotBlank
    @Size(min = 6, max = 6)
    private String otpCode;
}
