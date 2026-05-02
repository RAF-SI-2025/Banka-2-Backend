package rs.raf.banka2_bek.interbank.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContract;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContractStatus;
import rs.raf.banka2_bek.interbank.model.InterbankOtcNegotiation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InterbankOtcContractRepositoryIT {

    @Autowired
    private InterbankOtcNegotiationRepository negotiationRepository;

    @Autowired
    private InterbankOtcContractRepository contractRepository;

    private InterbankOtcNegotiation savedNegotiation(String opaqueId) {
        return negotiationRepository.save(InterbankOtcNegotiation.builder()
                .negotiationRoutingNumber("333")
                .negotiationOpaqueId(opaqueId)
                .stockTicker("MSFT")
                .settlementInstant(Instant.parse("2025-05-10T00:00:00Z"))
                .pricePerUnitAmount(new BigDecimal("300.0000"))
                .pricePerUnitCurrency("USD")
                .premiumAmount(new BigDecimal("100.0000"))
                .premiumCurrency("USD")
                .amount(10)
                .buyerRoutingNumber("111")
                .buyerOpaqueId("b")
                .sellerRoutingNumber("333")
                .sellerOpaqueId("s")
                .lastModifiedByRoutingNumber("111")
                .lastModifiedByOpaqueId("b")
                .ongoing(false)
                .updatedAt(Instant.now())
                .build());
    }

    private InterbankOtcContract buildContract(InterbankOtcNegotiation nego, InterbankOtcContractStatus status) {
        return InterbankOtcContract.builder()
                .negotiation(nego)
                .negotiationRoutingNumber(nego.getNegotiationRoutingNumber())
                .negotiationOpaqueId(nego.getNegotiationOpaqueId())
                .stockTicker(nego.getStockTicker())
                .pricePerUnitAmount(nego.getPricePerUnitAmount())
                .pricePerUnitCurrency(nego.getPricePerUnitCurrency())
                .settlementInstant(nego.getSettlementInstant())
                .shareAmount(nego.getAmount())
                .premiumAmount(nego.getPremiumAmount())
                .premiumCurrency(nego.getPremiumCurrency())
                .status(status)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void saveContractAndFindByNegotiation() {
        InterbankOtcNegotiation nego = savedNegotiation("n-1");
        contractRepository.save(buildContract(nego, InterbankOtcContractStatus.ACTIVE));

        List<InterbankOtcContract> byNeg = contractRepository.findByNegotiation_Id(nego.getId());
        assertThat(byNeg).hasSize(1);

        Optional<InterbankOtcContract> byForeign =
                contractRepository.findByNegotiationRoutingNumberAndNegotiationOpaqueId("333", "n-1");
        assertThat(byForeign).isPresent();
        assertThat(byForeign.get().getStatus()).isEqualTo(InterbankOtcContractStatus.ACTIVE);
    }

    @Test
    void findByStatus_filtersAcrossContracts() {
        InterbankOtcNegotiation a = savedNegotiation("n-a");
        InterbankOtcNegotiation b = savedNegotiation("n-b");
        InterbankOtcNegotiation c = savedNegotiation("n-c");

        contractRepository.save(buildContract(a, InterbankOtcContractStatus.ACTIVE));
        contractRepository.save(buildContract(b, InterbankOtcContractStatus.EXERCISED));
        contractRepository.save(buildContract(c, InterbankOtcContractStatus.EXPIRED));

        assertThat(contractRepository.findByStatus(InterbankOtcContractStatus.ACTIVE)).hasSize(1);
        assertThat(contractRepository.findByStatus(InterbankOtcContractStatus.EXERCISED)).hasSize(1);
        assertThat(contractRepository.findByStatus(InterbankOtcContractStatus.EXPIRED)).hasSize(1);
        assertThat(contractRepository.findByStatus(InterbankOtcContractStatus.CANCELLED)).isEmpty();
    }

    @Test
    void multipleContractsPerNegotiationAreReturned() {
        InterbankOtcNegotiation nego = savedNegotiation("n-multi");
        contractRepository.save(buildContract(nego, InterbankOtcContractStatus.ACTIVE));
        contractRepository.save(buildContract(nego, InterbankOtcContractStatus.EXPIRED));

        assertThat(contractRepository.findByNegotiation_Id(nego.getId())).hasSize(2);
    }
}
