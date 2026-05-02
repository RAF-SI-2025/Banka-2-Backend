package rs.raf.banka2_bek.investmentfund.dto;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * Pozicija klijenta u fondu (lista za Moj portfolio / Profit banke).
 * clientId moze biti i klijent koji predstavlja vlasnika banke
 * (Napomene 1 i 2 — to moze biti sama banka kao pravno lice).
 *
 * managerName i managerLastname se ukljucuju jer ih Profit Banke „Pozicije
 * u fondovima“ stranica direktno prikazuje (Celina 4 — kolona „ime i prezime
 * menadzera fonda“).
 *
 * Izvedena polja koja Celina 4 trazi za FE prikaz (ProcenatFonda,
 * TrenutnaVrednostPozicije, Profit) NE puni T12 — popunjava ih T7/T10 sloj
 * preko FundValueCalculator helper servisa pre vracanja FE-u.
 */
@Builder
public record ClientFundPositionDto(
        Long fundId,
        String fundName,
        String managerName,
        String managerLastname,
        Long clientId,
        BigDecimal totalInvestedAmountRsd
) {}
