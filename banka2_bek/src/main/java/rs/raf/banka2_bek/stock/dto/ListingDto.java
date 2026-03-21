package rs.raf.banka2_bek.stock.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ListingDto {
    private Long id;
    private String ticker;
    private String name;
    private String exchangeAcronym;
    private String listingType;
    private BigDecimal price;
    private BigDecimal ask;
    private BigDecimal bid;
    private Long volume;
    private BigDecimal priceChange;
    private BigDecimal changePercent;      // izvedeni: (change / (price - change)) * 100
    private BigDecimal initialMarginCost;  // izvedeni: maintenanceMargin * 1.1
    private BigDecimal maintenanceMargin;  // izvedeni: zavisi od tipa hartije

    // Stock-specific
    private Long outstandingShares;
    private BigDecimal dividendYield;
    private BigDecimal marketCap;          // izvedeni: outstandingShares * price

    // Forex-specific
    private String baseCurrency;
    private String quoteCurrency;
    private String liquidity;

    // Futures-specific
    private Integer contractSize;
    private String contractUnit;
    private LocalDate settlementDate;
}
