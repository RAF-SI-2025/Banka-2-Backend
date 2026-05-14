package rs.raf.banka2_bek.interbank.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.protocol.CurrencyCode;
import rs.raf.banka2_bek.interbank.repository.InterbankOtcContractRepository;
import rs.raf.banka2_bek.interbank.repository.InterbankOtcNegotiationRepository;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.interbank.protocol.ForeignBankId;
import rs.raf.banka2_bek.interbank.protocol.MonetaryValue;
import rs.raf.banka2_bek.interbank.protocol.OtcNegotiation;
import rs.raf.banka2_bek.interbank.protocol.OtcOffer;
import rs.raf.banka2_bek.interbank.protocol.StockDescription;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OtcNegotiationService — integration flow (create → counter → accept)")
class OtcNegotiationServiceIntegrationTest {

    private static final int OUR_RN = 222;
    private static final int SELLER_RN = 111;

    @Mock
    private InterbankClient client;
    @Mock
    private InterbankOtcNegotiationRepository negotiationRepository;
    @Mock
    private InterbankOtcContractRepository contractRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private TransactionExecutorService transactionExecutor;

    @Test
    @DisplayName("Pun OTC flow: kreiraj → procitaj (red kupca) → counter → accept")
    void fullOutboundFlow() {
        InterbankProperties properties = new InterbankProperties();
        properties.setMyRoutingNumber(OUR_RN);
        properties.setMyBankDisplayName("Banka 2");
        OtcNegotiationService service = new OtcNegotiationService(client, properties,
                negotiationRepository, contractRepository, portfolioRepository,
                clientRepository, employeeRepository, transactionExecutor);

        ForeignBankId buyer = new ForeignBankId(OUR_RN, "buyer-1");
        ForeignBankId seller = new ForeignBankId(SELLER_RN, "seller-1");
        OffsetDateTime settlement = OffsetDateTime.now().plusDays(30);

        // 1. KREIRAJ — buyer (mi) saljemo OTC offer prodavcevoj banci
        OtcOffer initial = new OtcOffer(
                new StockDescription("AAPL"),
                settlement,
                new MonetaryValue(CurrencyCode.USD, new BigDecimal("180")), // pricePerUnit
                new MonetaryValue(CurrencyCode.USD, new BigDecimal("700")), // premium
                buyer, seller, BigDecimal.valueOf(50),
                buyer); // lastModifiedBy = buyer (we created it)

        ForeignBankId negotiationId = new ForeignBankId(SELLER_RN, "neg-42");
        when(client.postNegotiation(eq(SELLER_RN), eq(initial))).thenReturn(negotiationId);
        ForeignBankId returned = service.createNegotiation(initial);
        assertThat(returned).isEqualTo(negotiationId);

        // 2. CITAJ — seller je u medjuvremenu poslao counter (lastModifiedBy = seller),
        //    sto po §3.3 znaci da je sad NAS red (buyer)
        OtcOffer afterSellerCounter = new OtcOffer(
                initial.stock(), initial.settlementDate(),
                new MonetaryValue(CurrencyCode.USD, new BigDecimal("200")), // seller povecao cenu
                new MonetaryValue(CurrencyCode.USD, new BigDecimal("1150")), // i premium
                buyer, seller, initial.amount(),
                seller); // lastModifiedBy = seller -> turn = buyer (mi)
        OtcNegotiation read = new OtcNegotiation(
                afterSellerCounter.stock(), afterSellerCounter.settlementDate(),
                afterSellerCounter.pricePerUnit(), afterSellerCounter.premium(),
                buyer, seller, afterSellerCounter.amount(),
                afterSellerCounter.lastModifiedBy(), true);
        when(client.getNegotiation(negotiationId)).thenReturn(read);
        OtcNegotiation actualRead = service.readNegotiation(negotiationId);
        assertThat(actualRead.isOngoing()).isTrue();
        assertThat(actualRead.lastModifiedBy()).isEqualTo(seller); // potvrda da je nas red
        assertThat(actualRead.lastModifiedBy()).isNotEqualTo(buyer);

        // 3. KONTRAPONUDA — postujemo turn pravilo: lastModifiedBy = buyer (mi)
        OtcOffer ourCounter = new OtcOffer(
                actualRead.stock(), actualRead.settlementDate(),
                new MonetaryValue(CurrencyCode.USD, new BigDecimal("190")),
                new MonetaryValue(CurrencyCode.USD, new BigDecimal("900")),
                buyer, seller, actualRead.amount(),
                buyer); // mi smo poslednji izmenili
        service.postCounterOffer(negotiationId, ourCounter);

        // 4. ACCEPT — predpostavljamo da je seller posle nase counter ponude prihvatio
        //    i sad kao buyer kliknemo "Prihvati"
        service.acceptOffer(negotiationId);

        // Provera: pozivi prolaze kroz klijenta tacno tim redom
        var ord = inOrder(client);
        ord.verify(client).postNegotiation(eq(SELLER_RN), eq(initial));
        ord.verify(client).getNegotiation(negotiationId);
        ord.verify(client).putCounterOffer(eq(negotiationId), eq(ourCounter));
        ord.verify(client).acceptNegotiation(negotiationId);
    }

    @Test
    @DisplayName("Close iz pune sredine pregovora poziva DELETE")
    void closeNegotiation_inMidNegotiation() {
        InterbankProperties properties = new InterbankProperties();
        properties.setMyRoutingNumber(OUR_RN);
        OtcNegotiationService service = new OtcNegotiationService(client, properties,
                negotiationRepository, contractRepository, portfolioRepository,
                clientRepository, employeeRepository, transactionExecutor);

        ForeignBankId negotiationId = new ForeignBankId(SELLER_RN, "neg-77");
        when(client.postNegotiation(eq(SELLER_RN), any()))
                .thenReturn(negotiationId);
        service.createNegotiation(new OtcOffer(
                new StockDescription("AAPL"),
                OffsetDateTime.now().plusDays(7),
                new MonetaryValue(CurrencyCode.USD, BigDecimal.valueOf(200)),
                new MonetaryValue(CurrencyCode.USD, BigDecimal.valueOf(500)),
                new ForeignBankId(OUR_RN, "b"),
                new ForeignBankId(SELLER_RN, "s"),
                BigDecimal.valueOf(10),
                new ForeignBankId(OUR_RN, "b")));

        service.closeNegotiation(negotiationId);

        var ord = inOrder(client);
        ord.verify(client).postNegotiation(eq(SELLER_RN), any());
        ord.verify(client).deleteNegotiation(negotiationId);
    }
}