package rs.raf.banka2_bek.assistant.dto.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Streaming response chunk sa /v1/chat/completions sa stream=true.
 * Server salje vise chunks (SSE-style: "data: {...}\n\n"), poslednji
 * je literalni "data: [DONE]\n\n".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiChatChunk(
        String id,
        String model,
        List<Choice> choices
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            int index,
            Delta delta,
            @JsonProperty("finish_reason") String finishReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Delta(
            String role,
            String content,
            @JsonProperty("tool_calls") List<DeltaToolCall> toolCalls
    ) {}

    /**
     * U streaming-u tool calls dolaze fragmentirano:
     *  - prva chunk-a ima id + type + function.name + function.arguments="{"
     *  - sledece chunk-e imaju samo function.arguments="koji se nadovezuju"
     *  - finalna chunk-a ima finish_reason="tool_calls"
     * Mi ih NE accumulate-ujemo u stream-u — koristimo non-stream za tool
     * iteracije, stream samo za finalni odgovor.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeltaToolCall(
            Integer index,
            String id,
            String type,
            DeltaFunction function
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record DeltaFunction(String name, String arguments) {}
    }
}
