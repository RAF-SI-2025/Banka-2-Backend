package rs.raf.banka2_bek.interbank.service;

import org.junit.jupiter.api.BeforeEach;
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
import rs.raf.banka2_bek.interbank.protocol.PublicStock;
import rs.raf.banka2_bek.interbank.protocol.StockDescription;
import rs.raf.banka2_bek.interbank.protocol.UserInformation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OtcNegotiationService — outbound (T2)")
class OtcNegotiationServiceTest {

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

    private InterbankProperties properties;
    private OtcNegotiationService service;

    @BeforeEach
    void setUp() {
        properties = new InterbankProperties();
        properties.setMyRoutingNumber(OUR_RN);
        properties.setMyBankDisplayName("Banka 2");
        service = new OtcNegotiationService(client, properties,
                negotiationRepository, contractRepository,
                portfolioRepository, clientRepository, employeeRepository,
                transactionExecutor);
    }

    // ───────────────────── §3.1 fetchRemotePublicStocks ─────────────────────

    @Test
    @DisplayName("§3.1 prvi poziv ide ka klijentu, drugi se vraca iz cache-a")
    void fetchRemotePublicStocks_secondCall_servedFromCache() {
        List<PublicStock> stocks = List.of(
                new PublicStock(new StockDescription("AAPL"), List.of()));
        when(client.fetchPublicStocks(SELLER_RN)).thenReturn(stocks);

        List<PublicStock> first = service.fetchRemotePublicStocks(SELLER_RN);
        List<PublicStock> second = service.fetchRemotePublicStocks(SELLER_RN);

        assertThat(first).isSameAs(stocks);
        assertThat(second).isSameAs(stocks);
        verify(client, times(1)).fetchPublicStocks(SELLER_RN);
    }

    @Test
    @DisplayName("§3.1 cache razlikuje partner banke po routing number-u")
    void fetchRemotePublicStocks_separateBanks_cachedSeparately() {
        when(client.fetchPublicStocks(SELLER_RN)).thenReturn(List.of());
        when(client.fetchPublicStocks(333)).thenReturn(List.of());

        service.fetchRemotePublicStocks(SELLER_RN);
        service.fetchRemotePublicStocks(333);
        service.fetchRemotePublicStocks(SELLER_RN);
        service.fetchRemotePublicStocks(333);

        verify(client, times(1)).fetchPublicStocks(SELLER_RN);
        verify(client, times(1)).fetchPublicStocks(333);
    }

    @Test
    @DisplayName("§3.1 stale cache entry se osvezava kad TTL istekne")
    void fetchRemotePublicStocks_refetches_whenEntryStale() {
        when(client.fetchPublicStocks(SELLER_RN)).thenReturn(List.of());

        service.fetchRemotePublicStocks(SELLER_RN);
        // Force expiry pomocu test hook-a — simulira proteklu TTL
        service.invalidatePublicStockCache(SELLER_RN);
        service.fetchRemotePublicStocks(SELLER_RN);

        verify(client, times(2)).fetchPublicStocks(SELLER_RN);
    }

    @Test
    @DisplayName("§3.1 CachedPublicStocks.isFresh reflektuje TTL")
    void cachedEntry_isFresh_respectsTtl() {
        var entry = new OtcNegotiationService.CachedPublicStocks(
                List.of(),
                java.time.Instant.now().minus(OtcNegotiationService.PUBLIC_STOCK_TTL).minusSeconds(1));
        assertThat(entry.isFresh(java.time.Instant.now())).isFalse();

        var fresh = new OtcNegotiationService.CachedPublicStocks(
                List.of(), java.time.Instant.now());
        assertThat(fresh.isFresh(java.time.Instant.now())).isTrue();
    }

    // ───────────────────── §3.2 createNegotiation ─────────────────────

    @Test
    @DisplayName("§3.2 prosledjuje POST i vraca negotiationId")
    void createNegotiation_postsThroughClient_andReturnsId() {
        OtcOffer offer = sampleOffer();
        ForeignBankId expected = new ForeignBankId(SELLER_RN, "neg-1");
        when(client.postNegotiation(eq(SELLER_RN), eq(offer))).thenReturn(expected);

        ForeignBankId result = service.createNegotiation(offer);

        assertThat(result).isEqualTo(expected);
        verify(client).postNegotiation(SELLER_RN, offer);
    }

    @Test
    @DisplayName("§3.2 invalida cache prodavceve banke posle uspesnog kreiranja")
    void createNegotiation_invalidatesCache_forSellerBank() {
        when(client.fetchPublicStocks(SELLER_RN)).thenReturn(List.of());
        service.fetchRemotePublicStocks(SELLER_RN);
        verify(client, times(1)).fetchPublicStocks(SELLER_RN);

        OtcOffer offer = sampleOffer();
        when(client.postNegotiation(eq(SELLER_RN), any()))
                .thenReturn(new ForeignBankId(SELLER_RN, "neg-1"));
        service.createNegotiation(offer);

        // Sledeci fetch mora ponovo udariti klijenta
        service.fetchRemotePublicStocks(SELLER_RN);
        verify(client, times(2)).fetchPublicStocks(SELLER_RN);
    }

    @Test
    @DisplayName("§3.2 odbija ako lastModifiedBy != buyerId")
    void createNegotiation_rejects_whenLastModifiedByNotBuyer() {
        ForeignBankId buyer = new ForeignBankId(OUR_RN, "user-1");
        ForeignBankId seller = new ForeignBankId(SELLER_RN, "user-2");
        OtcOffer bad = new OtcOffer(
                new StockDescription("AAPL"),
                OffsetDateTime.now().plusDays(7),
                new MonetaryValue(CurrencyCode.USD, new BigDecimal("200")),
                new MonetaryValue(CurrencyCode.USD, new BigDecimal("700")),
                buyer, seller, BigDecimal.valueOf(50),
                seller // lastModifiedBy = seller, ne buyer
        );

        assertThatThrownBy(() -> service.createNegotiation(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lastModifiedBy");
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("§3.2 odbija nevalidan amount / settlementDate / buyer==seller")
    void createNegotiation_validatesInvariants() {
        // amount <= 0
        OtcOffer zeroAmount = withAmount(sampleOffer(), BigDecimal.ZERO);
        assertThatThrownBy(() -> service.createNegotiation(zeroAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");

        // settlementDate u proslosti
        OtcOffer pastDate = withSettlement(sampleOffer(), OffsetDateTime.now().minusDays(1));
        assertThatThrownBy(() -> service.createNegotiation(pastDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("settlementDate");

        // buyer == seller
        ForeignBankId same = new ForeignBankId(OUR_RN, "x");
        OtcOffer selfTrade = new OtcOffer(
                new StockDescription("AAPL"),
                OffsetDateTime.now().plusDays(7),
                new MonetaryValue(CurrencyCode.USD, BigDecimal.TEN),
                new MonetaryValue(CurrencyCode.USD, BigDecimal.TEN),
                same, same, BigDecimal.ONE, same);
        assertThatThrownBy(() -> service.createNegotiation(selfTrade))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(client);
    }

    // ───────────────────── §3.3 postCounterOffer ─────────────────────

    @Test
    @DisplayName("§3.3 prosledjuje PUT i loguje")
    void postCounterOffer_delegates() {
        ForeignBankId negotiationId = new ForeignBankId(SELLER_RN, "neg-1");
        OtcOffer counter = sampleOffer(); // lastModifiedBy = buyer (us)

        service.postCounterOffer(negotiationId, counter);

        verify(client).putCounterOffer(negotiationId, counter);
    }

    @Test
    @DisplayName("§3.3 odbija ako lastModifiedBy nije iz nase banke (rn != myRoutingNumber)")
    void postCounterOffer_rejects_whenLastModifiedByNotOurs() {
        ForeignBankId negotiationId = new ForeignBankId(SELLER_RN, "neg-1");
        OtcOffer base = sampleOffer();
        // lastModifiedBy je iz remote banke — ne sme da posaljemo counter "u njihovo ime"
        OtcOffer notOurs = new OtcOffer(
                base.stock(), base.settlementDate(),
                base.pricePerUnit(), base.premium(),
                base.buyerId(), base.sellerId(), base.amount(),
                base.sellerId());

        assertThatThrownBy(() -> service.postCounterOffer(negotiationId, notOurs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nase banke");
        verify(client, never()).putCounterOffer(any(), any());
    }

    // ───────────────────── §3.4 readNegotiation ─────────────────────

    @Test
    @DisplayName("§3.4 prosledjuje GET i vraca remote stanje")
    void readNegotiation_delegates() {
        ForeignBankId negotiationId = new ForeignBankId(SELLER_RN, "neg-1");
        OtcNegotiation stub = new OtcNegotiation(
                new StockDescription("AAPL"),
                OffsetDateTime.now().plusDays(7),
                new MonetaryValue(CurrencyCode.USD, new BigDecimal("200")),
                new MonetaryValue(CurrencyCode.USD, new BigDecimal("700")),
                new ForeignBankId(OUR_RN, "buyer"),
                new ForeignBankId(SELLER_RN, "seller"),
                BigDecimal.valueOf(50),
                new ForeignBankId(OUR_RN, "buyer"),
                true);
        when(client.getNegotiation(negotiationId)).thenReturn(stub);

        OtcNegotiation result = service.readNegotiation(negotiationId);

        assertThat(result).isSameAs(stub);
        assertThat(result.isOngoing()).isTrue();
    }

    // ───────────────────── §3.5 closeNegotiation ─────────────────────

    @Test
    @DisplayName("§3.5 prosledjuje DELETE")
    void closeNegotiation_delegates() {
        ForeignBankId negotiationId = new ForeignBankId(SELLER_RN, "neg-1");
        service.closeNegotiation(negotiationId);
        verify(client).deleteNegotiation(negotiationId);
    }

    // ───────────────────── §3.6 acceptOffer ─────────────────────

    @Test
    @DisplayName("§3.6 prosledjuje GET .../accept i invalida cache prodavceve banke")
    void acceptOffer_delegates_andInvalidatesCache() {
        when(client.fetchPublicStocks(SELLER_RN)).thenReturn(List.of());
        service.fetchRemotePublicStocks(SELLER_RN);
        verify(client, times(1)).fetchPublicStocks(SELLER_RN);

        ForeignBankId negotiationId = new ForeignBankId(SELLER_RN, "neg-1");
        service.acceptOffer(negotiationId);

        verify(client).acceptNegotiation(negotiationId);
        // Cache prodavceve banke invalidiran -> sledeci fetch ide ponovo na klijent
        service.fetchRemotePublicStocks(SELLER_RN);
        verify(client, times(2)).fetchPublicStocks(SELLER_RN);
    }

    // ───────────────────── §3.7 resolveUserName ─────────────────────

    @Test
    @DisplayName("§3.7 prosledjuje GET /user/{rn}/{id}")
    void resolveUserName_delegates() {
        ForeignBankId userId = new ForeignBankId(SELLER_RN, "u-1");
        UserInformation info = new UserInformation("Banka 1", "Pera Peric");
        when(client.getUserInfo(userId)).thenReturn(info);

        UserInformation result = service.resolveUserName(userId);

        assertThat(result).isSameAs(info);
    }

    // ───────────────────── helpers ─────────────────────

    private OtcOffer sampleOffer() {
        ForeignBankId buyer = new ForeignBankId(OUR_RN, "buyer-1");
        ForeignBankId seller = new ForeignBankId(SELLER_RN, "seller-1");
        return new OtcOffer(
                new StockDescription("AAPL"),
                OffsetDateTime.now().plusDays(7),
                new MonetaryValue(CurrencyCode.USD, new BigDecimal("200")),
                new MonetaryValue(CurrencyCode.USD, new BigDecimal("700")),
                buyer, seller, BigDecimal.valueOf(50),
                buyer);
    }

    private static OtcOffer withAmount(OtcOffer o, BigDecimal newAmount) {
        return new OtcOffer(o.stock(), o.settlementDate(), o.pricePerUnit(),
                o.premium(), o.buyerId(), o.sellerId(), newAmount, o.lastModifiedBy());
    }

    private static OtcOffer withSettlement(OtcOffer o, OffsetDateTime when) {
        return new OtcOffer(o.stock(), when, o.pricePerUnit(),
                o.premium(), o.buyerId(), o.sellerId(), o.amount(), o.lastModifiedBy());
    }
}