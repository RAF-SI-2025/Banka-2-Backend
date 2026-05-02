package rs.raf.banka2_bek.assistant.tool.handlers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.ToolHandler;
import rs.raf.banka2_bek.assistant.tool.client.WikipediaToolClient;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.util.List;
import java.util.Map;

/**
 * wikipedia_search(query, lang, limit) -> {results: [naslovi clanaka]}
 *
 * Tipicno se zove pre wikipedia_summary kad model ne zna tacan naslov clanka.
 */
@Component
@RequiredArgsConstructor
public class WikipediaSearchHandler implements ToolHandler {

    private final WikipediaToolClient client;

    @Override
    public String name() {
        return "wikipedia_search";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Pretraga Wikipedia clanaka po keywordu. Vraca listu " +
                        "naslova clanaka koji su najrelevantniji.")
                .param(new ToolDefinition.Param("query", "string",
                        "Tekst za pretragu (npr. 'BELIBOR', 'Apple Inc')", true, null, null))
                .param(new ToolDefinition.Param("lang", "string",
                        "Jezik clanka. 'sr' za srpski, 'en' za engleski.", false, "sr",
                        List.of("sr", "en")))
                .param(new ToolDefinition.Param("limit", "integer",
                        "Maksimalan broj rezultata (1-10).", false, 5, null))
                .build();
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, UserContext user) {
        String query = String.valueOf(args.getOrDefault("query", ""));
        String lang = String.valueOf(args.getOrDefault("lang", "sr"));
        int limit = parseInt(args.get("limit"), 5);
        if (query.isBlank()) {
            return Map.of("error", "query je obavezan");
        }
        return client.search(query, lang, Math.min(Math.max(limit, 1), 10));
    }

    private static int parseInt(Object raw, int fallback) {
        if (raw == null) return fallback;
        try { return Integer.parseInt(String.valueOf(raw)); }
        catch (NumberFormatException e) { return fallback; }
    }
}
