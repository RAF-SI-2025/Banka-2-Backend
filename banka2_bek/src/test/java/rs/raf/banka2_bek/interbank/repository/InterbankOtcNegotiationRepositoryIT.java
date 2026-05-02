package rs.raf.banka2_bek.interbank.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.interbank.model.InterbankOtcNegotiation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InterbankOtcNegotiationRepositoryIT {

    @Autowired
    private InterbankOtcNegotiationRepository repository;

    @Autowired
    private EntityManager entityManager;

    private InterbankOtcNegotiation buildSample(String routingNumber, String opaqueId) {
        return InterbankOtcNegotiation.builder()
                .negotiationRoutingNumber(routingNumber)
                .negotiationOpaqueId(opaqueId)
                .stockTicker("AAPL")
                .settlementInstant(Instant.parse("2025-04-05T12:00:00Z"))
                .pricePerUnitAmount(new BigDecimal("200.0000"))
                .pricePerUnitCurrency("USD")
                .premiumAmount(new BigDecimal("700.0000"))
                .premiumCurrency("USD")
                .amount(50)
                .buyerRoutingNumber("111")
                .buyerOpaqueId("b1")
                .sellerRoutingNumber(routingNumber)
                .sellerOpaqueId("s1")
                .lastModifiedByRoutingNumber("111")
                .lastModifiedByOpaqueId("b1")
                .ongoing(true)
                .updatedAt(Instant.parse("2025-04-01T10:00:00Z"))
                .build();
    }

    @Test
    void saveAndFindByForeignId() {
        InterbankOtcNegotiation saved = repository.save(buildSample("222", "neg-opaque-1"));
        assertThat(saved.getId()).isNotNull();

        Optional<InterbankOtcNegotiation> found =
                repository.findByNegotiationRoutingNumberAndNegotiationOpaqueId("222", "neg-opaque-1");
        assertThat(found).isPresent();
        assertThat(found.get().getAmount()).isEqualTo(50);
        assertThat(found.get().isOngoing()).isTrue();
    }

    @Test
    void findByForeignId_notFound_returnsEmpty() {
        repository.save(buildSample("222", "neg-opaque-1"));

        Optional<InterbankOtcNegotiation> found =
                repository.findByNegotiationRoutingNumberAndNegotiationOpaqueId("999", "missing");
        assertThat(found).isEmpty();
    }

    @Test
    void uniqueConstraintOnForeignIdRejectsDuplicate() {
        repository.saveAndFlush(buildSample("222", "dup-1"));

        assertThatThrownBy(() -> {
            repository.saveAndFlush(buildSample("222", "dup-1"));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentForeignIdsCoexist() {
        repository.saveAndFlush(buildSample("222", "n-a"));
        repository.saveAndFlush(buildSample("222", "n-b"));
        repository.saveAndFlush(buildSample("333", "n-a"));

        assertThat(repository.findAll()).hasSize(3);
    }
}
