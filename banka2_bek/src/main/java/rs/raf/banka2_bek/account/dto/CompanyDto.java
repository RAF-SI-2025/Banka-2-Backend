package rs.raf.banka2_bek.account.dto;

import lombok.Data;

@Data
public class CompanyDto {
    private String name;
    private String registrationNumber; // Matični broj
    private String taxId;              // PIB
    private String activityCode;       // Šifra delatnosti
    private String address;
}