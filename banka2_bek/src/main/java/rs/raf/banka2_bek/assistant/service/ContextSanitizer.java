package rs.raf.banka2_bek.assistant.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Defensive scrubbing prepre nego sto bilo sta ode ka LLM provider-u.
 *
 * Cak i kad je provider lokalan (Ollama/LM Studio), pravimo audit log ka disku
 * i mogli bi smo migrirati na cloud kasnije — pa svaki sloj defensive coding.
 *
 * Pravila iz plana §5:
 * <ol>
 *   <li>Maskiraj 18-cifren broj racuna (sacuvaj 3 + 4)</li>
 *   <li>Maskiraj 16-cifren broj kartice (Amex 15 ide takodje)</li>
 *   <li>Ukloni JWT-like tokene</li>
 *   <li>Ukloni recenice koje imaju password=, cvv=, otp=, secret=, apikey=</li>
 * </ol>
 */
@Component
public class ContextSanitizer {

    private static final Pattern ACCOUNT_18 = Pattern.compile("\\b(\\d{3})\\d{11}(\\d{4})\\b");
    private static final Pattern CARD_16 = Pattern.compile("\\b(\\d{4})\\d{8}(\\d{4})\\b");
    private static final Pattern CARD_15 = Pattern.compile("\\b(\\d{4})\\d{7}(\\d{4})\\b");  // Amex
    private static final Pattern JWT = Pattern.compile(
            "[A-Za-z0-9_\\-]{20,}\\.[A-Za-z0-9_\\-]{20,}\\.[A-Za-z0-9_\\-]{10,}");
    private static final Pattern SECRET_TOKEN = Pattern.compile(
            "(?i)(password|pass|cvv|otp|secret|apikey|api_key|token|bearer)\\s*[:=]\\s*\\S+");

    public String sanitize(String input) {
        if (input == null || input.isEmpty()) return input;
        String out = input;
        out = ACCOUNT_18.matcher(out).replaceAll("$1***********$2");
        out = CARD_16.matcher(out).replaceAll("$1********$2");
        out = CARD_15.matcher(out).replaceAll("$1*******$2");
        out = JWT.matcher(out).replaceAll("[REDACTED_TOKEN]");
        out = SECRET_TOKEN.matcher(out).replaceAll("[REDACTED_SECRET]");
        return out;
    }
}
