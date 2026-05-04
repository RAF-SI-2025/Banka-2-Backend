package rs.raf.banka2_bek.interbank.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.order.service.CurrencyConversionService.ConversionResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterbankFxService")
class InterbankFxServiceTest {

    @Mock
    private CurrencyConversionService currencyConversionService;

    @InjectMocks
    private InterbankFxService fxService;

    @Test
    @DisplayName("Same-currency klijent fee: vraca razliku kao commission, target = source - fee")
    void quoteOutbound_sameCurrencyWithFee_returnsCommission() {
        InterbankFxService.InterbankFxQuote quote = fxService.quoteOutboundPayment(
                new BigDecimal("100"), "RSD", "RSD", true);

        assertThat(quote.isCrossCurrency()).isFalse();
        assertThat(quote.commission()).isEqualByComparingTo("0.5000");
        assertThat(quote.targetAmount()).isEqualByComparingTo("99.5000");
        assertThat(quote.midRate()).isEqualByComparingTo("1");
        assertThat(quote.effectiveRate()).isEqualByComparingTo("1");
        verify(currencyConversionService, never()).convertForPurchase(
                any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Same-currency bez fee-a (banka settlement): commission = 0")
    void quoteBankSettlement_sameCurrency_zeroFee() {
        InterbankFxService.InterbankFxQuote quote = fxService.quoteBankSettlement(
                new BigDecimal("250"), "EUR", "EUR");

        assertThat(quote.isCrossCurrency()).isFalse();
        assertThat(quote.commission()).isEqualByComparingTo("0");
        assertThat(quote.targetAmount()).isEqualByComparingTo("250");
        verify(currencyConversionService, never()).convertForPurchase(
                any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Cross-currency klijent fee: target je mid-rate konverzija, commission iz wrap-a")
    void quoteOutbound_crossCurrencyWithFee_delegatesToConversionService() {
        when(currencyConversionService.convertForPurchase(
                new BigDecimal("100"), "USD", "RSD", true))
                .thenReturn(new ConversionResult(
                        new BigDecimal("11055.5500"),
                        new BigDecimal("55.5500"),
                        new BigDecimal("110.5555"),
                        new BigDecimal("110.0000")));

        InterbankFxService.InterbankFxQuote quote = fxService.quoteOutboundPayment(
                new BigDecimal("100"), "USD", "RSD", true);

        assertThat(quote.isCrossCurrency()).isTrue();
        assertThat(quote.sourceAmount()).isEqualByComparingTo("100");
        assertThat(quote.targetAmount()).isEqualByComparingTo("11000.0000");
        assertThat(quote.commission()).isEqualByComparingTo("55.5500");
        assertThat(quote.midRate()).isEqualByComparingTo("110.0000");
        assertThat(quote.effectiveRate()).isEqualByComparingTo("110.5555");
        assertThat(quote.sourceCurrency()).isEqualTo("USD");
        assertThat(quote.targetCurrency()).isEqualTo("RSD");
    }

    @Test
    @DisplayName("Cross-currency bez fee-a: target = mid-rate amount, commission = 0")
    void quoteBankSettlement_crossCurrency_noFee() {
        when(currencyConversionService.convertForPurchase(
                new BigDecimal("100"), "EUR", "RSD", false))
                .thenReturn(new ConversionResult(
                        new BigDecimal("11700.0000"),
                        BigDecimal.ZERO,
                        new BigDecimal("117.0000"),
                        new BigDecimal("117.0000")));

        InterbankFxService.InterbankFxQuote quote = fxService.quoteBankSettlement(
                new BigDecimal("100"), "EUR", "RSD");

        assertThat(quote.isCrossCurrency()).isTrue();
        assertThat(quote.targetAmount()).isEqualByComparingTo("11700.0000");
        assertThat(quote.commission()).isEqualByComparingTo("0");
        assertThat(quote.midRate()).isEqualByComparingTo("117.0000");
    }

    @Test
    @DisplayName("Negativan iznos baca IllegalArgumentException")
    void quoteOutbound_negativeAmount_throws() {
        assertThatThrownBy(() -> fxService.quoteOutboundPayment(
                new BigDecimal("-1"), "USD", "RSD", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("Zero iznos baca IllegalArgumentException")
    void quoteOutbound_zeroAmount_throws() {
        assertThatThrownBy(() -> fxService.quoteOutboundPayment(
                BigDecimal.ZERO, "USD", "RSD", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("Null source currency baca IllegalArgumentException")
    void quoteOutbound_nullSourceCurrency_throws() {
        assertThatThrownBy(() -> fxService.quoteOutboundPayment(
                new BigDecimal("100"), null, "RSD", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Currency code se vraca uppercase u quote-u")
    void quoteOutbound_returnsUppercaseCurrencyCodes() {
        InterbankFxService.InterbankFxQuote quote = fxService.quoteOutboundPayment(
                new BigDecimal("100"), "rsd", "rsd", false);

        assertThat(quote.sourceCurrency()).isEqualTo("RSD");
        assertThat(quote.targetCurrency()).isEqualTo("RSD");
    }

    // Mockito helpers (lokalni jer su any() iz org.mockito.ArgumentMatchers)
    private static <T> T any() { return org.mockito.ArgumentMatchers.any(); }
    private static boolean anyBoolean() { return org.mockito.ArgumentMatchers.anyBoolean(); }
}
