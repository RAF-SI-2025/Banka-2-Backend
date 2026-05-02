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
 * wikipedia_summary(title, lang, sentences) -> {title, lang, summary, ...}
 *
 * Vraca prvih N recenica Wikipedia clanka. Ako pun naslov nije dostupan na
 * trazenom jeziku, sidecar pokusava engleski fallback. DisambiguationError
 * vraca {disambiguation_options: [...]} listu — model treba da ponovi sa
 * preciznim naslovom.
 */
@Component
@RequiredArgsConstructor
public class WikipediaSummaryHandler implements ToolHandler {

    private final WikipediaToolClient client;

    @Override
    public String name() {
        return "wikipedia_summary";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Vraca sazetak Wikipedia clanka po naslovu (prvih N " +
                        "recenica). Pozovi posle wikipedia_search da bi dobio tekst, " +
                        "ili direktno ako znas tacan naslov.")
                .param(new ToolDefinition.Param("title", "string",
                        "Tacan naslov Wikipedia clanka.", true, null, null))
                .param(new ToolDefinition.Param("lang", "string",
                        "Jezik clanka. 'sr' za srpski, 'en' za engleski.", false, "sr",
                        List.of("sr", "en")))
                .param(new ToolDefinition.Param("sentences", "integer",
                        "Broj recenica u sazetku (1-10).", false, 3, null))
                .build();
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, UserContext user) {
        String title = String.valueOf(args.getOrDefault("title", ""));
        String lang = String.valueOf(args.getOrDefault("lang", "sr"));
        int sentences = parseInt(args.get("sentences"), 3);
        if (title.isBlank()) {
            return Map.of("error", "title je obavezan");
        }
        return client.summary(title, lang, Math.min(Math.max(sentences, 1), 10));
    }

    private static int parseInt(Object raw, int fallback) {
        if (raw == null) return fallback;
        try { return Integer.parseInt(String.valueOf(raw)); }
        catch (NumberFormatException e) { return fallback; }
    }
}
