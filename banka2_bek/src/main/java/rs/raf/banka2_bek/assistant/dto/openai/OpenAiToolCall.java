package rs.raf.banka2_bek.assistant.dto.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OpenAI Chat Completions tool_calls entry.
 * <pre>
 *   {
 *     "id": "call_abc123",
 *     "type": "function",
 *     "function": {
 *       "name": "wikipedia_search",
 *       "arguments": "{\"query\":\"BELIBOR\",\"lang\":\"sr\"}"
 *     }
 *   }
 * </pre>
 *
 * NAPOMENA: {@code arguments} dolazi kao JSON STRING (ne objekat) — model ga
 * pravi token-po-token, lakse mu je da generise plain JSON string nego struct.
 * Mi ga deserijalizujemo u {@code Map<String,Object>} pre dispatcha.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)  // Ollama dodaje "index" field u response, ignorisemo
public record OpenAiToolCall(
        String id,
        String type,
        Function function
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Function(String name, String arguments) {}
}
