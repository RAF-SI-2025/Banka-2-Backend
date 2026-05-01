package rs.raf.banka2_bek.assistant.dto.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible Chat Completions request body.
 * Stream je uvek false za tool-call iteracije, a true za finalni odgovor —
 * AssistantService kontrolise ovaj flag direktno.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiChatRequest(
        String model,
        List<OpenAiMessage> messages,
        List<Map<String, Object>> tools,
        @JsonProperty("tool_choice") Object toolChoice,
        Boolean stream,
        Double temperature,
        @JsonProperty("top_p") Double topP,
        @JsonProperty("top_k") Integer topK,
        @JsonProperty("max_tokens") Integer maxTokens
) {}
