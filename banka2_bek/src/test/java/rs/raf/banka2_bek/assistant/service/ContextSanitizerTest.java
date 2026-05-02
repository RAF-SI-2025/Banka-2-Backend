package rs.raf.banka2_bek.assistant.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextSanitizerTest {

    private final ContextSanitizer sanitizer = new ContextSanitizer();

    @Test
    void masks18DigitAccountNumber() {
        String input = "Treba mi pomoc oko racuna 222000100000123456 — ne radi";
        String out = sanitizer.sanitize(input);
        assertThat(out).contains("222***********3456");
        assertThat(out).doesNotContain("222000100000123456");
    }

    @Test
    void masks16DigitCardNumber() {
        String input = "Kartica 5798123412345571 mi je blokirana";
        String out = sanitizer.sanitize(input);
        assertThat(out).contains("5798********5571");
        assertThat(out).doesNotContain("5798123412345571");
    }

    @Test
    void masks15DigitAmexCard() {
        String input = "Imam Amex 374512345678901, kako da je odblokiram?";
        String out = sanitizer.sanitize(input);
        assertThat(out).contains("3745*******8901");
    }

    @Test
    void redactsJwtTokens() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4ifQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        String out = sanitizer.sanitize("token=" + jwt + " probaj");
        assertThat(out).contains("[REDACTED");
        assertThat(out).doesNotContain(jwt);
    }

    @Test
    void redactsSecretAssignments() {
        String out = sanitizer.sanitize("Lozinka: password=tajna123 i otp=987654");
        assertThat(out).contains("[REDACTED_SECRET]");
        assertThat(out).doesNotContain("tajna123");
        assertThat(out).doesNotContain("987654");
    }

    @Test
    void leavesNormalTextIntact() {
        String input = "Sta je BELIBOR? Kako da kupim 10 MSFT akcija?";
        String out = sanitizer.sanitize(input);
        assertThat(out).isEqualTo(input);
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(sanitizer.sanitize(null)).isNull();
        assertThat(sanitizer.sanitize("")).isEqualTo("");
    }
}
