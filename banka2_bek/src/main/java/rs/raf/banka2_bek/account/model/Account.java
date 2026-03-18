package rs.raf.banka2_bek.account.model;

import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.employee.model.Employee;

import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.model.AccountSubtype;

import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 18-cifreni broj računa (prvih 7 uvek iste: šifra banke + šifra filijale)
    @Column(nullable = false, unique = true, length = 18)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AccountSubtype accountSubtype;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    // Vlasnik: fizičko lice (null ako je kompanija vlasnik)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "client_id",
            foreignKey = @ForeignKey(name = "FK_accounts_users") // Menjamo naziv i vezu
    )
    private User user;

    // Vlasnik: pravno lice (null ako je klijent vlasnik)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    // Zaposleni koji je kreirao račun
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // Stanja

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;           // Ukupno stanje

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;  // Raspoloživo stanje (balance - rezervisano)

    // Limiti 

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal dailyLimit = BigDecimal.ZERO;        // Dnevni limit plaćanja

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal monthlyLimit = BigDecimal.ZERO;      // Mesečni limit plaćanja

    // Potrošnja (resetuje se schedulovanim jobom)

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal dailySpending = BigDecimal.ZERO;     // Resetuje se u 00:00

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal monthlySpending = BigDecimal.ZERO;   // Resetuje se 1. u mesecu

    // Ostalo 
    @Column(precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal maintenanceFee = BigDecimal.ZERO;    // Mesečna naknada

    private LocalDate expirationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(length = 64)
    private String name;                                    // Prilagođeni naziv (korisnik može menjati)

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Validacija: tačno jedno od client/company mora biti postavljeno ───────
    // MySQL ne podrzava CHECK constraints kroz Hibernate DDL auto,
    // pa se validacija radi na aplikacionom nivou pre svakog persist/merge.
    @AssertTrue(message = "Racun mora imati vlasnika: ili klijenta ili kompaniju, ali ne oba.")
    @Transient
    public boolean isOwnerValid() {
        return (user == null) != (company == null);
    }
}
