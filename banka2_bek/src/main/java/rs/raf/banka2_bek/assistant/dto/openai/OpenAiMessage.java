package rs.raf.banka2_bek.assistant.dto.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenAI Chat Completions API message format.
 * Koristi se i za Ollama (port 11434) i LM Studio (port 1234) jer oba
 * implementiraju OpenAI-compatible endpoint.
 *
 *  - role: "system" | "user" | "assistant" | "tool"
 *  - content: tekst poruke ({@code null} za asistent poruke koje samo pozivaju alate)
 *  - toolCalls: ne-null SAMO za role=assistant kad je model pozvao alat
 *  - toolCallId: ne-null SAMO za role=tool, mora odgovarati id-u iz prethodnog tool_call-a
 *  - name: ne koristimo (legacy field za function_call)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiMessage(
        String role,
        String content,
        @JsonProperty("tool_calls") List<OpenAiToolCall> toolCalls,
        @JsonProperty("tool_call_id") String toolCallId,
        String name,
        /**
         * Ollama Gemma 4 emituje thinking output u {@code reasoning} polje
         * umesto u {@code content} (OpenAI-compatible extension koju Ollama
         * doda za modelove sa thinking sposobnostima). Ako je content prazan
         * a reasoning postoji, koristi reasoning kao finalan odgovor.
         */
        String reasoning,
        /**
         * Phase 5 multimodal — base64-encoded images ILI audio (WAV).
         * Ollama Gemma 4 multimodal API (issue ollama#15333) trenutno
         * koristi {@code images} field i za sliku I za audio (audio se
         * encoduje preko ImageEncoder klase u modelu, neobicno ali radi).
         * Polje ide samo na user role poruke.
         */
        List<String> images
) {
    public static OpenAiMessage system(String content) {
        return new OpenAiMessage("system", content, null, null, null, null, null);
    }

    public static OpenAiMessage user(String content) {
        return new OpenAiMessage("user", content, null, null, null, null, null);
    }

    /**
     * Phase 5 multimodal user message — sa base64 audio/image attachment-om.
     * Gemma 4 model card: "Place image/audio content BEFORE text". Audio mora
     * biti WAV 16kHz mono, max 30s (300M audio encoder).
     */
    public static OpenAiMessage userWithMedia(String content, List<String> base64Media) {
        return new OpenAiMessage("user", content, null, null, null, null, base64Media);
    }

    public static OpenAiMessage assistant(String content) {
        return new OpenAiMessage("assistant", content, null, null, null, null, null);
    }

    public static OpenAiMessage assistantWithTools(String content, List<OpenAiToolCall> toolCalls) {
        return new OpenAiMessage("assistant", content, toolCalls, null, null, null, null);
    }

    public static OpenAiMessage tool(String toolCallId, String content) {
        return new OpenAiMessage("tool", content, null, toolCallId, null, null, null);
    }

    /**
     * Vraca content ako postoji, u suprotnom reasoning. Koristi se kao
     * fallback za Ollama Gemma 4 koja emituje thinking u reasoning polju.
     */
    public String effectiveContent() {
        if (content != null && !content.isBlank()) return content;
        if (reasoning != null && !reasoning.isBlank()) return reasoning;
        return "";
    }
}
