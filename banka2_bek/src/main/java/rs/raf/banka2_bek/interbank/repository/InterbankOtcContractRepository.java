package rs.raf.banka2_bek.interbank.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContract;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContractStatus;

import java.util.List;
import java.util.Optional;

/**
 * Repozitorijum inter-bank OTC opcionih ugovora
 * (snapshot prihvacene ponude — protokol §2.7.2 / §3.6).
 *
 * TODO T2 outbound: dodati po potrebi finder metode kad outbound „accept“ formira
 * ugovor i ceka potvrdu T1 transaction executora:
 *   - findByStatusAndStockTicker(...) — agregacija po hartiji.
 *
 * TODO T3 inbound: dodati po potrebi finder metode za saga / izvrsavanje opcija:
 *   - findBySettlementInstantBeforeAndStatus(Instant, ACTIVE) — scheduler za isteklost
 *     opcija (vidi §2.7.2: "if the option was not used, the resources stuck in an
 *     option shall be un-reserved").
 */
public interface InterbankOtcContractRepository extends JpaRepository<InterbankOtcContract, Long> {

    List<InterbankOtcContract> findByNegotiation_Id(Long negotiationId);

    Optional<InterbankOtcContract> findByNegotiationRoutingNumberAndNegotiationOpaqueId(
            String negotiationRoutingNumber,
            String negotiationOpaqueId
    );

    List<InterbankOtcContract> findByStatus(InterbankOtcContractStatus status);
}
