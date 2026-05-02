package rs.raf.banka2_bek.interbank.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.interbank.model.InterbankOtcNegotiation;

import java.util.Optional;

/**
 * Repozitorijum inter-bank OTC pregovora.
 *
 * T12 izlaze samo lookup po autoritativnom ForeignBankId paru
 * (negotiationRoutingNumber + negotiationOpaqueId) — to je dovoljno za detekciju
 * mirror-a vec postojeceg pregovora i za update po PUT /negotiations/{rn}/{id}.
 *
 * TODO T2 outbound: dodati custom finder metode po potrebi:
 *   - findByBuyerRoutingNumberAndBuyerOpaqueId(...) — listanje pregovora za kupca
 *     (kad mi saljemo ponudu drugoj banci)
 *   - findByLastModifiedByRoutingNumberAndLastModifiedByOpaqueId(...) — turn check
 *     pre slanja kontraponude (ne smemo dva puta zaredom)
 *
 * TODO T3 inbound: dodati custom finder metode po potrebi:
 *   - findByOngoingTrue() — aktivni pregovori za scheduler / cleanup
 *   - findBySellerRoutingNumberAndSellerOpaqueId(...) — listanje pregovora gde smo
 *     mi prodavac (autoritativna kopija je uvek kod prodavca)
 *   - findByLastModifiedByRoutingNumberAndLastModifiedByOpaqueId(...) — turn check
 *     pri inbound PUT (ako je lastModifiedBy = trenutni posiljalac, 409 Conflict)
 */
public interface InterbankOtcNegotiationRepository extends JpaRepository<InterbankOtcNegotiation, Long> {

    Optional<InterbankOtcNegotiation> findByNegotiationRoutingNumberAndNegotiationOpaqueId(
            String negotiationRoutingNumber,
            String negotiationOpaqueId
    );
}
