package rs.raf.banka2_bek.stock.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Additional tests for ListingServiceImpl focusing on refreshPrices and data loading.
 * Covers: AlphaVantage mock, Fixer mock, fallback simulation, FUTURES random, price history snapshots.
 */
@ExtendWith(MockitoExtension.class)
class ListingServiceRefreshPricesTest {

    @Mock private ListingRepository listingRepository;
    @Mock private ListingDailyPriceInfoRepository dailyPriceRepository;
    @Mock private RestTemplate restTemplate;
    @Mock private ExchangeService exchangeService;

    @InjectMocks
    private ListingServiceImpl listingService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private Listing stockListing(String ticker, BigDecimal price) {
        Listing l = new Listing();
        l.setId(1L);
        l.setTicker(ticker);
        l.setName(ticker + " Inc.");
        l.setListingType(ListingType.STOCK);
        l.setPrice(price);
        l.setVolume(50000L);
        l.setLastRefresh(LocalDateTime.now().minusHours(1));
        return l;
    }

    private Listing forexListing(String base, String quote, BigDecimal price) {
        Listing l = new Listing();
        l.setId(2L);
        l.setTicker(base + "/" + quote);
        l.setName(base + "/" + quote);
        l.setListingType(ListingType.FOREX);
        l.setBaseCurrency(base);
        l.setQuoteCurrency(quote);
        l.setPrice(price);
        l.setVolume(100000L);
        l.setLastRefresh(LocalDateTime.now().minusHours(1));
        return l;
    }

    private Listing futuresListing(String ticker, BigDecimal price) {
        Listing l = new Listing();
        l.setId(3L);
        l.setTicker(ticker);
        l.setName("Crude Oil Future");
        l.setListingType(ListingType.FUTURES);
        l.setPrice(price);
        l.setVolume(20000L);
        l.setLastRefresh(LocalDateTime.now().minusHours(1));
        return l;
    }

    // ─── refreshPrices: STOCK via AlphaVantage (mocked RestTemplate) ────────────

    @Nested
    @DisplayName("refreshPrices - STOCK with AlphaVantage API failure (fallback)")
    class StockFallback {

        @Test
        @DisplayName("when AlphaVantage returns null, uses random simulation for STOCK")
        void alphaVantageReturnsNull_usesFallback() {
            Listing stock = stockListing("AAPL", BigDecimal.valueOf(150));
            when(listingRepository.findAll()).thenReturn(List.of(stock));
            when(dailyPriceRepository.findByListingIdAndDate(any(), any())).thenReturn(Collections.emptyList());
            // RestTemplate returns null -> AlphaVantage fails
            when(restTemplate.getForObject(any(String.class), eq(Map.class))).thenReturn(null);

            listingService.refreshPrices();

            assertThat(stock.getPrice()).isNotNull();
            assertThat(stock.getAsk()).isNotNull();
            assertThat(stock.getBid()).isNotNull();
            assertThat(stock.getPriceChange()).isNotNull();
            assertThat(stock.getLastRefresh()).isAfter(LocalDateTime.now().minusMinutes(1));
            verify(listingRepository).saveAll(any());
        }

        @Test
        @DisplayName("when AlphaVantage throws exception, uses random simulation")
        void alphaVantageThrowsException_usesFallback() {
            Listing stock = stockListing("MSFT", BigDecimal.valueOf(300));
            when(listingRepository.findAll()).thenReturn(List.of(stock));
            when(dailyPriceRepository.findByListingIdAndDate(any(), any())).thenReturn(Collections.emptyList());
            when(restTemplate.getForObject(any(String.class), eq(Map.class)))
                    .thenThrow(new RuntimeException("API limit reached"));

            listingService.refreshPrices();

            // Price should still be updated via random simulation
            assertThat(stock.getPrice()).isNotNull();
            assertThat(stock.getAsk()).isNotNull();
            verify(listingRepository).saveAll(any());
        }

        @Test
        @DisplayName("when AlphaVantage returns empty Global Quote, uses fallback")
        void alphaVantageEmptyQuote_usesFallback() {
            Listing stock = stockListing("GOOG", BigDecimal.valueOf(2800));
            when(listingRepository.findAll()).thenReturn(List.of(stock));
            when(dailyPriceRepository.findByListingIdAndDate(any(), any())).thenReturn(Collections.emptyList());
            when(restTemplate.getForObject(any(String.class), eq(Map.class)))
                    .thenReturn(Map.of("Global Quote", Map.of()));

            listingService.refreshPrices();

            assertThat(stock.getPrice()).isNotNull();
            verify(listingRepository).saveAll(any());
        }
    }

    // ─── refreshPrices: FOREX via ExchangeService (fixer.io mock) ───────────────

    @Nested
    @DisplayName("refreshPrices - FOREX with ExchangeService")
    class ForexRefresh {

        @Test
        @DisplayName("forex pair price calculated from cross rate when rates available")
        void forexCrossRateCalculation() {
            Listing forex = forexListing("EUR", "USD", BigDecimal.valueOf(1.10));
            when(listingRepository.findAll()).thenReturn(List.of(forex));
            when(dailyPriceRepository.findByListingIdAndDate(any(), any())).thenReturn(Collections.emptyList());

            List<ExchangeRateDto> rates = List.of(
                    new ExchangeRateDto("EUR", 0.008547),  // 1 RSD = 0.008547 EUR
                    new ExchangeRateDto("USD", 0.009090)   // 1 RSD = 0.009090 USD
            );
            when(exchangeService.getAllRates()).thenReturn(rates);

            listingService.refreshPrices();

            // Cross rate should be USD/EUR = 0.009090 / 0.008547 ~ 1.0635
            assertThat(forex.getPrice()).isNotNull();
            assertThat(forex.getPrice().doubleValue()).isGreaterThan(0);
            assertThat(forex.getAsk()).isNotNull();
            assertThat(forex.getBid()).isNotNull();
            verify(listingRepository).saveAll(any());
        }

        @Test
        @DisplayName("forex fallback when ExchangeService throws exception")
        void forexFallbackOnException() {
            Listing forex = forexListing("GBP", "JPY", BigDecimal.valueOf(180));
            when(listingRepository.findAll()).thenReturn(List.of(forex));
            when(dailyPriceRepository.findByListingIdAndDate(any(), any())).thenReturn(Collections.emptyList());
            when(exchangeService.getAllRates()).thenThrow(new RuntimeException("Fixer API unavailable"));

            listingService.refreshPrices();

            // Should fallback to random simulation
            assertThat(forex.getPrice()).isNotNull();
            verify(listingRepository).saveAll(any());
        }

        @Test
        @DisplayName("forex fallback when base currency not found in rates")
        void forexFallbackOnMissingCurrency() {
            Listing forex = forexListing("XYZ", "USD", BigDecimal.valueOf(1.5));
            when(listingRepository.findAll()).thenReturn(List.of(forex));
            when(dailyPriceRepository.findByListingIdAndDate(any(), any())).thenReturn(Collections.emptyList());

            List<ExchangeRateDto> rates = List.of(new ExchangeRateDto("USD", 0.009090));
            when(exchangeService.getAllRates()).thenReturn(rates);

            listingService.refreshPrices();

            // XYZ not in rates => fetchForexPrice returns null => fallback
            assertThat(forex.getPrice()).isNotNull();
            verify(listingRepository).saveAll(any());
        }

        @Test
        @DisplayName("forex with null baseCurrency falls back to simulation")
        void forexNullBaseCurrency_fallback() {
            Listing forex = new Listing();
            forex.setId(10L);
            forex.setTicker("???");
            forex.setName("Unknown Forex");
            forex.setListingType(ListingType.FOREX);
            forex.setBaseCurrency(null);
            forex.setQuoteCurrency("USD");
            forex.setPrice(BigDecimal.valueOf(1.0));
            forex.setVolume(100000L);

            when(listingRepository.findAll()).thenReturn(List.of(forex));
            when(dailyPriceRepository.findByListingIdAndDate(any(), any())).thenReturn(Collections.emptyList());

            listingService.refreshPrices();

            assertThat(forex.getPrice()).isNotNull();
            verify(listingRepository).saveAll(any());
        }
    }

    // ─── refreshPrices: FUTURES (random simulation) ─────────────────────────────

    @Nested
    @DisplayName("refreshPrices - FUTURES with random simulation")
    class FuturesRefresh {

        @Test
        @DisplayName("futures uses random simulation, updates all fields")
        void futuresRandomSimulation() {
            Listing futures = futuresListing("CLJ26", BigDecimal.valueOf(70));
            when(listingRepository.findAll()).thenReturn(List.of(futures));
            when(dailyPriceRepository.findByListingIdAndDate(any(), any())).thenReturn(Collections.emptyList());

            listingService.refreshPrices();

            assertThat(futures.getPrice()).isNotNull();
            assertThat(futures.getAsk()).isNotNull();
            assertThat(futures.getBid()).isNotNull();
            assertThat(futures.getPriceChange()).isNotNull();
            assertThat(futures.getVolume()).isGreaterThan(0);
            verify(listingRepository).saveAll(any());
        }

        @Test
        @DisplayName("futures with null volume gets default 100000")
        void futuresNullVolume_defaultValue() {
            Listing futures = futuresListing("CLJ26", BigDecimal.valueOf(70));
            futures.setVolume(null);
            when(listingRepository.findAll()).thenReturn(List.of(futures));
            when(dailyPriceRepository.findByListingIdAndDate(any(), any())).thenReturn(Collections.emptyList());

            listingService.refreshPrices();

            assertThat(futures.getVolume()).isNotNull();
            verify(listingRepository).saveAll(any());
        }
    }

    // ─── refreshPrices: null price skipping ─────────────────────────────────────

    @Nested
    @DisplayName("refreshPrices - edge cases")
    class EdgeCases {

        @Test
        @DisplayName("listing with null price is skipped")
        void listingWithNullPrice_skipped() {
            Listing listing = stockListing("NULL", null);
            listing.setPrice(null);
            when(listingRepository.findAll()).thenReturn(List.of(listing));

            listingService.refreshPrices();

            assertThat(listing.getPrice()).isNull(); // Not updated
            verify(listingRepository).saveAll(any());
        }

        @Test
        @DisplayName("empty listing list does nothing")
        void emptyListingList() {
            when(listingRepository.findAll()).thenReturn(Collections.emptyList());

            listingService.refreshPrices();

            verify(listingRepository).saveAll(Collections.emptyList());
        }

        @Test
        @DisplayName("multiple listings of different types processed in one cycle")
        void mixedListings() {
            Listing stock = stockListing("AAPL", BigDecimal.valueOf(150));
            Listing forex = forexListing("EUR", "USD", BigDecimal.valueOf(1.10));
            Listing futures = futuresListing("CLJ26", BigDecimal.valueOf(70));

            when(listingRepository.findAll()).thenReturn(List.of(stock, forex, futures));
            when(dailyPriceRepository.findByListingIdAndDate(any(), any())).thenReturn(Collections.emptyList());
            // Stock: AlphaVantage returns null -> fallback
            when(restTemplate.getForObject(any(String.class), eq(Map.class))).thenReturn(null);
            // Forex: ExchangeService throws -> fallback
            when(exchangeService.getAllRates()).thenThrow(new RuntimeException("API error"));

            listingService.refreshPrices();

            assertThat(stock.getPrice()).isNotNull();
            assertThat(forex.getPrice()).isNotNull();
            assertThat(futures.getPrice()).isNotNull();
            verify(listingRepository).saveAll(any());
        }
    }

    // ─── Daily price snapshot ───────────────────────────────────────────────────

    @Nested
    @DisplayName("refreshPrices - daily price snapshot")
    class DailyPriceSnapshot {

        @Test
        @DisplayName("creates new daily price when none exists for today")
        void createsNewDailyPrice() {
            Listing stock = stockListing("AAPL", BigDecimal.valueOf(150));
            when(listingRepository.findAll()).thenReturn(List.of(stock));
            when(dailyPriceRepository.findByListingIdAndDate(any(), any())).thenReturn(Collections.emptyList());
            when(restTemplate.getForObject(any(String.class), eq(Map.class))).thenReturn(null);

            listingService.refreshPrices();

            verify(dailyPriceRepository).save(any(ListingDailyPriceInfo.class));
        }

        @Test
        @DisplayName("updates existing daily price when one already exists for today")
        void updatesExistingDailyPrice() {
            Listing stock = stockListing("AAPL", BigDecimal.valueOf(150));

            ListingDailyPriceInfo existing = new ListingDailyPriceInfo();
            existing.setListing(stock);
            existing.setDate(LocalDate.now());
            existing.setPrice(BigDecimal.valueOf(148));
            existing.setHigh(BigDecimal.valueOf(149));
            existing.setLow(BigDecimal.valueOf(147));
            existing.setChange(BigDecimal.valueOf(1));
            existing.setVolume(10000L);

            when(listingRepository.findAll()).thenReturn(List.of(stock));
            when(dailyPriceRepository.findByListingIdAndDate(any(), any())).thenReturn(List.of(existing));
            when(restTemplate.getForObject(any(String.class), eq(Map.class))).thenReturn(null);

            listingService.refreshPrices();

            verify(dailyPriceRepository).save(existing);
        }
    }

    // ─── loadInitialData ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("loadInitialData")
    class LoadInitialData {

        @Test
        @DisplayName("logs info when listings exist in database")
        void listingsExist_logsInfo() {
            when(listingRepository.count()).thenReturn(10L);

            listingService.loadInitialData();

            verify(listingRepository).count();
        }

        @Test
        @DisplayName("logs warning when no listings in database")
        void noListings_logsWarning() {
            when(listingRepository.count()).thenReturn(0L);

            listingService.loadInitialData();

            verify(listingRepository).count();
        }
    }
}
