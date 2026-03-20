package rs.raf.banka2_bek.account.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountRequestDto {
    private String accountType;
    private String accountSubtype;
    private String currency;
    private BigDecimal initialDeposit;
    private Boolean createCard;
}
