package rs.raf.banka2_bek.stock.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ListingDailyPriceDto {
    private LocalDate date;
    private BigDecimal price;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal change;
    private Long volume;
}
