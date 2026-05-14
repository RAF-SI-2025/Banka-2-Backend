package rs.raf.banka2_bek.savings.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ToggleAutoRenewDto {
    @NotNull
    private Boolean autoRenew;
}
