package rs.raf.banka2_bek.stock.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Istorijski podaci o ceni hartije na dnevnom nivou.
 * Koristi se za prikaz grafika promene cene.
 *
 * Specifikacija: Celina 3 - ListingDailyPriceInfo
 */
@Entity
@Table(name = "listing_daily_prices")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListingDailyPriceInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    // TODO: Datum za koji su podaci zabelezerli
    @Column(nullable = false)
    private LocalDate date;

    // TODO: Poslednja cena trgovanja za taj dan
    @Column(precision = 18, scale = 4)
    private BigDecimal price;

    // TODO: Najvisa cena po kojoj je hartija prodata tog dana
    @Column(precision = 18, scale = 4)
    private BigDecimal high;

    // TODO: Najniza cena po kojoj je hartija kupljena tog dana
    @Column(precision = 18, scale = 4)
    private BigDecimal low;

    // TODO: Razlika u ceni u odnosu na prethodni dan
    @Column(name = "price_change", precision = 18, scale = 4)
    private BigDecimal change;

    // TODO: Broj prodatih/kupljenih hartija tokom dana
    private Long volume;
}
