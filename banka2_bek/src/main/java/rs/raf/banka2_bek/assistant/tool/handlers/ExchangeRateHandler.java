package rs.raf.banka2_bek.assistant.tool.handlers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.ToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.exchange.dto.ExchangeRateDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * exchange_rate(from, to, amount?) -> {rate, converted?, ...}
 *
 * Vraca trenutni srednji kurs izmedju dve valute. Ako je `amount` zadat,
 * dodaje i konvertovan iznos (bez provizije — informativno za chat).
 */
@Component
@RequiredArgsConstructor
public class ExchangeRateHandler implements ToolHandler {

    private final ExchangeService exchangeService;
    private final AssistantProperties properties;

    @Override
    public boolean isEnabled() {
        return properties.getTools().getInternal().isExchangeRate();
    }

    @Override
    public String name() {
        return "exchange_rate";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Vraca dnevni srednji kurs izmedju dve valute (RSD, EUR, " +
                        "USD, CHF, GBP, JPY, CAD, AUD). Opcioni amount parametar " +
                        "vraca i konvertovan iznos (informativno, bez provizije). " +
                        "Pozovi za konverziju '100 EUR u RSD' itd.")
                .param(new ToolDefinition.Param("from", "string",
                        "Izvorna valuta (ISO 4217 kod, npr. 'EUR').", true, null, null))
                .param(new ToolDefinition.Param("to", "string",
                        "Ciljna valuta (ISO 4217 kod, npr. 'RSD').", true, null, null))
                .param(new ToolDefinition.Param("amount", "number",
                        "Opcioni iznos za konverziju.", false, null, null))
                .build();
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, UserContext user) {
        String from = String.valueOf(args.getOrDefault("from", "")).toUpperCase();
        String to = String.valueOf(args.getOrDefault("to", "")).toUpperCase();
        if (from.isBlank() || to.isBlank()) {
            return Map.of("error", "from i to su obavezni");
        }
        if (from.equals(to)) {
            return Map.of("from", from, "to", to, "rate", 1.0, "note", "Iste valute.");
        }

        List<ExchangeRateDto> rates = exchangeService.getAllRates();
        Double fromRate = findRate(rates, from);  // koliko jedinica X za 1 RSD
        Double toRate = findRate(rates, to);
        if (fromRate == null) {
            return Map.of("error", "Nepodrzana valuta: " + from);
        }
        if (toRate == null) {
            return Map.of("error", "Nepodrzana valuta: " + to);
        }
        // Konverzija A → B preko RSD-a: amountA / rateA = RSD; * rateB = amountB
        BigDecimal rateAtoB = BigDecimal.valueOf(toRate)
                .divide(BigDecimal.valueOf(fromRate), 6, RoundingMode.HALF_UP);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("from", from);
        result.put("to", to);
        result.put("rate", rateAtoB.toPlainString());
        result.put("source", "Banka 2 srednji kurs (Fixer API)");

        Object amountRaw = args.get("amount");
        if (amountRaw != null) {
            try {
                BigDecimal amount = new BigDecimal(String.valueOf(amountRaw));
                BigDecimal converted = amount.multiply(rateAtoB).setScale(2, RoundingMode.HALF_UP);
                result.put("amount", amount.toPlainString());
                result.put("converted", converted.toPlainString());
                result.put("note", "Bez provizije — pri stvarnim transakcijama klijenti placaju 1% FX maržu.");
            } catch (NumberFormatException e) {
                result.put("amountError", "amount nije validan broj: " + amountRaw);
            }
        }
        return result;
    }

    private static Double findRate(List<ExchangeRateDto> rates, String currency) {
        for (ExchangeRateDto r : rates) {
            if (currency.equalsIgnoreCase(r.getCurrency())) return r.getRate();
        }
        return null;
    }
}
