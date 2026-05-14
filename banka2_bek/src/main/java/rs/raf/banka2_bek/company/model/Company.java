package rs.raf.banka2_bek.company.model;

import jakarta.persistence.*;
import lombok.*;

import rs.raf.banka2_bek.account.model.Account;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // Matični broj — jedinstven, ne menja se nakon upisa
    @Column(unique = true, length = 20, updatable = false)
    private String registrationNumber;

    // PIB — jedinstven, ne menja se nakon upisa
    @Column(unique = true, length = 20, updatable = false)
    private String taxNumber;

    @Column(length = 10)
    private String activityCode;    // Šifra delatnosti (može se menjati)

    @Column(nullable = false, length = 200)
    private String address;

    // Matično pravno lice (opciono — self-referencing FK)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "majority_owner_id")
    private Company majorityOwner;

    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("1")
    @Builder.Default
    private Boolean active = true;

    // Da li je ovo entitet drzave (Republika Srbija) — koristi se za poreze
    // (TaxService.collectTaxFromUser prebacuje porez na drzavin RSD racun).
    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    @Builder.Default
    private Boolean isState = false;

    // Da li je ovo entitet nase Banke (Banka 2025 Tim 2). Razdvojeno od
    // {@link #isState} jer u Celini 3 spec-u i banka i drzava su zasebne
    // "Firme" (Celina 2 §73-78 i Celina 3 §47).
    // Koristi InvestmentFundService.createFund kao company-relaciju za
    // novi fund accountu.
    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    @Builder.Default
    private Boolean isBank = false;

    @Column(nullable = false, updatable = false)
    @org.hibernate.annotations.ColumnDefault("CURRENT_TIMESTAMP")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Relacije ──────────────────────────────────────────────────────────────

    // Zaposleni koji rade u ovoj kompaniji (ovlašćene osobe)
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AuthorizedPerson> authorizedPersons = new ArrayList<>();

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Account> accounts = new ArrayList<>();
}
