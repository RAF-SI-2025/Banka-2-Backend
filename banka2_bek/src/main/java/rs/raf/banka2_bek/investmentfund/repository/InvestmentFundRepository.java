package rs.raf.banka2_bek.investmentfund.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.investmentfund.model.InvestmentFund;

/**
 * Repozitorijum investicionih fondova.
 *
 * T12 ne zahteva nijednu custom metodu — samo entitet (sa ownerClient) i seed.
 *
 * TODO T7 discovery / create / details: dodati custom finder metode po potrebi:
 *   - List&lt;InvestmentFund&gt; findAllByActiveTrue() — Discovery page
 *   - List&lt;InvestmentFund&gt; findByManager_Id(Long managerId) — supervizorovi fondovi
 *   - Optional&lt;InvestmentFund&gt; findByName(String name) — uniqueness check pri kreiranju
 */
public interface InvestmentFundRepository extends JpaRepository<InvestmentFund, Long> {
}
