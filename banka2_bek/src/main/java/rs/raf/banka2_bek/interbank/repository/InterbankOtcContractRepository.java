package rs.raf.banka2_bek.interbank.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContract;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContractStatus;
import rs.raf.banka2_bek.interbank.model.InterbankPartyType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * T12 — Repository za inter-bank OTC opcione ugovore.
 *
 * Spec ref: Protokol §2.7.2 Options, §3.6 Accepting an offer; Celina 4
 * (Nova) — Sklopljeni ugovori; Celina 5 (Nova) — Postignut dogovor.
 *
 * KORISNICI:
 *   T2/T3        — kreira pri prihvatanju ponude (§3.6) i azurira pri exercise
 *   FE           — `findByLocalParty...` za "Sklopljeni ugovori" tab
 *   ExpiryScheduler (kasnije) — `findByStatusAndSettlementDateBefore` za auto-expire
 */
public interface InterbankOtcContractRepository extends JpaRepository<InterbankOtcContract, Long> {

    /** 1:1 mapiranje pregovor -> ugovor. Vraca .empty() ako pregovor nije rezultovao ugovorom. */
    Optional<InterbankOtcContract> findBySourceNegotiationId(Long sourceNegotiationId);

    /**
     * Sklopljeni ugovori za datog korisnika (BUYER ili SELLER), bilo kog
     * statusa. Koristi se za "Sklopljeni ugovori" tab — FE filtrira po
     * statusu (vazeci/istekli) na klijent strani.
     */
    List<InterbankOtcContract> findByLocalPartyIdAndLocalPartyRole(
            Long localPartyId, String localPartyRole);

    /**
     * Aktivni ugovori za datu lokalnu stranu (BUYER ili SELLER) — koristi
     * se npr. za reservaciju hartija prodavca (po §2.7.2 trebamo znati
     * koje ugovore drzimo "live" da ne overcommit-ujemo).
     */
    List<InterbankOtcContract> findByLocalPartyTypeAndStatus(
            InterbankPartyType localPartyType, InterbankOtcContractStatus status);

    /**
     * Auto-expiry helper: ugovori cija je settlementDate prosao a status je
     * jos ACTIVE. Scheduler ce ih pokupiti i pozvati expire() (oslobadja
     * rezervaciju hartija po §2.7.2).
     */
    List<InterbankOtcContract> findByStatusAndSettlementDateBefore(
            InterbankOtcContractStatus status, LocalDate settlementDateBefore);
}
