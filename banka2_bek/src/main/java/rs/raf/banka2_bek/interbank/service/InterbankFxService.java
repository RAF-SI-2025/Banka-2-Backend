package rs.raf.banka2_bek.interbank.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.order.service.CurrencyConversionService.ConversionResult;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * §2.8.7 + Celina 5 §40-50: cross-currency FX quote za inter-bank settlement.
 * Wrapper oko {@link CurrencyConversionService} koji odvaja inter-bank FX
 * obracun od order/payment FX (gde provizija ide klijentu vs banci).
 *
 * Koristi se na strani originator banke pre formiranja {@code Transaction} —
 * povratne vrednosti idu u balansirane double-entry postings.
 *
 * Spec Celina 5 §44-50 (stara): Banka B salje "Ready" sa kursom + provizijom.
 * Profesorov protokol: origin banka radi konverziju pre slanja, postings su
 * balansirani po asset-u (svaka strana u svojoj valuti).
 */
@Service
@RequiredArgsConstructor
public class InterbankFxService {

    /**
     * Inter-bank settlement provizija (banka A naplacuje klijenta za
     * cross-currency outbound). Razlikujemo od menjacnicke 1% jer ova ide
     * direktno u inter-bank revenue racun banke (a ne u FX revenue).
     */
    private static final BigDecimal INTERBANK_SETTLEMENT_FEE = new BigDecimal("0.005");

    private final CurrencyConversionService currencyConversionService;

    /**
     * Quote za outbound inter-bank payment.
     *
     * @param amountSourceCurrency  iznos koji klijent posalje, u source valuti
     * @param sourceCurrency        valuta posiljaoca
     * @param targetCurrency        valuta primaoca
     * @param chargeClientFee       true ako klijent placa proviziju (false za
     *                              banka-banka settlement)
     */
    public InterbankFxQuote quoteOutboundPayment(BigDecimal amountSourceCurrency,
                                                 String sourceCurrency,
                                                 String targetCurrency,
                                                 boolean chargeClientFee) {
        if (amountSourceCurrency == null || amountSourceCurrency.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (sourceCurrency == null || targetCurrency == null) {
            throw new IllegalArgumentException("Source/target currency must not be null");
        }

        // Same-currency: nema FX, ali jos uvek moze imati inter-bank fee.
        if (sourceCurrency.equalsIgnoreCase(targetCurrency)) {
            BigDecimal fee = chargeClientFee
                    ? amountSourceCurrency.multiply(INTERBANK_SETTLEMENT_FEE)
                            .setScale(4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return new InterbankFxQuote(
                    amountSourceCurrency,
                    amountSourceCurrency.subtract(fee).setScale(4, RoundingMode.HALF_UP),
                    fee,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    sourceCurrency.toUpperCase(),
                    targetCurrency.toUpperCase());
        }

        ConversionResult result = currencyConversionService.convertForPurchase(
                amountSourceCurrency, sourceCurrency, targetCurrency, chargeClientFee);

        // Ako je naplaceno klijentu, nominalni iznos primalca je mid-rate
        // konverzija; ConversionResult.amount() vec ukljucuje sve fee komponente.
        BigDecimal targetNominal = chargeClientFee
                ? result.amount().subtract(result.commission())
                        .setScale(4, RoundingMode.HALF_UP)
                : result.amount();

        return new InterbankFxQuote(
                amountSourceCurrency,
                targetNominal,
                result.commission(),
                result.midRate(),
                result.effectiveRate(),
                sourceCurrency.toUpperCase(),
                targetCurrency.toUpperCase());
    }

    /**
     * Quote za bank-to-bank settlement (banka A → banka B), bez provizije
     * klijentu. Koristi se u Profit Banke flow-ima i internim transferima
     * izmedju bankinih racuna.
     */
    public InterbankFxQuote quoteBankSettlement(BigDecimal amountSourceCurrency,
                                                String sourceCurrency,
                                                String targetCurrency) {
        return quoteOutboundPayment(amountSourceCurrency, sourceCurrency,
                targetCurrency, false);
    }

    /**
     * §2.6 + Celina 5: rezultat FX kvotacije za inter-bank settlement.
     *
     * @param sourceAmount         koliko sa posiljaocevog racuna ide ukupno
     *                             (ukljucuje commission ako je chargeClientFee=true)
     * @param targetAmount         koliko stize na primaocev racun (uvek bez fee-a)
     * @param commission           inter-bank settlement fee (u target valuti)
     * @param midRate              srednji kurs source→target
     * @param effectiveRate        stvarni primenjeni kurs (mid * (1 + fee))
     * @param sourceCurrency       ISO kod posiljaoceve valute
     * @param targetCurrency       ISO kod primaoceve valute
     */
    public record InterbankFxQuote(
            BigDecimal sourceAmount,
            BigDecimal targetAmount,
            BigDecimal commission,
            BigDecimal midRate,
            BigDecimal effectiveRate,
            String sourceCurrency,
            String targetCurrency) {

        public boolean isCrossCurrency() {
            return !sourceCurrency.equalsIgnoreCase(targetCurrency);
        }
    }
}
