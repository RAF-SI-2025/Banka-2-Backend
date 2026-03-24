package rs.raf.banka2_bek.stock.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ListingDto {
    @Schema(description = "Interni ID hartije")
    private Long id;

    @Schema(description = "Ticker simbol hartije", example = "AAPL")
    private String ticker;

    @Schema(description = "Puni naziv hartije", example = "Apple Inc.")
    private String name;

    @Schema(description = "Akronim berze na kojoj se trguje", example = "NASDAQ")
    private String exchangeAcronym;

    @Schema(description = "Tip hartije: STOCK, FOREX ili FUTURES", example = "STOCK")
    private String listingType;

    @Schema(description = "Poslednja cena hartije")
    private BigDecimal price;

    @Schema(description = "Ask (ponudbena) cena")
    private BigDecimal ask;

    @Schema(description = "Bid (trazena) cena")
    private BigDecimal bid;

    @Schema(description = "Obim trgovanja (broj akcija/ugovora)")
    private Long volume;

    @Schema(description = "Promena cene u odnosu na prethodni dan")
    private BigDecimal priceChange;

    @Schema(description = "Procentualna promena cene; izvedeno: (priceChange / (price - priceChange)) * 100")
    private BigDecimal changePercent;

    @Schema(description = "Pocetni margin troskovi; izvedeno: maintenanceMargin * 1.1")
    private BigDecimal initialMarginCost;

    @Schema(description = "Margin odrzavanja; izvedeno na osnovu tipa hartije")
    private BigDecimal maintenanceMargin;

    // Stock-specific
    @Schema(description = "[STOCK] Broj akcija u opticaju")
    private Long outstandingShares;

    @Schema(description = "[STOCK] Dividendni prinos")
    private BigDecimal dividendYield;

    @Schema(description = "[STOCK] Trzisna kapitalizacija; izvedeno: outstandingShares * price")
    private BigDecimal marketCap;

    // Forex-specific
    @Schema(description = "[FOREX] Osnovna valuta valutnog para", example = "EUR")
    private String baseCurrency;

    @Schema(description = "[FOREX] Kvotna valuta valutnog para", example = "USD")
    private String quoteCurrency;

    @Schema(description = "[FOREX] Likvidnost para: HIGH, MEDIUM ili LOW")
    private String liquidity;

    // Futures-specific
    @Schema(description = "[FUTURES] Velicina ugovora")
    private Integer contractSize;

    @Schema(description = "[FUTURES] Jedinica ugovora", example = "barrel")
    private String contractUnit;

    @Schema(description = "[FUTURES] Datum izmirenja ugovora")
    private LocalDate settlementDate;
}
