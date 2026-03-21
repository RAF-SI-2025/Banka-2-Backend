package rs.raf.banka2_bek.order.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rs.raf.banka2_bek.stock.model.Listing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Nalog za kupovinu ili prodaju hartije od vrednosti.
 *
 * Specifikacija: Celina 3 - Order
 */
@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TODO: Korisnik (aktuar ili klijent) koji je postavio order
    // - Za aktuare: employee ID
    // - Za klijente: client ID (klijenti sa permisijom za trgovinu)
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // TODO: Da li je korisnik zaposleni ili klijent
    // - Potrebno za odredjivanje sa kog racuna se skida novac
    //   Klijent: sa kog NJEGOVOG racuna (uz proviziju pri konverziji)
    //   Zaposleni: sa kog BANKINOG racuna (bez provizije)
    @Column(name = "user_role", nullable = false)
    private String userRole; // "EMPLOYEE" ili "CLIENT"

    // TODO: Hartija od vrednosti koja se kupuje/prodaje
    @ManyToOne
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    // TODO: Tip ordera (Market, Limit, Stop, Stop-Limit)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType;

    // TODO: Kolicina hartija od vrednosti
    @Column(nullable = false)
    private Integer quantity;

    // TODO: Contract Size - broj ugovora
    // - Podrazumevano 1
    @Column(name = "contract_size", nullable = false)
    private Integer contractSize;

    // TODO: Cena po jedinici
    // - Za Market Order: trenutna ask/bid cena
    // - Za Limit Order: limit cena koju je korisnik zadao
    // - Za Stop Order: stop cena
    // - Za Stop-Limit: stop cena (limit se cuva u limitValue)
    @Column(name = "price_per_unit", precision = 18, scale = 4)
    private BigDecimal pricePerUnit;

    // TODO: Limit Value - samo za Limit i Stop-Limit ordere
    // - Limit Order: max cena za BUY, min cena za SELL
    // - Stop-Limit: limit cena nakon sto se aktivira stop
    @Column(name = "limit_value", precision = 18, scale = 4)
    private BigDecimal limitValue;

    // TODO: Stop Value - samo za Stop i Stop-Limit ordere
    // - Cena na kojoj se order aktivira
    @Column(name = "stop_value", precision = 18, scale = 4)
    private BigDecimal stopValue;

    // TODO: BUY ili SELL
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderDirection direction;

    // TODO: Status ordera
    // - PENDING: ceka odobrenje supervizora
    // - APPROVED: odobren, ceka izvrsavanje
    // - DECLINED: odbijen
    // - DONE: kompletno izvrsen
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    // TODO: Supervizor koji je odobrio/odbio order
    // - "No need for approval" ako odobrenje nije bilo potrebno
    // - Ime supervizora ako je odobren/odbijen
    @Column(name = "approved_by")
    private String approvedBy;

    // TODO: Da li je order kompletno zavrsen
    @Column(name = "is_done", nullable = false)
    private boolean isDone;

    // TODO: Datum i vreme poslednje modifikacije
    @Column(name = "last_modification")
    private LocalDateTime lastModification;

    // TODO: Broj preostalih delova ordera koji nisu izvrseni
    // - Pocetna vrednost = quantity
    // - Smanjuje se kako se delovi izvrsavaju
    @Column(name = "remaining_portions")
    private Integer remainingPortions;

    // TODO: After Hours flag
    // - True ako je order kreiran manje od 4 sata od zatvaranja berze
    // - Izvrsavanje je sporije (dodatnih 30 min po delu)
    @Column(name = "after_hours", nullable = false)
    private boolean afterHours;

    // TODO: All or None flag
    // - Ako je true, order se izvrsava samo kompletno (ne u delovima)
    @Column(name = "all_or_none", nullable = false)
    private boolean allOrNone;

    // TODO: Margin flag
    // - Ako je true, order koristi kredit za izvrsavanje
    // - Zahteva permisije: zaposleni mora imati permisiju, klijent mora imati odobren kredit
    @Column(name = "is_margin", nullable = false)
    private boolean margin;

    // TODO: ID racuna sa kog se skida/na koji se uplacuje novac
    @Column(name = "account_id")
    private Long accountId;

    // TODO: Approximate Price = contractSize * pricePerUnit * quantity
    // Ovo se moze racunati u servisu, ne mora se cuvati
    @Column(name = "approximate_price", precision = 18, scale = 4)
    private BigDecimal approximatePrice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
