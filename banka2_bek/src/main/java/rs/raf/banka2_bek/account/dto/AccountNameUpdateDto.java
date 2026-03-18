package rs.raf.banka2_bek.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AccountNameUpdateDto {

    @NotBlank(message = "Account name is required.")
    @Size(max = 64, message = "Account name must be at most 64 characters.")
    private String name;
}
