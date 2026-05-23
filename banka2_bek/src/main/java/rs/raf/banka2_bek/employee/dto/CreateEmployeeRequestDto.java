package rs.raf.banka2_bek.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
public class CreateEmployeeRequestDto {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotNull
    @Past(message = "Datum rodjenja mora biti u proslosti")
    private LocalDate dateOfBirth;

    @NotBlank
    private String gender;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Pattern(
            regexp = "^\\+?[0-9]{6,20}$",
            message = "Telefon sme da sadrzi samo cifre uz opcioni vodeci znak +"
    )
    private String phone;

    @NotBlank
    private String address;

    @NotBlank
    private String username;

    @NotBlank
    private String position;

    @NotBlank
    private String department;

    private Boolean active;

    private Set<String> permissions;
}
