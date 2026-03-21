package rs.raf.banka2_bek.stock.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Osnovni entitet za sve hartije od vrednosti (akcije, futures, forex).
 * Cuva trenutne cene i osnovne podatke.
 *
 * Specifikacija: Celina 3 - Listings
 */
@Entity
@Table(name = "listings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TODO: Ticker - simbol hartije (AAPL, MSFT, EUR/USD, CLJ22)
    // - Za akcije: 1-5 slova (AAPL, MSFT, GOOG)
    // - Za forex: BASE/QUOTE format (EUR/USD, GBP/JPY)
    // - Za futures: ticker + MYY (CLJ22 = sirova nafta april 2022)
    @Column(nullable = false, unique = true)
    private String ticker;

    // TODO: Naziv hartije (Apple Inc., Euro/US Dollar, Crude Oil April 2022)
    @Column(nullable = false)
    private String name;

    // TODO: Na kojoj berzi se trguje (NYSE, NASDAQ, FOREX, CME)
    @Column(name = "exchange_acronym")
    private String exchangeAcronym;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingType listingType;

    // TODO: Poslednja cena po kojoj se hartija trgovala
    // - Azurira se svakih 15 minuta sa API-ja ili pri osvezavanju
    @Column(precision = 18, scale = 4)
    private BigDecimal price;

    // TODO: Ask/High - najniza cena po kojoj prodavac nudi hartiju
    @Column(precision = 18, scale = 4)
    private BigDecimal ask;

    // TODO: Bid/Low - najniza cena po kojoj je kupac spreman da kupi
    @Column(precision = 18, scale = 4)
    private BigDecimal bid;

    // TODO: Volume - broj prodatih/kupljenih hartija tokom dana
    private Long volume;

    // TODO: Change - razlika u ceni u odnosu na prethodni dan
    @Column(precision = 18, scale = 4)
    private BigDecimal priceChange;

    // TODO: Datum i vreme poslednjeg azuriranja podataka
    @Column(name = "last_refresh")
    private LocalDateTime lastRefresh;

    // ==========================================
    // POLJA SPECIFICNA ZA TIP HARTIJE
    // ==========================================

    // --- STOCKS (akcije) ---
    // TODO: Outstanding Shares - ukupan broj akcija u opticaju
    // - Samo za STOCK tip
    @Column(name = "outstanding_shares")
    private Long outstandingShares;

    // TODO: Dividend Yield - procenat godisnje dividende
    // - Samo za STOCK tip
    @Column(name = "dividend_yield", precision = 10, scale = 4)
    private BigDecimal dividendYield;

    // --- FOREX ---
    // TODO: Base Currency i Quote Currency za forex parove
    // - Samo za FOREX tip (npr. EUR i USD za EUR/USD)
    @Column(name = "base_currency")
    private String baseCurrency;

    @Column(name = "quote_currency")
    private String quoteCurrency;

    // TODO: Likvidnost forex para (HIGH, MEDIUM, LOW)
    private String liquidity;

    // --- FUTURES ---
    // TODO: Contract Size - kolicina proizvoda po ugovoru
    // - Za futures: npr. 5000 barela
    // - Za forex: standardno 1000
    // - Za akcije: 1
    @Column(name = "contract_size")
    private Integer contractSize;

    // TODO: Contract Unit - jedinica mere (Barrel, Kilogram, Liter)
    // - Samo za FUTURES tip
    @Column(name = "contract_unit")
    private String contractUnit;

    // TODO: Settlement Date - datum isteka ugovora
    // - Za futures i opcije: datum isporuke
    // - Za akcije i forex: null
    @Column(name = "settlement_date")
    private java.time.LocalDate settlementDate;

    // ==========================================
    // IZVEDENI PODACI (racunaju se, ne cuvaju se obavezno)
    // ==========================================
    // TODO: Ove podatke mozete racunati u servisu umesto da ih cuvate:
    //
    // Market Cap = outstandingShares * price  (samo za STOCK)
    // Nominal Value = contractSize * price
    // Initial Margin Cost = maintenanceMargin * 1.1
    // Dollar Volume = price * volume
    // Change Percent = (priceChange / (price - priceChange)) * 100
    //
    // Maintenance Margin se racuna razlicito po tipu:
    //   STOCK: 50% * price
    //   FOREX: contractSize * price * 10%
    //   FUTURES: contractSize * price * 10%
}
