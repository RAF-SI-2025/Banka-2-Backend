package rs.raf.banka2_bek.interbank.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.interbank.model.InterbankOtcNegotiation;
import rs.raf.banka2_bek.interbank.model.InterbankOtcNegotiationStatus;
import rs.raf.banka2_bek.interbank.model.InterbankPartyType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * T12 — Integration test za InterbankOtcNegotiationRepository.
 *
 * Pokriva: persist + lookup po protokol-ID-ju, pretrage po lokalnoj strani,
 * sumActiveAmountForSellerAndTicker (kvota check za §3.2 inbound).
 *
 * Profil "test" + H2 in-memory baza (vidi application-test.properties).
 *
 * NAPOMENA: Spring Boot 4 je uklonio @DataJpaTest iz default test-autoconfigure
 * modula, pa koristimo @SpringBootTest. @Transactional osigurava rollback
 * posle svakog testa (umesto rucnog deleteAll()).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InterbankOtcNegotiationRepositoryTest {

    @Autowired
    private InterbankOtcNegotiationRepository repository;

    private InterbankOtcNegotiation buildSellerSideNegotiation(String foreignNegId, String ticker,
                                                               BigDecimal amount,
                                                               InterbankOtcNegotiationStatus status) {
        // Mi smo SELLER (autoritativni vlasnik pregovora po §3.2). Foreign
        // negotiation ID je generisan KOD NAS, pa je foreignNegotiationRoutingNumber
        // = nas (222). Buyer je u banci 111.
        InterbankOtcNegotiation n = new InterbankOtcNegotiation();
        n.setForeignNegotiationRoutingNumber(222);
        n.setForeignNegotiationIdString(foreignNegId);
        n.setLocalPartyType(InterbankPartyType.SELLER);
        n.setLocalPartyId(1L);
        n.setLocalPartyRole("CLIENT");
        n.setForeignPartyRoutingNumber(111);
        n.setForeignPartyIdString("partner-buyer-001");
        n.setTicker(ticker);
        n.setAmount(amount);
        n.setPricePerUnit(new BigDecimal("180.00"));
        n.setPriceCurrency("USD");
        n.setPremium(new BigDecimal("700.00"));
        n.setPremiumCurrency("USD");
        n.setSettlementDate(LocalDate.now().plusDays(30));
        // Kupac je poslednji izmenio (inicirao pregovor) — turn je sad nas (seller).
        n.setLastModifiedByRoutingNumber(111);
        n.setLastModifiedByIdString("partner-buyer-001");
        n.setStatus(status);
        return n;
    }

    @BeforeEach
    void cleanUp() {
        // @Transactional na klasi obezbedjuje rollback per-test, ali
        // application-test.properties ima ddl-auto=create -> baza moze
        // perzistovati izmedju testova istog konteksta. Defensive cleanup.
        repository.deleteAll();
    }

    @Test
    @DisplayName("save + findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString — round-trip")
    void persistAndLookupByForeignId() {
        InterbankOtcNegotiation toSave = buildSellerSideNegotiation(
                "neg-001", "AAPL", new BigDecimal("50.00"), InterbankOtcNegotiationStatus.ACTIVE);

        InterbankOtcNegotiation saved = repository.save(toSave);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getLastModifiedAt()).isNotNull();
        // @PrePersist treba da postavi default ACTIVE ako nije bilo postavljeno.
        assertThat(saved.getStatus()).isEqualTo(InterbankOtcNegotiationStatus.ACTIVE);

        Optional<InterbankOtcNegotiation> found = repository
                .findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(222, "neg-001");

        assertThat(found).isPresent();
        assertThat(found.get().getTicker()).isEqualTo("AAPL");
        assertThat(found.get().getAmount()).isEqualByComparingTo("50.00");
        assertThat(found.get().getLocalPartyType()).isEqualTo(InterbankPartyType.SELLER);
    }

    @Test
    @DisplayName("findBy...IdString — vraca empty kad ID ne postoji")
    void lookupMissingNegotiation() {
        Optional<InterbankOtcNegotiation> found = repository
                .findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(222, "nepostojeci");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByLocalPartyIdAndLocalPartyRoleAndStatus — filtrira po (id, role, status)")
    void filterByLocalPartyAndStatus() {
        repository.save(buildSellerSideNegotiation(
                "neg-active-1", "AAPL", new BigDecimal("10"), InterbankOtcNegotiationStatus.ACTIVE));
        repository.save(buildSellerSideNegotiation(
                "neg-active-2", "AAPL", new BigDecimal("5"), InterbankOtcNegotiationStatus.ACTIVE));
        // CLOSED — ne bi smeo biti vracen
        repository.save(buildSellerSideNegotiation(
                "neg-closed", "AAPL", new BigDecimal("3"), InterbankOtcNegotiationStatus.CLOSED));

        List<InterbankOtcNegotiation> active = repository
                .findByLocalPartyIdAndLocalPartyRoleAndStatus(1L, "CLIENT", InterbankOtcNegotiationStatus.ACTIVE);

        assertThat(active).hasSize(2);
        assertThat(active).allMatch(n -> n.getStatus() == InterbankOtcNegotiationStatus.ACTIVE);
    }

    @Test
    @DisplayName("findByLocalPartyTypeAndStatus — filtrira po BUYER/SELLER")
    void filterByPartyType() {
        // 2 SELLER pregovora
        repository.save(buildSellerSideNegotiation(
                "neg-s1", "AAPL", new BigDecimal("10"), InterbankOtcNegotiationStatus.ACTIVE));
        repository.save(buildSellerSideNegotiation(
                "neg-s2", "AAPL", new BigDecimal("20"), InterbankOtcNegotiationStatus.ACTIVE));
        // 1 BUYER pregovor
        InterbankOtcNegotiation buyer = buildSellerSideNegotiation(
                "neg-b1", "MSFT", new BigDecimal("15"), InterbankOtcNegotiationStatus.ACTIVE);
        buyer.setLocalPartyType(InterbankPartyType.BUYER);
        // BUYER varijanta: foreign negotiation ID generisan kod partner banke (111).
        buyer.setForeignNegotiationRoutingNumber(111);
        buyer.setForeignNegotiationIdString("partner-neg-b1");
        repository.save(buyer);

        List<InterbankOtcNegotiation> sellers = repository
                .findByLocalPartyTypeAndStatus(InterbankPartyType.SELLER, InterbankOtcNegotiationStatus.ACTIVE);
        List<InterbankOtcNegotiation> buyers = repository
                .findByLocalPartyTypeAndStatus(InterbankPartyType.BUYER, InterbankOtcNegotiationStatus.ACTIVE);

        assertThat(sellers).hasSize(2);
        assertThat(buyers).hasSize(1);
        assertThat(buyers.get(0).getTicker()).isEqualTo("MSFT");
    }

    @Test
    @DisplayName("sumActiveAmountForSellerAndTicker — sumira ACTIVE pregovore za par (seller, ticker)")
    void sumActiveAmountForSeller() {
        // 2 ACTIVE pregovora za AAPL kod istog seller-a
        repository.save(buildSellerSideNegotiation(
                "neg-a1", "AAPL", new BigDecimal("30.00"), InterbankOtcNegotiationStatus.ACTIVE));
        repository.save(buildSellerSideNegotiation(
                "neg-a2", "AAPL", new BigDecimal("20.00"), InterbankOtcNegotiationStatus.ACTIVE));
        // ACCEPTED — NE bi smeo biti uracunat (samo ACTIVE)
        repository.save(buildSellerSideNegotiation(
                "neg-a3", "AAPL", new BigDecimal("100.00"), InterbankOtcNegotiationStatus.ACCEPTED));
        // Drugi ticker — NE bi smeo biti uracunat
        repository.save(buildSellerSideNegotiation(
                "neg-other", "MSFT", new BigDecimal("50.00"), InterbankOtcNegotiationStatus.ACTIVE));

        BigDecimal sum = repository.sumActiveAmountForSellerAndTicker(1L, "CLIENT", "AAPL");

        assertThat(sum).isCloseTo(new BigDecimal("50.00"), within(new BigDecimal("0.01")));
    }

    @Test
    @DisplayName("sumActiveAmountForSellerAndTicker — vraca 0 kad nema pregovora (coalesce)")
    void sumActiveAmountReturnsZeroWhenEmpty() {
        BigDecimal sum = repository.sumActiveAmountForSellerAndTicker(999L, "CLIENT", "AAPL");

        assertThat(sum).isNotNull();
        assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
