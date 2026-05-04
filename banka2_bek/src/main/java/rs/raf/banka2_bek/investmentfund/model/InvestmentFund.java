package rs.raf.banka2_bek.investmentfund.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "investment_funds", uniqueConstraints = {
        @UniqueConstraint(name = "uk_fund_name", columnNames = "name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentFund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 1024)
    private String description;

    @Column(name = "minimum_contribution", nullable = false, precision = 19, scale = 4)
    private BigDecimal minimumContribution;

    @Column(name = "manager_employee_id", nullable = false)
    private Long managerEmployeeId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "active", nullable = false)
    @org.hibernate.annotations.ColumnDefault("1")
    private boolean active = true;

    /** Datum osnivanja fonda (po spec-u "Datum kreiranja"). */
    @Column(name = "inception_date")
    private LocalDate inceptionDate;

    /**
     * P9 — Spec Celina 4 (Nova) §4222 Napomena 2: "Klijent je klijent koji je
     * vlasnik banke." Banka kao entitet investira kroz pozicije
     * ClientFundPosition sa userRole='CLIENT' i userId = ownerClientId banke.
     *
     * Ovo polje je NEnametljivo — moze biti null za fondove kreirane pre nego
     * sto je vlasnik banke uveden u seed. InvestmentFundService.listBankPositions
     * filtrira pozicije po (userRole='CLIENT', userId=ownerClientId).
     *
     * Seed.sql sadrzi klijenta sa email='banka2.doo@banka.rs'; centralni
     * `bank.owner-client-email` u application.properties resolvuje ID pri
     * svakom listBankPositions pozivu.
     */
    @Column(name = "owner_client_id")
    private Long ownerClientId;
}
