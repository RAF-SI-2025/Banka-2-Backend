package rs.raf.banka2_bek.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOrderDto {

    @NotNull(message = "Listing ID je obavezan")
    private Long listingId;

    @NotNull(message = "Tip ordera je obavezan (MARKET, LIMIT, STOP, STOP_LIMIT)")
    private String orderType;

    @NotNull(message = "Kolicina je obavezna")
    @Min(value = 1, message = "Kolicina mora biti najmanje 1")
    private Integer quantity;

    @NotNull(message = "Smer je obavezan (BUY, SELL)")
    private String direction;

    // TODO: Opciona polja za razlicite tipove ordera
    private BigDecimal limitValue;  // za LIMIT i STOP_LIMIT
    private BigDecimal stopValue;   // za STOP i STOP_LIMIT
    private boolean allOrNone;      // AON flag
    private boolean margin;         // Margin flag

    // TODO: ID racuna sa kog se skida novac (obavezno za BUY)
    // - Klijent: ID njegovog racuna
    // - Zaposleni: ID bankinog racuna u valuti berze
    @NotNull(message = "ID racuna je obavezan")
    private Long accountId;
}
