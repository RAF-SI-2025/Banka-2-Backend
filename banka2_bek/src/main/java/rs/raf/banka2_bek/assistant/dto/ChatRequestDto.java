package rs.raf.banka2_bek.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequestDto {

    /** Postojeci conversation UUID, ili null za novu konverzaciju. */
    private UUID conversationUuid;

    @NotBlank
    @Size(max = 4000)
    private String message;

    private PageContextDto pageContext;

    /** Override za default settings. Null = koristi default. */
    private Boolean useTools;
    private Boolean detailedMode;

    /**
     * Phase 4 v3.5 — agentic mode toggle. Default null/false znaci sto smo
     * imali ranije (read-only chat). Kad je true, BE registruje WriteToolHandler-e
     * u dispatcher-u i prevodi tool_calls u AgentAction preview event-e.
     */
    private Boolean agenticMode;

    /**
     * Phase 5 multimodal — base64-encoded WAV (16kHz mono, max 30s) ili
     * slika koja se prilaze user poruci. Ollama Gemma 4 multimodal API
     * (issue ollama#15333) prima i audio i sliku kroz {@code images} polje
     * — encoder klase modela rasporeduju po MIME type-u. Null = obican
     * tekstualan chat.
     */
    private java.util.List<String> mediaBase64;
}
