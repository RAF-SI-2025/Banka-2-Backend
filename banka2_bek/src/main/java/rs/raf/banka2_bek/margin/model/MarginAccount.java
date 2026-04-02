package rs.raf.banka2_bek.margin.model;

import jakarta.persistence.*;
import lombok.*;
import rs.raf.banka2_bek.account.model.Account;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entitet koji predstavlja margin racun.
 *
 * Margin racun je vezan za obican racun (Account) i omogucava korisniku
 * da trguje hartijama od vrednosti koristeci pozajmljena sredstva od banke.
 *
 * Specifikacija: Celina 3 - Margin racuni
 *
 * Kljucni koncepti:
 *   - initialMargin: ukupna vrednost racuna (depozit + kredit banke)
 *   - loanValue: koliko je banka pozajmila korisniku
 *   - maintenanceMargin: minimalna vrednost ispod koje se racun blokira (margin call)
 *   - bankParticipation: procenat koji banka pokriva (npr. 0.50 = 50%)
 */
@Entity
@Table(name = "margin_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarginAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Povezani obican racun sa kog se skidaju/uplacuju sredstva */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /** ID korisnika (klijenta) oji je vlasnik margin racuna */
    @Column(name = "user_id", nullable = false)
    private Long userId;


    /**
     * Pocetna margina — ukupna vrednost racuna.
     * initialMargin = korisnikov depozit + loanValue
     * Formula: initialMargin = deposit / (1 - bankParticipation)
     */
    @Column(name = "initial_margin", nullable = false, precision = 19, scale = 4)
    private BigDecimal initialMargin;

    /**
     * Vrednost kredita — koliko je banka pozajmila korisniku.
     * loanValue = initialMargin - deposit
     */
    @Column(name = "loan_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal loanValue;

    /**
     * Margina odrzavanja — minimalna vrednost racuna.
     * Ako initialMargin padne ispod ove vrednosti, racun se blokira (margin call).
     * Za akcije: maintenanceMargin = initialMargin * 0.5
     */
    @Column(name = "maintenance_margin", nullable = false, precision = 19, scale = 4)
    private BigDecimal maintenanceMargin;

    /**
     * Procenat ucestva banke u investiciji.
     * Npr. 0.50 znaci da banka pokriva 50% ukupne vrednosti.
     */
    @Column(name = "bank_participation", nullable = false, precision = 5, scale = 4)
    private BigDecimal bankParticipation;

    /** Status margin racuna (ACTIVE ili BLOCKED) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private MarginAccountStatus status = MarginAccountStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
