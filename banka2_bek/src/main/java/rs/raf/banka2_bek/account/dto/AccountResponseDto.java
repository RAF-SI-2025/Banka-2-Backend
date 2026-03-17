package rs.raf.banka2_bek.account.dto;

import lombok.*;
import rs.raf.banka2_bek.company.dto.CompanyDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

//TODO DA LI OVO MORA DA SE POKLAPA SA KLASOM ACCOUNT ILI NE
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponseDto {
    private Long id;
    private String name;
    private String accountNumber;
    private String accountType;
    private String accountSubType;
    private String status;

    private String ownerName; //vlasnik

    //stanja
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal reservedFunds;

    private String currencyCode;

    //limiti
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;

    private LocalDate expirationDate;
    private LocalDateTime createdAt;

    //zaposleni koji je kreirao
    private String createdByEmployee;

    // Podaci o firmi (samo za poslovne racune, inace null)
    private CompanyDto company;

}
