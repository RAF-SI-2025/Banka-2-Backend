package rs.raf.banka2_bek.interbank.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rs.raf.banka2_bek.interbank.model.InterbankOtcNegotiation;
import rs.raf.banka2_bek.interbank.model.InterbankOtcNegotiationStatus;
import rs.raf.banka2_bek.interbank.model.InterbankPartyType;

import java.util.List;
import java.util.Optional;

/**
 * T12 — Repository za inter-bank OTC pregovore.
 *
 * Spec ref: Protokol §3 OTC negotiation; Celina 5 (Nova) OTC Trgovina.
 *
 * KORISNICI:
 *   T2 outbound — `findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString`
 *                 za sync sa partner bankom (createNegotiation, postCounterOffer)
 *   T3 inbound  — isto za POST/PUT/GET/DELETE/accept handler-e
 *   FE          — `findByLocalParty...` za UI prikaz "Aktivne ponude" tab-a
 */
public interface InterbankOtcNegotiationRepository extends JpaRepository<InterbankOtcNegotiation, Long> {

    /**
     * Lookup po protokol-formi negotiation ID-ja (§2.3 ForeignBankId).
     * Pre svake mutacije (PUT/DELETE/accept) handler mora prvo da resolvuje
     * lokalni entitet preko ovog metoda.
     *
     * @return Optional.empty() ako pregovor sa tim ID-jem ne postoji.
     */
    Optional<InterbankOtcNegotiation> findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
            Integer foreignNegotiationRoutingNumber, String foreignNegotiationIdString);

    /**
     * Aktivne ponude po lokalnoj strani — koristi se za "Aktivne ponude" tab
     * u OTC Ponude i Ugovori portalu (Celina 4 (Nova)).
     *
     * @param localPartyId   id korisnika (clients.id ili employees.id)
     * @param localPartyRole "CLIENT" ili "EMPLOYEE"
     */
    List<InterbankOtcNegotiation> findByLocalPartyIdAndLocalPartyRoleAndStatus(
            Long localPartyId, String localPartyRole, InterbankOtcNegotiationStatus status);

    /**
     * Sve aktivne pregovore u kojima smo BUYER ili SELLER, bez obzira na
     * konkretnog korisnika — supervisor view.
     */
    List<InterbankOtcNegotiation> findByLocalPartyTypeAndStatus(
            InterbankPartyType localPartyType, InterbankOtcNegotiationStatus status);

    /**
     * Provera kvota/ovecounting: pri prihvatanju nove ponude moramo
     * proveriti da seller jos uvek ima dovoljno javnih akcija nakon
     * uracunavanja svih ACTIVE pregovora i ACCEPTED ugovora (§3.2 inbound
     * validacija u T3).
     *
     * Filter `localPartyType = SELLER` jer mi smo prodavac u tim
     * pregovorima (autoritativna kopija je kod nas).
     */
    @Query("select coalesce(sum(n.amount), 0) from InterbankOtcNegotiation n " +
            "where n.localPartyType = rs.raf.banka2_bek.interbank.model.InterbankPartyType.SELLER " +
            "and n.localPartyId = :sellerId " +
            "and n.localPartyRole = :sellerRole " +
            "and n.ticker = :ticker " +
            "and n.status = rs.raf.banka2_bek.interbank.model.InterbankOtcNegotiationStatus.ACTIVE")
    java.math.BigDecimal sumActiveAmountForSellerAndTicker(
            @Param("sellerId") Long sellerId,
            @Param("sellerRole") String sellerRole,
            @Param("ticker") String ticker);
}
