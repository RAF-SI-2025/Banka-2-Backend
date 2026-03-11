package rs.raf.banka2_bek.employee.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
public class UpdateEmployeeRequestDto {
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String gender;
    private String phone;
    private String address;
    private String position;
    private String department;
    private Boolean active;
    private Set<String> permissions;
}
