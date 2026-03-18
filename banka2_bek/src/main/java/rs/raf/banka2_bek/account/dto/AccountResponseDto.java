package rs.raf.banka2_bek.account.dto;

import rs.raf.banka2_bek.company.dto.CompanyDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponseDto {
    private Long id;

    private String accountNumber;
    private String accountType;
    private String accountSubType;
    private String name;
    private String status;

    // vlasnik
    private String ownerName;

    // stanja
    private BigDecimal availableBalance;
    private BigDecimal balance;
    private BigDecimal reservedFunds;

    private String currencyCode;

    // limiti
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;

    private LocalDate expirationDate;
    private LocalDateTime createdAt;

    // zaposleni koji je kreirao
    private String createdByEmployee;

    // podaci o firmi (samo za poslovne racune, inace null)
    private CompanyDto company;

}
