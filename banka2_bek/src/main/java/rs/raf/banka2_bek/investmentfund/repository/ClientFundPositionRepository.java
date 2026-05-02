package rs.raf.banka2_bek.investmentfund.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.investmentfund.model.ClientFundPosition;

import java.util.List;

/**
 * Repozitorijum pozicija klijenata u investicionim fondovima.
 *
 * T12 izlaze findByClient_Id — koristi ga InvestmentFundService.listMyPositions
 * i listBankPositions. Napomena: clientId moze biti i klijent koji predstavlja
 * vlasnika banke (po Napomenama 1 i 2 to moze biti sama banka kao pravno lice).
 *
 * TODO T7 fund details: dodati custom finder metode po potrebi:
 *   - List&lt;ClientFundPosition&gt; findByFund_Id(Long fundId) — lista ucesnika fonda
 *
 * TODO T8 invest / withdraw: dodati custom finder metode po potrebi:
 *   - Optional&lt;ClientFundPosition&gt; findByClient_IdAndFund_Id(Long clientId, Long fundId)
 *     — upsert postojece pozicije pri uplati / smanjenje pri isplati
 */
public interface ClientFundPositionRepository extends JpaRepository<ClientFundPosition, Long> {

    List<ClientFundPosition> findByClient_Id(Long clientId);
}
