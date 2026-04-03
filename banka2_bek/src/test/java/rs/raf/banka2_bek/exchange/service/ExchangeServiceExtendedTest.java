package rs.raf.banka2_bek.exchange.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.exchange.dto.CalculateExchangeResponseDto;
import rs.raf.banka2_bek.exchange.dto.ExchangeRateDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Extended tests for ExchangeService - covers cache behavior, fallback, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeServiceExtendedTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ExchangeService exchangeService;

    private static final String EXPECTED_URL =
            "https://data.fixer.io/api/latest?access_key=test-key&symbols=RSD,EUR,CHF,USD,GBP,JPY,CAD,AUD";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(exchangeService, "apiKey", "test-key");
        ReflectionTestUtils.setField(exchangeService, "apiUrl", "https://data.fixer.io/api/latest");
        // Reset cache for each test
        ReflectionTestUtils.setField(exchangeService, "cachedRates", null);
        ReflectionTestUtils.setField(exchangeService, "cacheTimestamp", 0L);
    }

    private void mockRates() {
        Map<String, Object> rates = new HashMap<>();
        rates.put("RSD", 117.35);
        rates.put("EUR", 1.0);
        rates.put("USD", 1.15);
        rates.put("CHF", 0.91);
        rates.put("GBP", 0.87);
        rates.put("JPY", 183.02);
        rates.put("CAD", 1.58);
        rates.put("AUD", 1.65);

        Map<String, Object> body = new HashMap<>();
        body.put("rates", rates);

        when(restTemplate.getForEntity(EXPECTED_URL, Map.class))
                .thenReturn(ResponseEntity.ok(body));
    }

    // ===== Cache behavior =====

    @Nested
    @DisplayName("Cache behavior")
    class CacheBehavior {

        @Test
        @DisplayName("returns cached rates within TTL window")
        void returnsCachedWithinTtl() {
            mockRates();

            // First call - fetches from API
            List<ExchangeRateDto> first = exchangeService.getAllRates();
            assertThat(first).hasSize(8);

            // Second call - should use cache (no second API call)
            List<ExchangeRateDto> second = exchangeService.getAllRates();
            assertThat(second).hasSize(8);

            verify(restTemplate, times(1)).getForEntity(EXPECTED_URL, Map.class);
        }

        @Test
        @DisplayName("refreshes cache after TTL expires")
        void refreshesAfterTtl() {
            mockRates();

            // First call
            exchangeService.getAllRates();

            // Simulate expired cache by setting timestamp far in the past
            ReflectionTestUtils.setField(exchangeService, "cacheTimestamp",
                    System.currentTimeMillis() - 6 * 60 * 1000); // 6 min ago, TTL is 5 min

            // Second call should fetch again
            exchangeService.getAllRates();

            verify(restTemplate, times(2)).getForEntity(EXPECTED_URL, Map.class);
        }
    }

    // ===== Fallback rates =====

    @Nested
    @DisplayName("Fallback rates")
    class FallbackRates {

        @Test
        @DisplayName("returns fallback rates when API throws exception")
        void fallbackOnApiException() {
            when(restTemplate.getForEntity(EXPECTED_URL, Map.class))
                    .thenThrow(new RestClientException("API down"));

            List<ExchangeRateDto> result = exchangeService.getAllRates();

            assertThat(result).hasSize(8);
            // Verify fallback has RSD at 1.0
            ExchangeRateDto rsd = result.stream()
                    .filter(r -> r.getCurrency().equals("RSD"))
                    .findFirst().orElse(null);
            assertThat(rsd).isNotNull();
            assertThat(rsd.getRate()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("returns previously cached rates when API fails on refresh")
        void returnsPreviousCacheOnApiFailure() {
            mockRates();

            // First call succeeds
            List<ExchangeRateDto> cached = exchangeService.getAllRates();
            assertThat(cached).hasSize(8);

            // Expire cache
            ReflectionTestUtils.setField(exchangeService, "cacheTimestamp",
                    System.currentTimeMillis() - 6 * 60 * 1000);

            // API fails on next call
            when(restTemplate.getForEntity(EXPECTED_URL, Map.class))
                    .thenThrow(new RestClientException("API down"));

            List<ExchangeRateDto> result = exchangeService.getAllRates();

            // Should return the previously cached rates, not fallback
            assertThat(result).hasSize(8);
        }

        @Test
        @DisplayName("fallback EUR rate is approximately correct")
        void fallbackEurRate() {
            when(restTemplate.getForEntity(EXPECTED_URL, Map.class))
                    .thenThrow(new RestClientException("timeout"));

            List<ExchangeRateDto> result = exchangeService.getAllRates();

            ExchangeRateDto eur = result.stream()
                    .filter(r -> r.getCurrency().equals("EUR"))
                    .findFirst().orElse(null);
            assertThat(eur).isNotNull();
            // ~0.0085 (1 RSD ~ 0.0085 EUR)
            assertThat(eur.getRate()).isBetween(0.008, 0.009);
        }
    }

    // ===== calculate edge cases =====

    @Nested
    @DisplayName("calculate edge cases")
    class CalculateEdgeCases {

        @Test
        @DisplayName("calculate with zero amount returns zero")
        void zeroAmount() {
            CalculateExchangeResponseDto result = exchangeService.calculate(0.0, "RSD");
            assertThat(result.getConvertedAmount()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("calculate RSD to RSD returns same amount for large values")
        void largeAmountRsdToRsd() {
            CalculateExchangeResponseDto result = exchangeService.calculate(999999999.99, "RSD");
            assertThat(result.getConvertedAmount()).isEqualTo(999999999.99);
            assertThat(result.getExchangeRate()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("calculate applies 2% sell markup and 0.5% commission")
        void sellMarkupAndCommission() {
            mockRates();

            // For EUR: rsdToEur = 1/117.35 = 0.008522
            // sellRate = 0.008522 * 1.02 = 0.008692 (approx)
            // converted = amount * sellRate * 0.995
            CalculateExchangeResponseDto result = exchangeService.calculate(100000.0, "EUR");

            assertThat(result.getConvertedAmount()).isPositive();
            assertThat(result.getExchangeRate()).isPositive();
            assertThat(result.getFromCurrency()).isEqualTo("RSD");
            assertThat(result.getToCurrency()).isEqualTo("EUR");

            // Rough check: 100000 RSD ~ 850 EUR
            assertThat(result.getConvertedAmount()).isBetween(800.0, 900.0);
        }
    }

    // ===== calculateCross edge cases =====

    @Nested
    @DisplayName("calculateCross edge cases")
    class CalculateCrossEdgeCases {

        @Test
        @DisplayName("same non-RSD currency goes through double conversion via RSD")
        void sameCurrencyFromAndTo() {
            // calculateCross with same non-RSD currencies goes through RSD (double conversion)
            mockRates();

            CalculateExchangeResponseDto result = exchangeService.calculateCross(100.0, "EUR", "EUR");
            // Double conversion via RSD: result may differ from 100 due to sell markup
            assertThat(result.getConvertedAmount()).isPositive();
            assertThat(result.getFromCurrency()).isEqualTo("EUR");
            assertThat(result.getToCurrency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("USD to GBP goes through RSD")
        void usdToGbpViaRsd() {
            mockRates();

            CalculateExchangeResponseDto result = exchangeService.calculateCross(1000.0, "USD", "GBP");

            assertThat(result.getFromCurrency()).isEqualTo("USD");
            assertThat(result.getToCurrency()).isEqualTo("GBP");
            assertThat(result.getConvertedAmount()).isPositive();
            assertThat(result.getExchangeRate()).isPositive();
        }

        @Test
        @DisplayName("CHF to JPY goes through RSD")
        void chfToJpyViaRsd() {
            mockRates();

            CalculateExchangeResponseDto result = exchangeService.calculateCross(500.0, "CHF", "JPY");

            assertThat(result.getFromCurrency()).isEqualTo("CHF");
            assertThat(result.getToCurrency()).isEqualTo("JPY");
            assertThat(result.getConvertedAmount()).isPositive();
        }

        @Test
        @DisplayName("RSD to RSD via calculateCross returns same amount minus spread")
        void rsdToRsdViaCross() {
            CalculateExchangeResponseDto result = exchangeService.calculateCross(1000.0, "RSD", "RSD");

            assertThat(result.getConvertedAmount()).isEqualTo(1000.0);
        }

        @Test
        @DisplayName("small amount cross conversion does not produce negative")
        void smallAmountCross() {
            mockRates();

            CalculateExchangeResponseDto result = exchangeService.calculateCross(0.01, "EUR", "USD");

            assertThat(result.getConvertedAmount()).isGreaterThanOrEqualTo(0.0);
        }
    }

    // ===== getAllRates edge cases =====

    @Nested
    @DisplayName("getAllRates edge cases")
    class GetAllRatesEdgeCases {

        @Test
        @DisplayName("handles non-Number values in rates map gracefully")
        void nonNumberValues() {
            Map<String, Object> rates = new HashMap<>();
            rates.put("RSD", 117.35);
            rates.put("EUR", 1.0);
            rates.put("USD", "not-a-number"); // invalid
            rates.put("CHF", 0.91);
            rates.put("GBP", 0.87);
            rates.put("JPY", 183.02);
            rates.put("CAD", 1.58);
            rates.put("AUD", 1.65);

            Map<String, Object> body = new HashMap<>();
            body.put("rates", rates);

            when(restTemplate.getForEntity(EXPECTED_URL, Map.class))
                    .thenReturn(ResponseEntity.ok(body));

            List<ExchangeRateDto> result = exchangeService.getAllRates();

            // Should have 7 rates (USD skipped because not a number)
            assertThat(result).hasSize(7);
        }

        @Test
        @DisplayName("handles integer values in rates map")
        void integerValues() {
            Map<String, Object> rates = new HashMap<>();
            rates.put("RSD", 117); // integer, not double
            rates.put("EUR", 1);
            rates.put("USD", 1);
            rates.put("CHF", 1);
            rates.put("GBP", 1);
            rates.put("JPY", 183);
            rates.put("CAD", 2);
            rates.put("AUD", 2);

            Map<String, Object> body = new HashMap<>();
            body.put("rates", rates);

            when(restTemplate.getForEntity(EXPECTED_URL, Map.class))
                    .thenReturn(ResponseEntity.ok(body));

            List<ExchangeRateDto> result = exchangeService.getAllRates();

            assertThat(result).hasSize(8);
            ExchangeRateDto rsd = result.stream()
                    .filter(r -> r.getCurrency().equals("RSD"))
                    .findFirst().orElse(null);
            assertThat(rsd).isNotNull();
            assertThat(rsd.getRate()).isEqualTo(1.0);
        }
    }

    // ===== Cache TTL boundary =====

    @Nested
    @DisplayName("Cache TTL boundary tests")
    class CacheTtlBoundary {

        @Test
        @DisplayName("cache still valid at exactly 4min 59sec (just under TTL)")
        void cacheValidJustUnderTtl() {
            mockRates();

            exchangeService.getAllRates();

            // Set timestamp to 4 min 59 sec ago (just under 5 min TTL)
            ReflectionTestUtils.setField(exchangeService, "cacheTimestamp",
                    System.currentTimeMillis() - (5 * 60 * 1000 - 1000));

            exchangeService.getAllRates();

            // Should still use cache — only 1 API call
            verify(restTemplate, times(1)).getForEntity(EXPECTED_URL, Map.class);
        }

        @Test
        @DisplayName("cache expired at exactly 5 minutes")
        void cacheExpiredAtExactTtl() {
            mockRates();

            exchangeService.getAllRates();

            // Set timestamp to exactly 5 min ago
            ReflectionTestUtils.setField(exchangeService, "cacheTimestamp",
                    System.currentTimeMillis() - 5 * 60 * 1000);

            exchangeService.getAllRates();

            // Should fetch again — 2 API calls
            verify(restTemplate, times(2)).getForEntity(EXPECTED_URL, Map.class);
        }

        @Test
        @DisplayName("double-check inside fetchAndCacheRates prevents redundant fetch")
        void doubleCheckPreventsRedundantFetch() {
            mockRates();

            // First call populates cache
            exchangeService.getAllRates();

            // Cache is still fresh — getAllRates returns cached without entering fetchAndCacheRates
            List<ExchangeRateDto> second = exchangeService.getAllRates();
            assertThat(second).hasSize(8);

            verify(restTemplate, times(1)).getForEntity(EXPECTED_URL, Map.class);
        }
    }

    // ===== API failure fallback — additional scenarios =====

    @Nested
    @DisplayName("API failure fallback - additional scenarios")
    class FallbackAdditional {

        @Test
        @DisplayName("null body returns fallback rates when no cache exists")
        void nullBodyNoCache() {
            when(restTemplate.getForEntity(EXPECTED_URL, Map.class))
                    .thenReturn(ResponseEntity.ok(null));

            List<ExchangeRateDto> result = exchangeService.getAllRates();

            assertThat(result).hasSize(8);
            // Verify these are fallback rates (EUR ~0.008547)
            ExchangeRateDto eur = result.stream()
                    .filter(r -> r.getCurrency().equals("EUR"))
                    .findFirst().orElse(null);
            assertThat(eur).isNotNull();
            assertThat(eur.getRate()).isEqualTo(0.008547);
        }

        @Test
        @DisplayName("body with null rates key returns fallback when no cache exists")
        void nullRatesKeyNoCache() {
            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            // no "rates" key

            when(restTemplate.getForEntity(EXPECTED_URL, Map.class))
                    .thenReturn(ResponseEntity.ok(body));

            List<ExchangeRateDto> result = exchangeService.getAllRates();

            assertThat(result).hasSize(8);
            // Verify fallback USD rate
            ExchangeRateDto usd = result.stream()
                    .filter(r -> r.getCurrency().equals("USD"))
                    .findFirst().orElse(null);
            assertThat(usd).isNotNull();
            assertThat(usd.getRate()).isEqualTo(0.009090);
        }

        @Test
        @DisplayName("null body returns previously cached rates when cache exists")
        void nullBodyWithExistingCache() {
            mockRates();

            // Populate cache
            List<ExchangeRateDto> cached = exchangeService.getAllRates();
            assertThat(cached).hasSize(8);

            // Expire cache
            ReflectionTestUtils.setField(exchangeService, "cacheTimestamp",
                    System.currentTimeMillis() - 6 * 60 * 1000);

            // API returns null body
            when(restTemplate.getForEntity(EXPECTED_URL, Map.class))
                    .thenReturn(ResponseEntity.ok(null));

            List<ExchangeRateDto> result = exchangeService.getAllRates();

            // Should return the previously cached rates
            assertThat(result).hasSize(8);
        }

        @Test
        @DisplayName("fallback rates contain all 8 currencies")
        void fallbackContainsAllCurrencies() {
            when(restTemplate.getForEntity(EXPECTED_URL, Map.class))
                    .thenThrow(new RestClientException("timeout"));

            List<ExchangeRateDto> result = exchangeService.getAllRates();

            assertThat(result).hasSize(8);
            List<String> currencies = result.stream()
                    .map(ExchangeRateDto::getCurrency)
                    .toList();
            assertThat(currencies).containsExactlyInAnyOrder(
                    "RSD", "EUR", "CHF", "USD", "GBP", "JPY", "CAD", "AUD");
        }
    }

    // ===== Currency conversion — exact math verification =====

    @Nested
    @DisplayName("Currency conversion - exact math")
    class CurrencyConversionMath {

        @Test
        @DisplayName("RSD to EUR: verify 2% sell markup applied to rate")
        void rsdToEurSellMarkup() {
            mockRates();

            CalculateExchangeResponseDto result = exchangeService.calculate(100000.0, "EUR");

            // rsdToEur = 1 / 117.35
            // sellRate = rsdToEur * 1.02 (2% markup)
            double rsdToEur = 1.0 / 117.35;
            double expectedSellRate = Math.round(rsdToEur * 1.02 * 1_000_000.0) / 1_000_000.0;

            assertThat(result.getExchangeRate()).isEqualTo(expectedSellRate);
        }

        @Test
        @DisplayName("RSD to EUR: verify 0.5% commission deducted from converted amount")
        void rsdToEurCommission() {
            mockRates();

            CalculateExchangeResponseDto result = exchangeService.calculate(100000.0, "EUR");

            double rsdToEur = 1.0 / 117.35;
            double sellRate = Math.round(rsdToEur * 1.02 * 1_000_000.0) / 1_000_000.0;
            double expectedAmount = Math.round((100000.0 * sellRate) * (1 - 0.005) * 10000.0) / 10000.0;

            assertThat(result.getConvertedAmount()).isEqualTo(expectedAmount);
        }

        @Test
        @DisplayName("RSD to USD conversion with exact sell rate and commission")
        void rsdToUsdExact() {
            mockRates();

            CalculateExchangeResponseDto result = exchangeService.calculate(50000.0, "USD");

            // USD rate from API: eurToUsd=1.15, eurToRsd=117.35
            // rsdToUsd = 1.15 / 117.35
            double rsdToUsd = 1.15 / 117.35;
            double sellRate = Math.round(rsdToUsd * 1.02 * 1_000_000.0) / 1_000_000.0;
            double expectedAmount = Math.round((50000.0 * sellRate) * 0.995 * 10000.0) / 10000.0;

            assertThat(result.getExchangeRate()).isEqualTo(sellRate);
            assertThat(result.getConvertedAmount()).isEqualTo(expectedAmount);
            assertThat(result.getFromCurrency()).isEqualTo("RSD");
            assertThat(result.getToCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("RSD to RSD returns 1:1 with no markup or commission")
        void rsdToRsdNoMarkup() {
            CalculateExchangeResponseDto result = exchangeService.calculate(12345.67, "RSD");

            assertThat(result.getConvertedAmount()).isEqualTo(12345.67);
            assertThat(result.getExchangeRate()).isEqualTo(1.0);
            assertThat(result.getFromCurrency()).isEqualTo("RSD");
            assertThat(result.getToCurrency()).isEqualTo("RSD");
        }

        @Test
        @DisplayName("EUR to USD cross conversion via RSD")
        void eurToUsdCross() {
            mockRates();

            CalculateExchangeResponseDto result = exchangeService.calculateCross(100.0, "EUR", "USD");

            assertThat(result.getFromCurrency()).isEqualTo("EUR");
            assertThat(result.getToCurrency()).isEqualTo("USD");
            assertThat(result.getConvertedAmount()).isPositive();
            // Cross rate should be derived from the two-step conversion
            assertThat(result.getExchangeRate()).isPositive();
        }

        @Test
        @DisplayName("case-insensitive currency code in calculate")
        void caseInsensitiveCurrency() {
            mockRates();

            CalculateExchangeResponseDto lower = exchangeService.calculate(1000.0, "eur");
            CalculateExchangeResponseDto upper = exchangeService.calculate(1000.0, "EUR");

            assertThat(lower.getConvertedAmount()).isEqualTo(upper.getConvertedAmount());
            assertThat(lower.getToCurrency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("case-insensitive RSD in calculate returns identity")
        void caseInsensitiveRsd() {
            CalculateExchangeResponseDto result = exchangeService.calculate(500.0, "rsd");

            assertThat(result.getConvertedAmount()).isEqualTo(500.0);
            assertThat(result.getExchangeRate()).isEqualTo(1.0);
        }
    }

    // ===== Unsupported currency =====

    @Nested
    @DisplayName("Unsupported currency handling")
    class UnsupportedCurrency {

        @Test
        @DisplayName("calculate throws for unsupported currency")
        void calculateUnsupported() {
            mockRates();

            assertThatThrownBy(() -> exchangeService.calculate(100.0, "BTC"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Currency not supported: BTC");
        }

        @Test
        @DisplayName("calculateCross throws for unsupported fromCurrency")
        void crossUnsupportedFrom() {
            mockRates();

            assertThatThrownBy(() -> exchangeService.calculateCross(100.0, "BTC", "EUR"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Currency not supported: BTC");
        }

        @Test
        @DisplayName("calculateCross throws for unsupported toCurrency")
        void crossUnsupportedTo() {
            mockRates();

            assertThatThrownBy(() -> exchangeService.calculateCross(100.0, "EUR", "BTC"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Currency not supported: BTC");
        }
    }

    // ===== RSD rate edge cases =====

    @Nested
    @DisplayName("RSD rate edge cases")
    class RsdRateEdgeCases {

        @Test
        @DisplayName("throws when RSD rate is zero")
        void throwsWhenRsdRateIsZero() {
            Map<String, Object> rates = new HashMap<>();
            rates.put("RSD", 0.0);
            rates.put("EUR", 1.0);

            Map<String, Object> body = new HashMap<>();
            body.put("rates", rates);

            when(restTemplate.getForEntity(EXPECTED_URL, Map.class))
                    .thenReturn(ResponseEntity.ok(body));

            assertThatThrownBy(() -> exchangeService.getAllRates())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("RSD rate not found.");
        }

        @Test
        @DisplayName("throws when RSD rate is missing from rates map")
        void throwsWhenRsdRateMissing() {
            Map<String, Object> rates = new HashMap<>();
            rates.put("EUR", 1.0);
            rates.put("USD", 1.15);

            Map<String, Object> body = new HashMap<>();
            body.put("rates", rates);

            when(restTemplate.getForEntity(EXPECTED_URL, Map.class))
                    .thenReturn(ResponseEntity.ok(body));

            assertThatThrownBy(() -> exchangeService.getAllRates())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("RSD rate not found.");
        }

        @Test
        @DisplayName("throws when RSD rate is a non-number string")
        void throwsWhenRsdRateIsString() {
            Map<String, Object> rates = new HashMap<>();
            rates.put("RSD", "invalid");
            rates.put("EUR", 1.0);

            Map<String, Object> body = new HashMap<>();
            body.put("rates", rates);

            when(restTemplate.getForEntity(EXPECTED_URL, Map.class))
                    .thenReturn(ResponseEntity.ok(body));

            assertThatThrownBy(() -> exchangeService.getAllRates())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("RSD rate not found.");
        }
    }

    // ===== calculateCross — from non-RSD to RSD =====

    @Nested
    @DisplayName("calculateCross - non-RSD to RSD")
    class CrossToRsd {

        @Test
        @DisplayName("EUR to RSD returns converted amount with sell markup and commission")
        void eurToRsd() {
            mockRates();

            CalculateExchangeResponseDto result = exchangeService.calculateCross(100.0, "EUR", "RSD");

            assertThat(result.getFromCurrency()).isEqualTo("EUR");
            assertThat(result.getToCurrency()).isEqualTo("RSD");
            assertThat(result.getConvertedAmount()).isPositive();
            // 100 EUR ~ 11735 RSD minus markups
            assertThat(result.getConvertedAmount()).isBetween(10000.0, 13000.0);
        }

        @Test
        @DisplayName("GBP to RSD goes through convertToRsd path")
        void gbpToRsd() {
            mockRates();

            CalculateExchangeResponseDto result = exchangeService.calculateCross(50.0, "GBP", "RSD");

            assertThat(result.getFromCurrency()).isEqualTo("GBP");
            assertThat(result.getToCurrency()).isEqualTo("RSD");
            assertThat(result.getConvertedAmount()).isPositive();
        }
    }

    // ===== Empty rates map =====

    @Nested
    @DisplayName("Empty rates map")
    class EmptyRatesMap {

        @Test
        @DisplayName("empty rates map causes RSD rate not found exception")
        void emptyRatesMap() {
            Map<String, Object> body = new HashMap<>();
            body.put("rates", new HashMap<>());

            when(restTemplate.getForEntity(EXPECTED_URL, Map.class))
                    .thenReturn(ResponseEntity.ok(body));

            assertThatThrownBy(() -> exchangeService.getAllRates())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("RSD rate not found.");
        }
    }
}
