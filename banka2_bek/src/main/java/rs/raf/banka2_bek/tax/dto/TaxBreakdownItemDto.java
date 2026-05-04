package rs.raf.banka2_bek.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Per-listing stavka u breakdown-u poreza za korisnika.
 *
 * Spec Celina 3 §516-518 + Napomena 1: profit po hartiji * 15% = porez na
 * tu hartiju. Ovaj DTO se vraca uz {@link TaxRecordDto} kako bi UI mogao
 * da prikaze "AAPL: +$50 → 7.50 RSD" umesto cistog totala.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaxBreakdownItemDto {
    private Long listingId;
    private String ticker;
    /** Valuta listinga pre konverzije u RSD. */
    private String listingCurrency;
    /** Profit u valuti listinga; moze biti negativan. */
    private BigDecimal profitNative;
    /** Profit u RSD (mid-rate konverzija); negativni se ne oporezuju. */
    private BigDecimal profitRsd;
    /** Porez = 15% * max(0, profitRsd), u RSD. */
    private BigDecimal taxOwed;
}
