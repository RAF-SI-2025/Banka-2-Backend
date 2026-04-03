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
    @Builder.Default
    private Boolean active = true;

    // Da li je ovo entitet države (Republika Srbija) — koristi se za poreze
    @Column(nullable = false)
    @Builder.Default
    private Boolean isState = false;

    @Column(nullable = false, updatable = false)
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
