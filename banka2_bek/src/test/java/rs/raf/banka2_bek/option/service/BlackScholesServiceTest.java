package rs.raf.banka2_bek.option.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for BlackScholesService covering:
 * - Call price calculation (standard case)
 * - Put price calculation (standard case)
 * - Expired options (T <= 0) return intrinsic value
 * - Zero volatility throws IllegalArgumentException
 * - Convenience methods with default risk-free rate
 * - Put-Call parity verification
 * - Edge cases: deep in-the-money, deep out-of-the-money
 * - normalCDF basic sanity checks
 */
@DisplayName("BlackScholesService")
class BlackScholesServiceTest {

    private final BlackScholesService service = new BlackScholesService();

    // ─── Call Price ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("calculateCallPrice")
    class CallPrice {

        @Test
        @DisplayName("standard ATM call price is positive")
        void standardAtmCall() {
            // S=100, K=100, T=1yr, r=5%, sigma=25%
            BigDecimal price = service.calculateCallPrice(100, 100, 1.0, 0.05, 0.25);

            assertThat(price.doubleValue()).isGreaterThan(0);
            // For ATM with these params, price should be roughly 12-14
            assertThat(price.doubleValue()).isBetween(10.0, 18.0);
        }

        @Test
        @DisplayName("deep ITM call has price close to S - K*e^(-rT)")
        void deepItmCall() {
            // S=200, K=100, T=0.5yr, r=5%, sigma=25%
            BigDecimal price = service.calculateCallPrice(200, 100, 0.5, 0.05, 0.25);

            // Should be close to 200 - 100*e^(-0.025) ~ 102.5
            assertThat(price.doubleValue()).isGreaterThan(95);
        }

        @Test
        @DisplayName("deep OTM call has price close to 0")
        void deepOtmCall() {
            // S=50, K=200, T=0.1yr, r=5%, sigma=25%
            BigDecimal price = service.calculateCallPrice(50, 200, 0.1, 0.05, 0.25);

            assertThat(price.doubleValue()).isLessThan(1);
        }

        @Test
        @DisplayName("higher volatility increases call price")
        void higherVolatilityIncreasesPrice() {
            BigDecimal lowVol = service.calculateCallPrice(100, 100, 1.0, 0.05, 0.10);
            BigDecimal highVol = service.calculateCallPrice(100, 100, 1.0, 0.05, 0.40);

            assertThat(highVol.doubleValue()).isGreaterThan(lowVol.doubleValue());
        }

        @Test
        @DisplayName("longer time to expiry increases call price")
        void longerTimeIncreasesPrice() {
            BigDecimal shortTime = service.calculateCallPrice(100, 100, 0.25, 0.05, 0.25);
            BigDecimal longTime = service.calculateCallPrice(100, 100, 2.0, 0.05, 0.25);

            assertThat(longTime.doubleValue()).isGreaterThan(shortTime.doubleValue());
        }
    }

    // ─── Put Price ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("calculatePutPrice")
    class PutPrice {

        @Test
        @DisplayName("standard ATM put price is positive")
        void standardAtmPut() {
            BigDecimal price = service.calculatePutPrice(100, 100, 1.0, 0.05, 0.25);

            assertThat(price.doubleValue()).isGreaterThan(0);
            assertThat(price.doubleValue()).isBetween(5.0, 15.0);
        }

        @Test
        @DisplayName("deep ITM put has price close to K*e^(-rT) - S")
        void deepItmPut() {
            // S=50, K=200, T=0.5yr
            BigDecimal price = service.calculatePutPrice(50, 200, 0.5, 0.05, 0.25);

            assertThat(price.doubleValue()).isGreaterThan(140);
        }

        @Test
        @DisplayName("deep OTM put has price close to 0")
        void deepOtmPut() {
            // S=200, K=50, T=0.1yr
            BigDecimal price = service.calculatePutPrice(200, 50, 0.1, 0.05, 0.25);

            assertThat(price.doubleValue()).isLessThan(1);
        }
    }

    // ─── Expired Options (T <= 0) ───────────────────────────────────────────────

    @Nested
    @DisplayName("Expired options (T <= 0)")
    class ExpiredOptions {

        @Test
        @DisplayName("expired CALL returns max(0, S-K) for ITM")
        void expiredCallItm() {
            BigDecimal price = service.calculateCallPrice(150, 100, 0, 0.05, 0.25);

            assertThat(price.doubleValue()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("expired CALL returns 0 for OTM")
        void expiredCallOtm() {
            BigDecimal price = service.calculateCallPrice(80, 100, 0, 0.05, 0.25);

            assertThat(price.doubleValue()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("expired PUT returns max(0, K-S) for ITM")
        void expiredPutItm() {
            BigDecimal price = service.calculatePutPrice(80, 100, 0, 0.05, 0.25);

            assertThat(price.doubleValue()).isEqualTo(20.0);
        }

        @Test
        @DisplayName("expired PUT returns 0 for OTM")
        void expiredPutOtm() {
            BigDecimal price = service.calculatePutPrice(150, 100, 0, 0.05, 0.25);

            assertThat(price.doubleValue()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("negative T is treated same as expired")
        void negativeTimeExpired() {
            BigDecimal price = service.calculateCallPrice(150, 100, -0.5, 0.05, 0.25);

            assertThat(price.doubleValue()).isEqualTo(50.0);
        }
    }

    // ─── Volatility Validation ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Volatility validation")
    class VolatilityValidation {

        @Test
        @DisplayName("zero volatility throws IllegalArgumentException for call")
        void zeroVolatilityCall() {
            assertThatThrownBy(() -> service.calculateCallPrice(100, 100, 1.0, 0.05, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Volatility");
        }

        @Test
        @DisplayName("negative volatility throws IllegalArgumentException for call")
        void negativeVolatilityCall() {
            assertThatThrownBy(() -> service.calculateCallPrice(100, 100, 1.0, 0.05, -0.25))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("zero volatility throws IllegalArgumentException for put")
        void zeroVolatilityPut() {
            assertThatThrownBy(() -> service.calculatePutPrice(100, 100, 1.0, 0.05, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("negative volatility throws IllegalArgumentException for put")
        void negativeVolatilityPut() {
            assertThatThrownBy(() -> service.calculatePutPrice(100, 100, 1.0, 0.05, -0.1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─── Convenience Methods (default risk-free rate) ───────────────────────────

    @Nested
    @DisplayName("Convenience methods with default risk-free rate")
    class ConvenienceMethods {

        @Test
        @DisplayName("call convenience method uses default r=0.05")
        void callDefaultRate() {
            BigDecimal explicit = service.calculateCallPrice(100, 100, 1.0, 0.05, 0.25);
            BigDecimal convenience = service.calculateCallPrice(100, 100, 1.0, 0.25);

            assertThat(convenience).isEqualByComparingTo(explicit);
        }

        @Test
        @DisplayName("put convenience method uses default r=0.05")
        void putDefaultRate() {
            BigDecimal explicit = service.calculatePutPrice(100, 100, 1.0, 0.05, 0.25);
            BigDecimal convenience = service.calculatePutPrice(100, 100, 1.0, 0.25);

            assertThat(convenience).isEqualByComparingTo(explicit);
        }
    }

    // ─── Put-Call Parity ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Put-Call parity verification")
    class PutCallParity {

        @Test
        @DisplayName("C - P approximately equals S - K*e^(-rT)")
        void putCallParity() {
            double S = 100, K = 100, T = 1.0, r = 0.05, sigma = 0.25;
            BigDecimal call = service.calculateCallPrice(S, K, T, r, sigma);
            BigDecimal put = service.calculatePutPrice(S, K, T, r, sigma);

            // C - P = S - K*e^(-rT) = 100 - 100*e^(-0.05) ~ 4.877
            double expected = S - K * Math.exp(-r * T);
            double actual = call.doubleValue() - put.doubleValue();

            assertThat(actual).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.01));
        }
    }

    // ─── Result scale ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Result precision")
    class ResultPrecision {

        @Test
        @DisplayName("call price is scaled to 4 decimal places")
        void callScale() {
            BigDecimal price = service.calculateCallPrice(100, 100, 1.0, 0.05, 0.25);

            assertThat(price.scale()).isEqualTo(4);
        }

        @Test
        @DisplayName("put price is scaled to 4 decimal places")
        void putScale() {
            BigDecimal price = service.calculatePutPrice(100, 100, 1.0, 0.05, 0.25);

            assertThat(price.scale()).isEqualTo(4);
        }
    }
}
