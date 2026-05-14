package rs.raf.banka2_bek.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
    @Min(value = 1, message = "Quantity and contractSize must be > 0")
    private Integer quantity;

    @Min(value = 1, message = "Contract size must be > 0")
    private Integer contractSize = 1;

    @NotNull(message = "Smer je obavezan (BUY, SELL)")
    private String direction;

    private BigDecimal limitValue;  // za LIMIT i STOP_LIMIT
    private BigDecimal stopValue;   // za STOP i STOP_LIMIT
    private boolean allOrNone;      // AON flag
    private boolean margin;         // Margin flag

    /**
     * Racun sa kog se skida novac (BUY) ili na koji se uplacuje (SELL).
     * Required kada nije fund-trade. Kada je fundId != null, BE
     * resolvuje racun preko fund.accountId pa je accountId opcioni.
     * Validacija (XOR) se sprovodi u OrderServiceImpl.createOrder.
     */
    private Long accountId;

    @NotBlank(message = "Verifikacioni kod je obavezan")
    private String otpCode;

    /**
     * P3 — Spec Celina 4 (Nova) §3883-3964: kada supervizor kupuje hartiju
     * u ime investicionog fonda, FE salje fundId. BE validira da:
     *  - poziva ga supervizor
     *  - fund postoji i supervizor je manager tog fonda
     *  - fund.account.balance >= order.cost
     * Null znaci da nije fond-trgovina (klijent ili banka).
     */
    private Long fundId;
}
