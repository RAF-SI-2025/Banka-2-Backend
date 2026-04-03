package rs.raf.banka2_bek.card.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CardTest {

    // ================================================================
    // isValidLuhn — known valid card numbers
    // ================================================================

    @ParameterizedTest
    @ValueSource(strings = {
            "4532015112830366",   // VISA
            "5425233430109903",   // Mastercard
            "374245455400126",    // American Express
            "6011514433546201",   // Discover
            "0000000000000000",   // All zeros (Luhn-valid: sum=0, 0%10==0)
            "79927398713"        // Luhn test vector
    })
    void isValidLuhn_knownValidNumbers_returnsTrue(String number) {
        assertTrue(Card.isValidLuhn(number), "Expected Luhn-valid: " + number);
    }

    // ================================================================
    // isValidLuhn — known invalid card numbers
    // ================================================================

    @ParameterizedTest
    @ValueSource(strings = {
            "4532015112830367",   // Last digit changed from valid 6 → 7
            "5425233430109904",   // Last digit changed from valid 3 → 4
            "1234567890123456",   // Random invalid
            "0000000000000001",   // Single digit off from all zeros
            "1111111111111111"    // All ones (not Luhn-valid)
    })
    void isValidLuhn_knownInvalidNumbers_returnsFalse(String number) {
        assertFalse(Card.isValidLuhn(number), "Expected Luhn-invalid: " + number);
    }

    // ================================================================
    // isValidLuhn — single digit
    // ================================================================

    @Test
    void isValidLuhn_singleDigitZero_returnsTrue() {
        assertTrue(Card.isValidLuhn("0"));
    }

    @Test
    void isValidLuhn_singleDigitNonZero_returnsFalse() {
        assertFalse(Card.isValidLuhn("5"));
    }

    // ================================================================
    // generateCardNumber(VISA) — prefix 422200, 16 digits, Luhn-valid
    // ================================================================

    @Test
    void generateCardNumber_visa_correctFormatAndLuhnValid() {
        for (int i = 0; i < 50; i++) {
            String card = Card.generateCardNumber(CardType.VISA);
            assertEquals(16, card.length(), "VISA should be 16 digits");
            assertTrue(card.startsWith("4"), "VISA should start with 4");
            assertTrue(card.startsWith("422200"), "VISA should start with 422200");
            assertTrue(Card.isValidLuhn(card), "Generated VISA should be Luhn-valid: " + card);
        }
    }

    // ================================================================
    // generateCardNumber(MASTERCARD) — prefix 51-55, 16 digits, Luhn-valid
    // ================================================================

    @Test
    void generateCardNumber_mastercard_correctFormatAndLuhnValid() {
        for (int i = 0; i < 50; i++) {
            String card = Card.generateCardNumber(CardType.MASTERCARD);
            assertEquals(16, card.length(), "Mastercard should be 16 digits");
            assertTrue(card.startsWith("5"), "Mastercard should start with 5");
            int secondDigit = Character.getNumericValue(card.charAt(1));
            assertTrue(secondDigit >= 1 && secondDigit <= 5,
                    "Mastercard second digit should be 1-5, got: " + secondDigit);
            assertTrue(Card.isValidLuhn(card), "Generated Mastercard should be Luhn-valid: " + card);
        }
    }

    // ================================================================
    // generateCardNumber(DINACARD) — prefix 9891, 16 digits, Luhn-valid
    // ================================================================

    @Test
    void generateCardNumber_dinacard_correctFormatAndLuhnValid() {
        for (int i = 0; i < 50; i++) {
            String card = Card.generateCardNumber(CardType.DINACARD);
            assertEquals(16, card.length(), "DinaCard should be 16 digits");
            assertTrue(card.startsWith("9891"), "DinaCard should start with 9891");
            assertTrue(Card.isValidLuhn(card), "Generated DinaCard should be Luhn-valid: " + card);
        }
    }

    // ================================================================
    // generateCardNumber(AMERICAN_EXPRESS) — prefix 34 or 37, 15 digits, Luhn-valid
    // ================================================================

    @Test
    void generateCardNumber_americanExpress_correctFormatAndLuhnValid() {
        boolean saw34 = false;
        boolean saw37 = false;

        for (int i = 0; i < 100; i++) {
            String card = Card.generateCardNumber(CardType.AMERICAN_EXPRESS);
            assertEquals(15, card.length(), "AmEx should be 15 digits");
            assertTrue(card.startsWith("34") || card.startsWith("37"),
                    "AmEx should start with 34 or 37, got: " + card.substring(0, 2));
            assertTrue(Card.isValidLuhn(card), "Generated AmEx should be Luhn-valid: " + card);

            if (card.startsWith("34")) saw34 = true;
            if (card.startsWith("37")) saw37 = true;
        }

        // With 100 iterations and 50/50 chance, probability of not seeing both is negligible
        assertTrue(saw34, "Expected at least one AmEx with prefix 34");
        assertTrue(saw37, "Expected at least one AmEx with prefix 37");
    }

    // ================================================================
    // generateCardNumber() default overload — should produce VISA
    // ================================================================

    @Test
    void generateCardNumber_defaultOverload_producesVisa() {
        String card = Card.generateCardNumber();
        assertEquals(16, card.length());
        assertTrue(card.startsWith("422200"));
        assertTrue(Card.isValidLuhn(card));
    }

    // ================================================================
    // generateCvv — 3 digits, zero-padded
    // ================================================================

    @Test
    void generateCvv_alwaysThreeDigits() {
        for (int i = 0; i < 100; i++) {
            String cvv = Card.generateCvv();
            assertEquals(3, cvv.length(), "CVV should be 3 characters");
            assertTrue(cvv.matches("\\d{3}"), "CVV should be 3 digits: " + cvv);
        }
    }
}
