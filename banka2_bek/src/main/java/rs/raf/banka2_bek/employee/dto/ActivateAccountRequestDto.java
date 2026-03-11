package rs.raf.banka2_bek.employee.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActivateAccountRequestDto {

    @NotBlank
    private String token;
}
