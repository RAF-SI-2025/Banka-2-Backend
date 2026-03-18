package rs.raf.banka2_bek.company.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder

// TODO actual companyDto - was added to implement accountService
public class CompanyDto {
    private Long id;
    private String name;
    private String registrationNumber;
    private String taxNumber;
    private String activityCode;
    private String address;
}
