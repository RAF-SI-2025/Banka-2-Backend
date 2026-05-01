package rs.raf.banka2_bek.assistant.tool.handlers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.ToolHandler;
import rs.raf.banka2_bek.assistant.tool.client.RagToolClient;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.util.Map;

/**
 * rag_search_spec(query, top_k) -> {results: [{text, source, celina, score}, ...]}
 *
 * Semanticka pretraga nad Banka 2 spec dokumentima (Celina 1-5 + Marzni +
 * Opcije + E2E Scenario). Sluzi za "kako" pitanja (kako kreiram fond, kako
 * radi OTC, kako se racuna porez itd) gde model treba precizan tekst spec-a.
 */
@Component
@RequiredArgsConstructor
public class RagSearchHandler implements ToolHandler {

    private final RagToolClient client;

    @Override
    public String name() {
        return "rag_search_spec";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Semanticka pretraga Banka 2 spec dokumentima " +
                        "(Celina 1-5 + Marzni racuni + Opcije + E2E Scenario). " +
                        "Pozovi za 'kako' pitanja gde detalji aplikacije nisu u " +
                        "core pojmovniku (npr. 'kako se racuna marza banke', " +
                        "'koja polja idu u zahtev za kredit', 'koje su faze SAGA " +
                        "izvrsenja'). Vraca paragrafe iz spec-a sa atribucijom.")
                .param(new ToolDefinition.Param("query", "string",
                        "Pitanje na srpskom (npr. 'kako kreiram fond', 'sta je " +
                        "limit aktuara').", true, null, null))
                .param(new ToolDefinition.Param("top_k", "integer",
                        "Broj paragrafa za vratiti (1-10).", false, 5, null))
                .build();
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, UserContext user) {
        String query = String.valueOf(args.getOrDefault("query", ""));
        int topK = parseInt(args.get("top_k"), 5);
        if (query.isBlank()) {
            return Map.of("error", "query je obavezan");
        }
        return client.search(query, Math.min(Math.max(topK, 1), 10));
    }

    private static int parseInt(Object raw, int fallback) {
        if (raw == null) return fallback;
        try { return Integer.parseInt(String.valueOf(raw)); }
        catch (NumberFormatException e) { return fallback; }
    }
}
