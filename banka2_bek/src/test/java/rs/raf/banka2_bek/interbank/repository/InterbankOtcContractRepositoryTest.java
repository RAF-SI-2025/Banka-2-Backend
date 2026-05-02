package rs.raf.banka2_bek.interbank.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContract;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContractStatus;
import rs.raf.banka2_bek.interbank.model.InterbankPartyType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T12 — Integration test za InterbankOtcContractRepository.
 *
 * Pokriva: persist + lookup po sourceNegotiationId, pretrage po lokalnoj
 * strani, auto-expiry helper findByStatusAndSettlementDateBefore.
 *
 * NAPOMENA: Spring Boot 4 je uklonio @DataJpaTest iz default test-autoconfigure
 * modula — koristimo @SpringBootTest sa H2 (application-test.properties).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InterbankOtcContractRepositoryTest {

    @Autowired
    private InterbankOtcContractRepository repository;

    private InterbankOtcContract buildBuyerSideContract(Long sourceNegId, String ticker,
                                                        BigDecimal qty, LocalDate settlement,
                                                        InterbankOtcContractStatus status) {
        // Mi smo BUYER — kupili smo opciju od partner banke (111).
        InterbankOtcContract c = new InterbankOtcContract();
        c.setSourceNegotiationId(sourceNegId);
        c.setLocalPartyType(InterbankPartyType.BUYER);
        c.setLocalPartyId(7L);
        c.setLocalPartyRole("CLIENT");
        c.setForeignPartyRoutingNumber(111);
        c.setForeignPartyIdString("partner-seller-77");
        c.setTicker(ticker);
        c.setQuantity(qty);
        c.setStrikePrice(new BigDecimal("200.00"));
        c.setStrikeCurrency("USD");
        c.setPremium(new BigDecimal("1150.00"));
        c.setPremiumCurrency("USD");
        c.setSettlementDate(settlement);
        c.setStatus(status);
        return c;
    }

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("save + findBySourceNegotiationId — round-trip")
    void persistAndLookupBySource() {
        InterbankOtcContract toSave = buildBuyerSideContract(
                42L, "AAPL", new BigDecimal("50"),
                LocalDate.now().plusDays(30), InterbankOtcContractStatus.ACTIVE);

        InterbankOtcContract saved = repository.save(toSave);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        // @PrePersist treba da postavi default ACTIVE.
        assertThat(saved.getStatus()).isEqualTo(InterbankOtcContractStatus.ACTIVE);

        Optional<InterbankOtcContract> found = repository.findBySourceNegotiationId(42L);

        assertThat(found).isPresent();
        assertThat(found.get().getTicker()).isEqualTo("AAPL");
        assertThat(found.get().getQuantity()).isEqualByComparingTo("50");
        assertThat(found.get().getLocalPartyType()).isEqualTo(InterbankPartyType.BUYER);
    }

    @Test
    @DisplayName("findBySourceNegotiationId — empty kad pregovor nije rezultovao ugovorom")
    void lookupMissingContract() {
        Optional<InterbankOtcContract> found = repository.findBySourceNegotiationId(999_999L);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByLocalPartyIdAndLocalPartyRole — vraca sve ugovore za korisnika nezavisno od statusa")
    void allContractsForUser() {
        repository.save(buildBuyerSideContract(
                1L, "AAPL", new BigDecimal("10"),
                LocalDate.now().plusDays(10), InterbankOtcContractStatus.ACTIVE));
        repository.save(buildBuyerSideContract(
                2L, "MSFT", new BigDecimal("20"),
                LocalDate.now().minusDays(5), InterbankOtcContractStatus.EXPIRED));
        repository.save(buildBuyerSideContract(
                3L, "GOOG", new BigDecimal("5"),
                LocalDate.now().minusDays(15), InterbankOtcContractStatus.EXERCISED));
        // Drugi korisnik — ne sme biti vracen
        InterbankOtcContract other = buildBuyerSideContract(
                4L, "TSLA", new BigDecimal("3"),
                LocalDate.now().plusDays(7), InterbankOtcContractStatus.ACTIVE);
        other.setLocalPartyId(8L);
        repository.save(other);

        List<InterbankOtcContract> mine = repository.findByLocalPartyIdAndLocalPartyRole(7L, "CLIENT");

        assertThat(mine).hasSize(3);
        assertThat(mine).extracting(InterbankOtcContract::getTicker)
                .containsExactlyInAnyOrder("AAPL", "MSFT", "GOOG");
    }

    @Test
    @DisplayName("findByLocalPartyTypeAndStatus — filtrira po BUYER/SELLER + status")
    void filterByPartyAndStatus() {
        // 2 BUYER ACTIVE
        repository.save(buildBuyerSideContract(
                1L, "AAPL", new BigDecimal("10"),
                LocalDate.now().plusDays(10), InterbankOtcContractStatus.ACTIVE));
        repository.save(buildBuyerSideContract(
                2L, "MSFT", new BigDecimal("5"),
                LocalDate.now().plusDays(15), InterbankOtcContractStatus.ACTIVE));
        // 1 BUYER EXERCISED — ne sme biti vracen
        repository.save(buildBuyerSideContract(
                3L, "GOOG", new BigDecimal("8"),
                LocalDate.now().minusDays(2), InterbankOtcContractStatus.EXERCISED));
        // 1 SELLER ACTIVE — ne sme biti vracen
        InterbankOtcContract sellerSide = buildBuyerSideContract(
                4L, "TSLA", new BigDecimal("3"),
                LocalDate.now().plusDays(20), InterbankOtcContractStatus.ACTIVE);
        sellerSide.setLocalPartyType(InterbankPartyType.SELLER);
        repository.save(sellerSide);

        List<InterbankOtcContract> active = repository
                .findByLocalPartyTypeAndStatus(InterbankPartyType.BUYER, InterbankOtcContractStatus.ACTIVE);

        assertThat(active).hasSize(2);
        assertThat(active).allMatch(c -> c.getStatus() == InterbankOtcContractStatus.ACTIVE);
        assertThat(active).allMatch(c -> c.getLocalPartyType() == InterbankPartyType.BUYER);
    }

    @Test
    @DisplayName("findByStatusAndSettlementDateBefore — auto-expiry helper za scheduler")
    void autoExpiryHelper() {
        LocalDate today = LocalDate.now();
        // ACTIVE + settlement u proslosti → kandidat za expiry
        repository.save(buildBuyerSideContract(
                1L, "AAPL", new BigDecimal("10"),
                today.minusDays(1), InterbankOtcContractStatus.ACTIVE));
        repository.save(buildBuyerSideContract(
                2L, "MSFT", new BigDecimal("5"),
                today.minusDays(10), InterbankOtcContractStatus.ACTIVE));
        // ACTIVE + settlement u buducnosti → NE kandidat
        repository.save(buildBuyerSideContract(
                3L, "GOOG", new BigDecimal("8"),
                today.plusDays(5), InterbankOtcContractStatus.ACTIVE));
        // EXPIRED — vec expirovan, ne kandidat (status filter)
        repository.save(buildBuyerSideContract(
                4L, "TSLA", new BigDecimal("3"),
                today.minusDays(20), InterbankOtcContractStatus.EXPIRED));

        List<InterbankOtcContract> toExpire = repository
                .findByStatusAndSettlementDateBefore(InterbankOtcContractStatus.ACTIVE, today);

        assertThat(toExpire).hasSize(2);
        assertThat(toExpire).extracting(InterbankOtcContract::getTicker)
                .containsExactlyInAnyOrder("AAPL", "MSFT");
    }
}
