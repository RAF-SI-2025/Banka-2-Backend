package rs.raf.banka2_bek.stock.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.exchange.dto.ExchangeRateDto;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingDailyPriceInfo;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingDailyPriceInfoRepository;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.stock.service.implementation.ListingServiceImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentMatchers;

/**
 * Coverage tests for ListingServiceImpl — targets paths not covered by
 * ListingServiceImplTest: refreshPrices, fetchAlphaVantagePrice, fetchForexPrice,
 * saveDailyPriceSnapshot, scheduledRefresh, loadInitialData, filter branches.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ListingServiceImplCoverageTest {

    @Mock private ListingRepository listingRepository;
    @Mock private ListingDailyPriceInfoRepository dailyPriceRepository;
    @Mock private RestTemplate restTemplate;
    @Mock private ExchangeService exchangeService;

    @InjectMocks
    private ListingServiceImpl listingService;

    @BeforeEach
    void setUp() {
        // Use short delay in tests if possible — set api keys so round-robin works
        ReflectionTestUtils.setField(listingService, "stockApiKeys", "KEY1,KEY2,KEY3");
        ReflectionTestUtils.setField(listingService, "stockApiUrl", "https://test.local/query");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Listing stock(String ticker, BigDecimal price) {
        Listing l = new Listing();
        l.setId(1L);
        l.setTicker(ticker);
        l.setName(ticker);
        l.setListingType(ListingType.STOCK);
        l.setPrice(price);
        l.setVolume(100_000L);
        return l;
    }

    private Listing forex(String ticker, String base, String quote, BigDecimal price) {
        Listing l = new Listing();
        l.setId(2L);
        l.setTicker(ticker);
        l.setName(ticker);
        l.setListingType(ListingType.FOREX);
        l.setPrice(price);
        l.setBaseCurrency(base);
        l.setQuoteCurrency(quote);
        l.setVolume(50_000L);
        return l;
    }

    private Listing futures(String ticker, BigDecimal price) {
        Listing l = new Listing();
        l.setId(3L);
        l.setTicker(ticker);
        l.setName(ticker);
        l.setListingType(ListingType.FUTURES);
        l.setPrice(price);
        l.setVolume(200_000L);
        return l;
    }

    // ========================================================================
    // getListings — filter branches (priceMin/Max, exchangePrefix, dates)
    // ========================================================================

    @Test
    @DisplayName("getListings sa punim filterima (exchangePrefix, priceRange, settlementDates) prolazi")
    void getListings_withAllFilters_ok() {
        when(listingRepository.findAll(ArgumentMatchers.<Specification<Listing>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stock("AAPL", BigDecimal.valueOf(150)))));

        var result = listingService.getListings(
                "STOCK", "app", "NASDAQ",
                BigDecimal.valueOf(10), BigDecimal.valueOf(500),
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(10),
                0, 20);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getListings baca IllegalArgumentException kad priceMin > priceMax")
    void getListings_priceMinGreaterThanMax_throws() {
        assertThatThrownBy(() -> listingService.getListings(
                "STOCK", null, null,
                BigDecimal.valueOf(500), BigDecimal.valueOf(100),
                null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Minimalna");
    }

    @Test
    @DisplayName("getListings dozvoljava priceMin == priceMax")
    void getListings_priceMinEqualsMax_ok() {
        when(listingRepository.findAll(ArgumentMatchers.<Specification<Listing>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        var result = listingService.getListings(
                "STOCK", null, null,
                BigDecimal.valueOf(100), BigDecimal.valueOf(100),
                null, null, 0, 20);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("getListings sa samo priceMin (bez max) ne pada")
    void getListings_onlyPriceMin_ok() {
        when(listingRepository.findAll(ArgumentMatchers.<Specification<Listing>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        listingService.getListings(
                "STOCK", null, null,
                BigDecimal.valueOf(50), null,
                null, null, 0, 20);

        verify(listingRepository, times(1))
                .findAll(ArgumentMatchers.<Specification<Listing>>any(), any(Pageable.class));
    }

    @Test
    @DisplayName("getListings isClient null auth — ne pada u isClient")
    void getListings_noAuthContext_ok() {
        // clear context so SecurityContextHolder.getAuthentication() returns null
        SecurityContextHolder.clearContext();
        when(listingRepository.findAll(ArgumentMatchers.<Specification<Listing>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        var result = listingService.getListings("FOREX", null, 0, 20);

        assertThat(result.getContent()).isEmpty();
    }

    // ========================================================================
    // refreshPrices — STOCK branches
    // ========================================================================

    @Test
    @DisplayName("refreshPrices STOCK sa null currentPrice — preskace")
    void refreshPrices_stockNullPrice_skips() {
        Listing l = stock("AAPL", null);
        when(listingRepository.findAll()).thenReturn(List.of(l));

        listingService.refreshPrices();

        // ListingRepository.saveAll se ipak zove (na praznoj listi promena)
        verify(listingRepository).saveAll(any());
        // Alpha vantage se NE zove
        verify(restTemplate, never()).getForObject(anyString(), eq(Map.class));
    }

    @Test
    @DisplayName("refreshPrices STOCK kad Alpha Vantage vrati null — koristi random simulation")
    void refreshPrices_stock_alphaReturnsNull_fallback() {
        Listing l = stock("AAPL", BigDecimal.valueOf(100));
        when(listingRepository.findAll()).thenReturn(List.of(l));
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(null);
        when(dailyPriceRepository.findByListingIdAndDate(eq(1L), any())).thenReturn(List.of());

        listingService.refreshPrices();

        assertThat(l.getPrice()).isNotNull();
        assertThat(l.getAsk()).isNotNull();
        assertThat(l.getBid()).isNotNull();
        assertThat(l.getLastRefresh()).isNotNull();
        verify(dailyPriceRepository).save(any(ListingDailyPriceInfo.class));
    }

    @Test
    @DisplayName("refreshPrices STOCK kad Alpha Vantage vrati response bez Global Quote — fallback")
    void refreshPrices_stock_noGlobalQuoteKey_fallback() {
        Listing l = stock("AAPL", BigDecimal.valueOf(100));
        when(listingRepository.findAll()).thenReturn(List.of(l));
        Map<String, Object> response = new HashMap<>();
        response.put("Other Key", "value");
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);
        when(dailyPriceRepository.findByListingIdAndDate(eq(1L), any())).thenReturn(List.of());

        listingService.refreshPrices();

        assertThat(l.getPrice()).isNotNull();
    }

    @Test
    @DisplayName("refreshPrices STOCK kad quote map je prazan — fallback")
    void refreshPrices_stock_emptyQuote_fallback() {
        Listing l = stock("AAPL", BigDecimal.valueOf(100));
        when(listingRepository.findAll()).thenReturn(List.of(l));
        Map<String, Object> response = new HashMap<>();
        response.put("Global Quote", new HashMap<String, String>());
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);
        when(dailyPriceRepository.findByListingIdAndDate(eq(1L), any())).thenReturn(List.of());

        listingService.refreshPrices();

        assertThat(l.getPrice()).isNotNull();
    }

    @Test
    @DisplayName("refreshPrices STOCK kad quote map nema '05. price' — fallback")
    void refreshPrices_stock_nullPrice_fallback() {
        Listing l = stock("AAPL", BigDecimal.valueOf(100));
        when(listingRepository.findAll()).thenReturn(List.of(l));
        Map<String, Object> response = new HashMap<>();
        Map<String, String> quote = new HashMap<>();
        quote.put("something", "else");
        response.put("Global Quote", quote);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);
        when(dailyPriceRepository.findByListingIdAndDate(eq(1L), any())).thenReturn(List.of());

        listingService.refreshPrices();

        assertThat(l.getPrice()).isNotNull();
    }

    @Test
    @DisplayName("refreshPrices STOCK sa RestTemplate exception — fallback (log warn)")
    void refreshPrices_stock_restException_fallback() {
        Listing l = stock("AAPL", BigDecimal.valueOf(100));
        when(listingRepository.findAll()).thenReturn(List.of(l));
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RestClientException("boom"));
        when(dailyPriceRepository.findByListingIdAndDate(eq(1L), any())).thenReturn(List.of());

        listingService.refreshPrices();

        assertThat(l.getPrice()).isNotNull();
    }

    @Test
    @DisplayName("refreshPrices STOCK sa volume==null ide na default 100000")
    void refreshPrices_stock_nullVolume_defaultsTo100k() {
        Listing l = stock("AAPL", BigDecimal.valueOf(100));
        l.setVolume(null);
        when(listingRepository.findAll()).thenReturn(List.of(l));
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(null);
        when(dailyPriceRepository.findByListingIdAndDate(eq(1L), any())).thenReturn(List.of());

        listingService.refreshPrices();

        assertThat(l.getVolume()).isEqualTo(100000L);
    }

    // ========================================================================
    // fetchAlphaVantagePrice — happy path with real response data
    // NOTE: This triggers Thread.sleep(12000) — test will take ~12s.
    // ========================================================================

    @Test
    @DisplayName("refreshPrices STOCK sa pravim Alpha Vantage response (ukljucujuci high/low)")
    void refreshPrices_stock_fullAlphaResponse() {
        Listing l = stock("AAPL", BigDecimal.valueOf(100));
        when(listingRepository.findAll()).thenReturn(List.of(l));

        Map<String, Object> response = new HashMap<>();
        Map<String, String> quote = new HashMap<>();
        quote.put("05. price", "175.5000");
        quote.put("08. previous close", "170.0000");
        quote.put("06. volume", "5000000");
        quote.put("03. high", "178.0000");
        quote.put("04. low", "173.0000");
        response.put("Global Quote", quote);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);
        when(dailyPriceRepository.findByListingIdAndDate(eq(1L), any())).thenReturn(List.of());

        listingService.refreshPrices();

        assertThat(l.getPrice()).isEqualByComparingTo(new BigDecimal("175.5000"));
        // Price change should be 5.5
        assertThat(l.getPriceChange()).isEqualByComparingTo(new BigDecimal("5.5000"));
        assertThat(l.getVolume()).isEqualTo(5000000L);
        // newAsk/newBid overridden by high/low
        assertThat(l.getAsk()).isEqualByComparingTo(new BigDecimal("178.0000"));
        assertThat(l.getBid()).isEqualByComparingTo(new BigDecimal("173.0000"));
    }

    @Test
    @DisplayName("refreshPrices STOCK sa Alpha response bez previousClose/volume/high/low")
    void refreshPrices_stock_partialAlphaResponse() {
        Listing l = stock("AAPL", BigDecimal.valueOf(100));
        when(listingRepository.findAll()).thenReturn(List.of(l));

        Map<String, Object> response = new HashMap<>();
        Map<String, String> quote = new HashMap<>();
        quote.put("05. price", "200.0000");
        // nema previous close, volume, high, low
        response.put("Global Quote", quote);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);
        when(dailyPriceRepository.findByListingIdAndDate(eq(1L), any())).thenReturn(List.of());

        listingService.refreshPrices();

        assertThat(l.getPrice()).isEqualByComparingTo(new BigDecimal("200.0000"));
        assertThat(l.getVolume()).isEqualTo(100000L);
    }

    // ========================================================================
    // refreshPrices FOREX branches
    // ========================================================================

    @Test
    @DisplayName("refreshPrices FOREX sa uspesnim fixer.io response")
    void refreshPrices_forex_success() {
        Listing l = forex("EUR/USD", "EUR", "USD", BigDecimal.valueOf(1.1));
        when(listingRepository.findAll()).thenReturn(List.of(l));

        List<ExchangeRateDto> rates = new ArrayList<>();
        rates.add(new ExchangeRateDto("EUR", 0.0085));
        rates.add(new ExchangeRateDto("USD", 0.0091));
        when(exchangeService.getAllRates()).thenReturn(rates);
        when(dailyPriceRepository.findByListingIdAndDate(eq(2L), any())).thenReturn(List.of());

        listingService.refreshPrices();

        assertThat(l.getPrice()).isNotNull();
        assertThat(l.getAsk()).isNotNull();
        assertThat(l.getBid()).isNotNull();
    }

    @Test
    @DisplayName("refreshPrices FOREX sa null baseCurrency — fallback na random")
    void refreshPrices_forex_nullBaseCurrency_fallback() {
        Listing l = forex("EUR/USD", null, "USD", BigDecimal.valueOf(1.1));
        when(listingRepository.findAll()).thenReturn(List.of(l));
        when(dailyPriceRepository.findByListingIdAndDate(eq(2L), any())).thenReturn(List.of());

        listingService.refreshPrices();

        assertThat(l.getPrice()).isNotNull();
        // exchangeService should NOT be called with null currencies ideally, but fetchForexPrice checks null → returns null
        // Either way, fallback path runs
    }

    @Test
    @DisplayName("refreshPrices FOREX kad rate nije pronadjen u listi — fallback")
    void refreshPrices_forex_rateNotFound_fallback() {
        Listing l = forex("XXX/YYY", "XXX", "YYY", BigDecimal.valueOf(1.0));
        when(listingRepository.findAll()).thenReturn(List.of(l));
        when(exchangeService.getAllRates()).thenReturn(List.of(new ExchangeRateDto("EUR", 0.0085)));
        when(dailyPriceRepository.findByListingIdAndDate(eq(2L), any())).thenReturn(List.of());

        listingService.refreshPrices();

        assertThat(l.getPrice()).isNotNull();
    }

    @Test
    @DisplayName("refreshPrices FOREX kad baseRate je 0 — fallback")
    void refreshPrices_forex_baseRateZero_fallback() {
        Listing l = forex("EUR/USD", "EUR", "USD", BigDecimal.valueOf(1.1));
        when(listingRepository.findAll()).thenReturn(List.of(l));
        List<ExchangeRateDto> rates = new ArrayList<>();
        rates.add(new ExchangeRateDto("EUR", 0.0));
        rates.add(new ExchangeRateDto("USD", 0.0091));
        when(exchangeService.getAllRates()).thenReturn(rates);
        when(dailyPriceRepository.findByListingIdAndDate(eq(2L), any())).thenReturn(List.of());

        listingService.refreshPrices();

        assertThat(l.getPrice()).isNotNull();
    }

    @Test
    @DisplayName("refreshPrices FOREX kad exchangeService baca exception — fallback")
    void refreshPrices_forex_exchangeServiceException_fallback() {
        Listing l = forex("EUR/USD", "EUR", "USD", BigDecimal.valueOf(1.1));
        when(listingRepository.findAll()).thenReturn(List.of(l));
        when(exchangeService.getAllRates()).thenThrow(new RuntimeException("api down"));
        when(dailyPriceRepository.findByListingIdAndDate(eq(2L), any())).thenReturn(List.of());

        listingService.refreshPrices();

        assertThat(l.getPrice()).isNotNull();
    }

    @Test
    @DisplayName("refreshPrices FOREX sa volume==null")
    void refreshPrices_forex_nullVolume() {
        Listing l = forex("EUR/USD", "EUR", "USD", BigDecimal.valueOf(1.1));
        l.setVolume(null);
        when(listingRepository.findAll()).thenReturn(List.of(l));
        List<ExchangeRateDto> rates = new ArrayList<>();
        rates.add(new ExchangeRateDto("EUR", 0.0085));
        rates.add(new ExchangeRateDto("USD", 0.0091));
        when(exchangeService.getAllRates()).thenReturn(rates);
        when(dailyPriceRepository.findByListingIdAndDate(eq(2L), any())).thenReturn(List.of());

        listingService.refreshPrices();

        assertThat(l.getVolume()).isEqualTo(100000L);
    }

    @Test
    @DisplayName("refreshPrices FOREX fallback sa volume==null")
    void refreshPrices_forex_fallbackNullVolume() {
        Listing l = forex("EUR/USD", null, "USD", BigDecimal.valueOf(1.1));
        l.setVolume(null);
        when(listingRepository.findAll()).thenReturn(List.of(l));
        when(dailyPriceRepository.findByListingIdAndDate(eq(2L), any())).thenReturn(List.of());

        listingService.refreshPrices();

        assertThat(l.getVolume()).isEqualTo(100000L);
    }

    // ========================================================================
    // refreshPrices FUTURES (random simulation only)
    // ========================================================================

    @Test
    @DisplayName("refreshPrices FUTURES pravi random simulation")
    void refreshPrices_futures_randomSimulation() {
        Listing l = futures("CLJ26", BigDecimal.valueOf(80));
        when(listingRepository.findAll()).thenReturn(List.of(l));
        when(dailyPriceRepository.findByListingIdAndDate(eq(3L), any())).thenReturn(List.of());

        listingService.refreshPrices();

        assertThat(l.getPrice()).isNotNull();
        assertThat(l.getAsk()).isNotNull();
        assertThat(l.getBid()).isNotNull();
        verify(restTemplate, never()).getForObject(anyString(), eq(Map.class));
    }

    @Test
    @DisplayName("refreshPrices FUTURES sa volume==null")
    void refreshPrices_futures_nullVolume() {
        Listing l = futures("CLJ26", BigDecimal.valueOf(80));
        l.setVolume(null);
        when(listingRepository.findAll()).thenReturn(List.of(l));
        when(dailyPriceRepository.findByListingIdAndDate(eq(3L), any())).thenReturn(List.of());

        listingService.refreshPrices();

        assertThat(l.getVolume()).isEqualTo(100000L);
    }

    // ========================================================================
    // saveDailyPriceSnapshot — existing today branch (update instead of insert)
    // ========================================================================

    @Test
    @DisplayName("saveDailyPriceSnapshot azurira postojeci snapshot kad vec postoji za danas")
    void saveDailyPriceSnapshot_existingToday_updates() {
        Listing l = futures("CLJ26", BigDecimal.valueOf(80));
        when(listingRepository.findAll()).thenReturn(List.of(l));

        ListingDailyPriceInfo existing = new ListingDailyPriceInfo();
        existing.setListing(l);
        existing.setDate(LocalDate.now());
        existing.setPrice(BigDecimal.valueOf(79));
        existing.setHigh(BigDecimal.valueOf(80));
        existing.setLow(BigDecimal.valueOf(78));
        existing.setChange(BigDecimal.valueOf(0.5));
        existing.setVolume(100L);

        when(dailyPriceRepository.findByListingIdAndDate(eq(3L), eq(LocalDate.now())))
                .thenReturn(List.of(existing));

        listingService.refreshPrices();

        verify(dailyPriceRepository).save(existing);
        // existing should have been mutated
        assertThat(existing.getPrice()).isEqualTo(l.getPrice());
    }

    @Test
    @DisplayName("saveDailyPriceSnapshot sa existing kada je novi high veci i low manji — azurira oba")
    void saveDailyPriceSnapshot_updatesHighLow() {
        Listing l = futures("CLJ26", BigDecimal.valueOf(80));
        when(listingRepository.findAll()).thenReturn(List.of(l));

        ListingDailyPriceInfo existing = new ListingDailyPriceInfo();
        existing.setListing(l);
        existing.setDate(LocalDate.now());
        existing.setPrice(BigDecimal.valueOf(79));
        // Start with extreme values so any new high/low will differ
        existing.setHigh(BigDecimal.valueOf(1));
        existing.setLow(BigDecimal.valueOf(10000));
        existing.setChange(BigDecimal.ZERO);
        existing.setVolume(100L);

        when(dailyPriceRepository.findByListingIdAndDate(eq(3L), eq(LocalDate.now())))
                .thenReturn(List.of(existing));

        listingService.refreshPrices();

        // high should be replaced by new higher value
        assertThat(existing.getHigh()).isEqualTo(l.getAsk());
        assertThat(existing.getLow()).isEqualTo(l.getBid());
    }

    // ========================================================================
    // Round-robin API key
    // ========================================================================

    @Test
    @DisplayName("getNextApiKey rotira kroz kljuceve (round-robin) preko vise STOCK listinga")
    void apiKeyRoundRobin() {
        // Use two listings to force two calls. Return null so we don't sleep 12s.
        Listing l1 = stock("AAPL", BigDecimal.valueOf(100));
        l1.setId(1L);
        Listing l2 = stock("MSFT", BigDecimal.valueOf(200));
        l2.setId(10L);
        when(listingRepository.findAll()).thenReturn(List.of(l1, l2));
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(null);
        when(dailyPriceRepository.findByListingIdAndDate(any(), any())).thenReturn(List.of());

        listingService.refreshPrices();

        verify(restTemplate, times(2)).getForObject(anyString(), eq(Map.class));
    }

    // ========================================================================
    // scheduledRefresh + loadInitialData
    // ========================================================================

    @Test
    @DisplayName("scheduledRefresh delegira na refreshPrices (prazna baza)")
    void scheduledRefresh_delegates() {
        when(listingRepository.findAll()).thenReturn(List.of());

        listingService.scheduledRefresh();

        verify(listingRepository).findAll();
        verify(listingRepository).saveAll(any());
    }

    @Test
    @DisplayName("loadInitialData sa count=0 loguje warn")
    void loadInitialData_emptyDb() {
        when(listingRepository.count()).thenReturn(0L);

        listingService.loadInitialData();

        verify(listingRepository).count();
    }

    @Test
    @DisplayName("loadInitialData sa count>0 loguje info")
    void loadInitialData_nonEmptyDb() {
        when(listingRepository.count()).thenReturn(42L);

        listingService.loadInitialData();

        verify(listingRepository).count();
    }
}
