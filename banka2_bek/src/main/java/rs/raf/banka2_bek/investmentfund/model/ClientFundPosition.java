package rs.raf.banka2_bek.investmentfund.model;

import jakarta.persistence.*;
import lombok.*;
import rs.raf.banka2_bek.client.model.Client;

import java.math.BigDecimal;

/**
 * Ukupna pozicija klijenta u investicionom fondu (RSD uloženo).
 *
 * Klijent moze biti i sama banka (vlasnik banke se modeluje kao Client po
 * Napomenama 1 i 2 iz Celine 4) — u tom slucaju red predstavlja bankinu
 * investiciju u fondu (vidljivu u Profit banke portalu i u supervizorovom
 * Moj portfolio).
 */
@Entity
@Table(
        name = "client_fund_positions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_client_fund",
                columnNames = {"client_id", "fund_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientFundPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fund_id", nullable = false)
    private InvestmentFund fund;

    @Column(name = "total_invested_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalInvestedAmount;
}
