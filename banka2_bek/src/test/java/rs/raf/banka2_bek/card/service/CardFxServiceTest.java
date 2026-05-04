package rs.raf.banka2_bek.card.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.card.model.CardType;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.order.service.CurrencyConversionService.ConversionResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardFxService — multi-currency brand fee")
class CardFxServiceTest {

    @Mock
    private CurrencyConversionService currencyConversionService;

    @InjectMocks
    private CardFxService service;

    private void stubMidRate(BigDecimal amount, String from, String to,
                             BigDecimal mid, BigDecimal rate) {
        when(currencyConversionService.convertForPurchase(amount, from, to, false))
                .thenReturn(new ConversionResult(mid, BigDecimal.ZERO, rate, rate));
    }

    @Test
    @DisplayName("MASTERCARD: 2% banka + 0.5% MC naknada (Celina 2 §270-272)")
    void mastercard_appliesBankAndMcFees() {
        stubMidRate(new BigDecimal("100"), "EUR", "RSD",
                new BigDecimal("11700.0000"), new BigDecimal("117.0000"));

        CardFxService.CardFxBreakdown breakdown = service.computeMultiCurrencyFee(
                new BigDecimal("100"), "EUR", "RSD", CardType.MASTERCARD);

        assertThat(breakdown.midAmount()).isEqualByComparingTo("11700.0000");
        // 2% od 11700 = 234
        assertThat(breakdown.bankCommission()).isEqualByComparingTo("234.0000");
        // 0.5% od 11700 = 58.5
        assertThat(breakdown.networkFee()).isEqualByComparingTo("58.5000");
        // ukupno = 11700 + 234 + 58.5 = 11992.5
        assertThat(breakdown.totalCharge()).isEqualByComparingTo("11992.5000");
        assertThat(breakdown.totalFee()).isEqualByComparingTo("292.5000");
        assertThat(breakdown.cardType()).isEqualTo(CardType.MASTERCARD);
    }

    @Test
    @DisplayName("VISA: 2% banka + 0.5% Visa naknada")
    void visa_appliesBankAndVisaFees() {
        stubMidRate(new BigDecimal("50"), "USD", "RSD",
                new BigDecimal("5500.0000"), new BigDecimal("110.0000"));

        CardFxService.CardFxBreakdown breakdown = service.computeMultiCurrencyFee(
                new BigDecimal("50"), "USD", "RSD", CardType.VISA);

        assertThat(breakdown.bankCommission()).isEqualByComparingTo("110.0000");
        assertThat(breakdown.networkFee()).isEqualByComparingTo("27.5000");
        assertThat(breakdown.totalCharge()).isEqualByComparingTo("5637.5000");
    }

    @Test
    @DisplayName("AMERICAN_EXPRESS: 2% banka + 1% AMEX naknada (visa)")
    void amex_appliesHigherFee() {
        stubMidRate(new BigDecimal("100"), "USD", "RSD",
                new BigDecimal("11000.0000"), new BigDecimal("110.0000"));

        CardFxService.CardFxBreakdown breakdown = service.computeMultiCurrencyFee(
                new BigDecimal("100"), "USD", "RSD", CardType.AMERICAN_EXPRESS);

        // 2% = 220, 1% = 110, ukupno = 11000 + 330 = 11330
        assertThat(breakdown.bankCommission()).isEqualByComparingTo("220.0000");
        assertThat(breakdown.networkFee()).isEqualByComparingTo("110.0000");
        assertThat(breakdown.totalCharge()).isEqualByComparingTo("11330.0000");
    }

    @Test
    @DisplayName("DINACARD: samo 2% banka, nema network naknade")
    void dinacard_onlyBankCommission() {
        stubMidRate(new BigDecimal("100"), "EUR", "RSD",
                new BigDecimal("11700.0000"), new BigDecimal("117.0000"));

        CardFxService.CardFxBreakdown breakdown = service.computeMultiCurrencyFee(
                new BigDecimal("100"), "EUR", "RSD", CardType.DINACARD);

        assertThat(breakdown.bankCommission()).isEqualByComparingTo("234.0000");
        assertThat(breakdown.networkFee()).isEqualByComparingTo("0");
        // ukupno = 11700 + 234 = 11934
        assertThat(breakdown.totalCharge()).isEqualByComparingTo("11934.0000");
    }

    @Test
    @DisplayName("Same-currency placanje: i dalje se primenjuju fee jer multi-currency card moze biti debited drugom valutom")
    void sameCurrency_stillAppliesBankFee() {
        // Kada konverzija ne menja iznos, fee se i dalje primenjuje
        stubMidRate(new BigDecimal("100"), "RSD", "RSD",
                new BigDecimal("100"), BigDecimal.ONE);

        CardFxService.CardFxBreakdown breakdown = service.computeMultiCurrencyFee(
                new BigDecimal("100"), "RSD", "RSD", CardType.MASTERCARD);

        assertThat(breakdown.bankCommission()).isEqualByComparingTo("2.0000");
        assertThat(breakdown.networkFee()).isEqualByComparingTo("0.5000");
        assertThat(breakdown.totalCharge()).isEqualByComparingTo("102.5000");
    }

    @Test
    @DisplayName("Negativan iznos baca IllegalArgumentException")
    void negativeAmount_throws() {
        assertThatThrownBy(() -> service.computeMultiCurrencyFee(
                new BigDecimal("-1"), "EUR", "RSD", CardType.MASTERCARD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Zero iznos baca IllegalArgumentException")
    void zeroAmount_throws() {
        assertThatThrownBy(() -> service.computeMultiCurrencyFee(
                BigDecimal.ZERO, "EUR", "RSD", CardType.MASTERCARD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("networkFeeFor static helper vraca tacne brand stope")
    void networkFeeFor_returnsCorrectRates() {
        assertThat(CardFxService.networkFeeFor(CardType.MASTERCARD))
                .isEqualByComparingTo("0.005");
        assertThat(CardFxService.networkFeeFor(CardType.VISA))
                .isEqualByComparingTo("0.005");
        assertThat(CardFxService.networkFeeFor(CardType.AMERICAN_EXPRESS))
                .isEqualByComparingTo("0.010");
        assertThat(CardFxService.networkFeeFor(CardType.DINACARD))
                .isEqualByComparingTo("0");
        assertThat(CardFxService.networkFeeFor(null))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("totalFee() vraca zbir bank + network commissione")
    void totalFee_isBankPlusNetwork() {
        stubMidRate(new BigDecimal("100"), "EUR", "RSD",
                new BigDecimal("11700.0000"), new BigDecimal("117.0000"));

        CardFxService.CardFxBreakdown breakdown = service.computeMultiCurrencyFee(
                new BigDecimal("100"), "EUR", "RSD", CardType.MASTERCARD);

        assertThat(breakdown.totalFee()).isEqualByComparingTo(
                breakdown.bankCommission().add(breakdown.networkFee()));
    }
}
