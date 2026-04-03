package rs.raf.banka2_bek.card.model;

import jakarta.persistence.*;
import lombok.*;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.client.model.Client;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 19)
    private String cardNumber;

    @Column(nullable = false, length = 30)
    private String cardName;

    @Column(nullable = false, length = 3)
    private String cvv;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal cardLimit = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CardType cardType = CardType.VISA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private CardStatus status = CardStatus.ACTIVE;

    @Column(nullable = false)
    private LocalDate createdAt;

    @Column(nullable = false)
    private LocalDate expirationDate;

    // --- Luhn algorithm ---

    public static boolean isValidLuhn(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = Character.getNumericValue(number.charAt(i));
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }

    /**
     * Generates a valid card number for the given card type.
     * VISA: 16 digits, prefix 4XXXXX
     * MASTERCARD: 16 digits, prefix 51-55
     * DINACARD: 16 digits, prefix 9891
     * AMERICAN_EXPRESS: 15 digits, prefix 34 or 37
     * The last digit is always the Luhn check digit.
     */
    public static String generateCardNumber(CardType cardType) {
        java.util.Random random = new java.util.Random();

        String prefix;
        int totalDigits;

        switch (cardType) {
            case MASTERCARD:
                // 51-55 range
                prefix = "5" + (random.nextInt(5) + 1);
                totalDigits = 16;
                break;
            case DINACARD:
                prefix = "9891";
                totalDigits = 16;
                break;
            case AMERICAN_EXPRESS:
                // 34 or 37
                prefix = random.nextBoolean() ? "34" : "37";
                totalDigits = 15;
                break;
            case VISA:
            default:
                prefix = "422200";
                totalDigits = 16;
                break;
        }

        StringBuilder sb = new StringBuilder(prefix);
        for (int i = prefix.length(); i < totalDigits - 1; i++) {
            sb.append(random.nextInt(10));
        }
        // Calculate Luhn check digit
        sb.append(calculateLuhnCheckDigit(sb.toString()));
        return sb.toString();
    }

    /**
     * Backward-compatible overload — defaults to VISA.
     */
    public static String generateCardNumber() {
        return generateCardNumber(CardType.VISA);
    }

    private static int calculateLuhnCheckDigit(String partial) {
        int sum = 0;
        boolean alternate = true;
        for (int i = partial.length() - 1; i >= 0; i--) {
            int n = Character.getNumericValue(partial.charAt(i));
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return (10 - (sum % 10)) % 10;
    }

    public static String generateCvv() {
        return String.format("%03d", new java.util.Random().nextInt(1000));
    }
}
