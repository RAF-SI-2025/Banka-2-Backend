package rs.raf.banka2_bek.stock.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ListingDailyPriceDto {
    @Schema(description = "Datum za koji su evidentirani podaci")
    private LocalDate date;

    @Schema(description = "Zatvarajuca cena tog dana")
    private BigDecimal price;

    @Schema(description = "Najvisa cena tog dana")
    private BigDecimal high;

    @Schema(description = "Najniza cena tog dana")
    private BigDecimal low;

    @Schema(description = "Promena cene u odnosu na prethodni dan")
    private BigDecimal change;

    @Schema(description = "Obim trgovanja tog dana")
    private Long volume;
}
