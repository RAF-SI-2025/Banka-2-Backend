package rs.raf.banka2_bek.card.model;

import jakarta.persistence.*;
import lombok.*;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.client.model.Client;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "cards",
        // P2.3 — DB-level constraint za max-card-per-account.
        // PARTIAL UNIQUE INDEX (account_id, client_id, card_slot) gde
        // status != 'DEACTIVATED'. Dva korisna scenarija:
        //   * Lični racun: vlasnik (1 client_id) moze imati max 2 kartice
        //     (slot 1 i slot 2). Treci insert sa slot=1 ili slot=2 baca SQL grom.
        //   * Poslovni racun: max 1 kartica po ovlascenom licu (client_id).
        //     Ovlasceno lice ce uvek imati slot=1 — drugi insert za istu osobu
        //     na isti business account (slot=1) ce udariti u uniqueness.
        // CHECK constraint card_slot BETWEEN 1 AND 2 sprecava arbitrarne brojeve.
        indexes = {
                @Index(name = "ix_cards_account", columnList = "account_id"),
                @Index(name = "ix_cards_client", columnList = "client_id")
        },
        check = @jakarta.persistence.CheckConstraint(
                name = "ck_card_slot_range",
                constraint = "card_slot BETWEEN 1 AND 2")
)
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

    /**
     * Slot pozicija u okviru (account, client) parova: 1 ili 2.
     * Service-level kod ({@code CardServiceImpl.checkCardLimit}) dodeljuje:
     *   * Lični racun: 1 ako nema kartice; 2 ako vec postoji slot 1.
     *   * Poslovni racun: uvek 1 (max 1 kartica po osobi).
     * Partial UNIQUE INDEX preko (account_id, client_id, card_slot) gde
     * status != 'DEACTIVATED' enforce-uje DB-level non-duplicate.
     */
    @Column(name = "card_slot", nullable = false)
    @org.hibernate.annotations.ColumnDefault("1")
    @Builder.Default
    private Integer cardSlot = 1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false, precision = 19, scale = 4)
    @org.hibernate.annotations.ColumnDefault("0")
    @Builder.Default
    private BigDecimal cardLimit = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @org.hibernate.annotations.ColumnDefault("'VISA'")
    @Builder.Default
    private CardType cardType = CardType.VISA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @org.hibernate.annotations.ColumnDefault("'ACTIVE'")
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
    /**
     * Cryptographically-secure RNG za generisanje brojeva kartica i CVV-a.
     * {@link java.util.Random} je predictable (linear congruential) — napadac koji
     * vidi nekoliko sukcesivnih izlaza moze da rekonstruise seed i predvidi sve
     * naredne brojeve, sto je neprihvatljivo za PCI-relevant podatke.
     * {@link java.security.SecureRandom} koristi OS entropy pool i ne moze se
     * reverse-engineer-ovati. Singleton inicijalizacija — instanciranje je skupo.
     */
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    public static String generateCardNumber(CardType cardType) {
        java.security.SecureRandom random = SECURE_RANDOM;

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
        return String.format("%03d", SECURE_RANDOM.nextInt(1000));
    }
}
