package rs.raf.banka2_bek.berza.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

/**
 * JPA entitet koji predstavlja berzu (stock exchange).
 *
 * Specifikacija: Celina 3 - Berza
 */
@Entity
@Table(name = "exchanges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exchange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Puno ime berze, npr. "New York Stock Exchange" */
    @Column(nullable = false)
    private String name;

    /** Skraceni naziv, npr. "NYSE" — mora biti unikatan */
    @Column(nullable = false, unique = true, length = 10)
    private String acronym;

    /** Market Identifier Code po ISO 10383, npr. "XNYS" */
    @Column(length = 10)
    private String micCode;

    /** Drzava u kojoj se berza nalazi, npr. "US" */
    @Column(nullable = false, length = 5)
    private String country;

    /** Valuta u kojoj se trguje, npr. "USD" */
    @Column(nullable = false, length = 5)
    private String currency;

    /** IANA timezone ID, npr. "America/New_York" */
    @Column(nullable = false, length = 40)
    private String timeZone;

    /** Vreme otvaranja berze u lokalnoj vremenskoj zoni */
    @Column(nullable = false)
    private LocalTime openTime;

    /** Vreme zatvaranja berze u lokalnoj vremenskoj zoni */
    @Column(nullable = false)
    private LocalTime closeTime;

    /** Vreme otvaranja pre-market sesije (moze biti null ako berza nema pre-market) */
    @Column
    private LocalTime preMarketOpenTime;

    /** Vreme zatvaranja post-market sesije (moze biti null ako berza nema post-market) */
    @Column
    private LocalTime postMarketCloseTime;

    /**
     * Test mode flag.
     * Kada je true, berza se smatra uvek otvorenom — koristi se za razvoj i testiranje.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean testMode = false;

    /** Da li je berza aktivna (neaktivne berze se ne prikazuju klijentima) */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Datumi praznika kada je berza zatvorena iako nije vikend.
     * Cuva se u zasebnoj tabeli exchange_holidays putem @ElementCollection.
     */
    @ElementCollection
    @CollectionTable(name = "exchange_holidays", joinColumns = @JoinColumn(name = "exchange_id"))
    @Column(name = "holiday_date")
    @Builder.Default
    private Set<LocalDate> holidays = new HashSet<>();
}
