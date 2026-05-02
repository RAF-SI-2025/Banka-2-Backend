package rs.raf.banka2_bek.assistant.tool;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible function calling tool definition. Generise se iz Java koda
 * (ToolDefinition.builder()) — bez rucnog odrzavanja JSON schema fajlova.
 *
 * Primer:
 *   ToolDefinition.builder()
 *       .name("wikipedia_search")
 *       .description("Pretraga Wikipedia clanaka")
 *       .stringParam("query", "Tekst za pretragu", true)
 *       .stringParam("lang", "Jezik (sr ili en)", false)
 *       .build();
 */
@Getter
@Builder
public class ToolDefinition {

    private final String name;
    private final String description;

    @Singular
    private final List<Param> params;

    /** Pretvori u OpenAI tool spec mapu (ide direktno u request body kao JSON). */
    public Map<String, Object> toOpenAiSpec() {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new java.util.ArrayList<>();
        for (Param p : params) {
            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("type", p.type());
            spec.put("description", p.description());
            if (p.enumValues() != null) spec.put("enum", p.enumValues());
            if (p.defaultValue() != null) spec.put("default", p.defaultValue());
            properties.put(p.name(), spec);
            if (p.required()) required.add(p.name());
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", required);

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "function");
        root.put("function", function);
        return root;
    }

    public record Param(
            String name,
            String type,
            String description,
            boolean required,
            Object defaultValue,
            List<String> enumValues
    ) {}
}
