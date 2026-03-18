package rs.raf.banka2_bek.account.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import rs.raf.banka2_bek.account.model.AccountSubtype;
import rs.raf.banka2_bek.account.model.AccountType;

import java.math.BigDecimal;

@Data
public class CreateAccountDto {
    @NotNull(message = "Tip računa je obavezan")
    private AccountType accountType;

    @NotNull(message = "Podtip računa je obavezan")
    private AccountSubtype accountSubtype;

    @NotNull(message = "Valuta je obavezna")
    private Long currencyId;

    @NotNull(message = "Početno stanje ne sme biti prazno")
    @PositiveOrZero(message = "Početno stanje mora biti 0 ili veće")
    private BigDecimal initialBalance;


    private Long clientId;       // Obavezno ako nije poslovni
    private CompanyDto company;  // Obavezno ako je poslovni
}
