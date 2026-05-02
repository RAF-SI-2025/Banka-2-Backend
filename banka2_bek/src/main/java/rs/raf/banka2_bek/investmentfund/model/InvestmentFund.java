package rs.raf.banka2_bek.investmentfund.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.employee.model.Employee;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Investicioni fond — Celina 4, entitet fond.
 *
 * ownerClient = klijent koji je vlasnik banke; pod njim se vode bankine pozicije
 * u fondovima (Napomene 1 i 2 iz Celine 4 — banka kao entitet ima vlasnika
 * koji je modelovan kao Client, i tu se beleze sve bankine investicije).
 *
 * Klijent vlasnik moze biti:
 *   - sama banka kao pravno lice (po zadatku T12 — „Banka 2 d.o.o.“ se vodi
 *     kao Client sa email-om bank.owner-client-email), ili
 *   - bilo koje drugo lice/firma koje je registrovano kao Client i postavljeno
 *     kao vlasnik banke (kasnije, ako bude potrebno).
 */
@Entity
@Table(name = "investment_funds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentFund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "minimum_contribution", nullable = false, precision = 19, scale = 4)
    private BigDecimal minimumContribution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_id", nullable = false)
    private Employee manager;

    /** RSD račun fonda (T7). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fund_account_id", nullable = false)
    private Account fundAccount;

    /**
     * Klijent koji je vlasnik banke (moze biti sama banka kao pravno lice, ili neko
     * drugo lice/firma vlasnik) — pod njim se vode bankine pozicije u fondu
     * (Napomene 1 i 2 iz Celine 4).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_client_id", nullable = false)
    private Client ownerClient;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private boolean active = true;
}
