package rs.raf.banka2_bek.card.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.card.model.CardType;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.order.service.CurrencyConversionService.ConversionResult;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Card multi-currency FX naknada (Celina 2 §269-272).
 *
 * Spec eksplicitno navodi MasterCard kao primer: kartice povezane sa
 * dinarskim racunom mogu da placaju u stranoj valuti — banka primenjuje
 * srednji kurs, dodaje proviziju (2%) i karticnu konverzionu naknadu
 * (Mastercard 0.5%). Konacan iznos se skida sa korisnikovog dinarskog
 * racuna.
 *
 * Razdvojeno od {@link CurrencyConversionService} (koji radi 1% generic
 * menjacku komisiju za order/transfer flow) jer card multi-currency ima
 * brand-specific naknadu koja zavisi od tipa kartice.
 */
@Service
@RequiredArgsConstructor
public class CardFxService {

    /** Bankarska provizija — uvek 2% u multi-currency card placanjima. */
    public static final BigDecimal BANK_COMMISSION = new BigDecimal("0.02");

    /** Brand-specific konverziona naknada. */
    public static final BigDecimal MASTERCARD_NETWORK_FEE = new BigDecimal("0.005");
    public static final BigDecimal VISA_NETWORK_FEE = new BigDecimal("0.005");
    public static final BigDecimal AMEX_NETWORK_FEE = new BigDecimal("0.010");
    /** DinaCard je domaca kartica — nema brand network naknadu. */
    public static final BigDecimal DINACARD_NETWORK_FEE = BigDecimal.ZERO;

    private static final int SCALE = 4;

    private final CurrencyConversionService currencyConversionService;

    /**
     * Vraca brand-specific network naknadu za karticu.
     * @return 0.005 za VISA i MASTERCARD, 0.010 za AMEX, 0 za DINACARD
     */
    public static BigDecimal networkFeeFor(CardType cardType) {
        if (cardType == null) return BigDecimal.ZERO;
        return switch (cardType) {
            case MASTERCARD -> MASTERCARD_NETWORK_FEE;
            case VISA -> VISA_NETWORK_FEE;
            case AMERICAN_EXPRESS -> AMEX_NETWORK_FEE;
            case DINACARD -> DINACARD_NETWORK_FEE;
        };
    }

    /**
     * Racuna multi-currency FX naknadu za card placanje.
     *
     * Spec: prvo se primenjuje srednji kurs banke, zatim se dodaje:
     * - provizija banke (2%)
     * - karticna konverziona naknada (brand-specific, npr. MC 0.5%)
     * Konacan iznos se skida sa korisnikovog dinarskog racuna.
     *
     * @param amountInTransactionCurrency  iznos koji merchant naplacuje (u stranoj valuti)
     * @param transactionCurrency          ISO kod valute transakcije (npr. "EUR")
     * @param cardCurrency                 ISO kod valute kartice/racuna (npr. "RSD")
     * @param cardType                     brand kartice
     * @return breakdown sa: midAmount, bankCommission, networkFee, totalCharge
     */
    public CardFxBreakdown computeMultiCurrencyFee(BigDecimal amountInTransactionCurrency,
                                                   String transactionCurrency,
                                                   String cardCurrency,
                                                   CardType cardType) {
        if (amountInTransactionCurrency == null || amountInTransactionCurrency.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (transactionCurrency == null || cardCurrency == null) {
            throw new IllegalArgumentException("Currencies must not be null");
        }

        // Mid-rate konverzija (bez bilo kakve provizije)
        ConversionResult mid = currencyConversionService.convertForPurchase(
                amountInTransactionCurrency, transactionCurrency, cardCurrency, false);
        BigDecimal midAmount = mid.amount();

        BigDecimal networkRate = networkFeeFor(cardType);
        BigDecimal bankCommission = midAmount.multiply(BANK_COMMISSION)
                .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal networkFee = midAmount.multiply(networkRate)
                .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal totalCharge = midAmount.add(bankCommission).add(networkFee)
                .setScale(SCALE, RoundingMode.HALF_UP);

        return new CardFxBreakdown(
                amountInTransactionCurrency,
                transactionCurrency.toUpperCase(),
                cardCurrency.toUpperCase(),
                cardType,
                midAmount,
                bankCommission,
                networkFee,
                totalCharge,
                mid.midRate());
    }

    /**
     * Detaljan breakdown FX naknade za multi-currency card placanje.
     *
     * @param transactionAmount  iznos koji merchant naplacuje (u transactionCurrency)
     * @param transactionCurrency  ISO kod valute transakcije
     * @param cardCurrency       ISO kod valute kartice
     * @param cardType           brand kartice
     * @param midAmount          iznos posle konverzije po srednjem kursu (u cardCurrency)
     * @param bankCommission     2% bankarska provizija (u cardCurrency)
     * @param networkFee         brand-specific naknada (npr. 0.5% MC, u cardCurrency)
     * @param totalCharge        konacan iznos sa korisnikovog racuna (cardCurrency)
     * @param midRate            primenjeni srednji kurs
     */
    public record CardFxBreakdown(
            BigDecimal transactionAmount,
            String transactionCurrency,
            String cardCurrency,
            CardType cardType,
            BigDecimal midAmount,
            BigDecimal bankCommission,
            BigDecimal networkFee,
            BigDecimal totalCharge,
            BigDecimal midRate) {

        public BigDecimal totalFee() {
            return bankCommission.add(networkFee);
        }
    }
}
