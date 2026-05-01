package rs.raf.banka2_bek.assistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import rs.raf.banka2_bek.assistant.dto.ChatRequestDto;
import rs.raf.banka2_bek.assistant.dto.ConversationListItemDto;
import rs.raf.banka2_bek.assistant.dto.HealthDto;
import rs.raf.banka2_bek.assistant.dto.MessageDto;
import rs.raf.banka2_bek.assistant.dto.PageContextDto;
import rs.raf.banka2_bek.assistant.service.AssistantService;
import rs.raf.banka2_bek.assistant.service.ProactiveSuggestionService;
import rs.raf.banka2_bek.assistant.tool.client.KokoroTtsClient;
import rs.raf.banka2_bek.auth.util.UserResolver;

import java.util.List;
import java.util.UUID;

/**
 * Arbitro endpoint surface (Day 1 stub).
 *
 *  POST   /assistant/chat                     SSE chat stream
 *  GET    /assistant/conversations            list of user's convs
 *  GET    /assistant/conversations/{uuid}/messages
 *  DELETE /assistant/conversations/{uuid}     soft delete
 *  POST   /assistant/conversations/{uuid}/clear   clear messages
 *  GET    /assistant/health                   provider + tools reachability
 *
 * Sve trase su `authenticated()` u GlobalSecurityConfig — dodaje se u Day 2.
 */
@RestController
@RequestMapping("/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;
    private final UserResolver userResolver;
    private final KokoroTtsClient kokoroTtsClient;
    private final ProactiveSuggestionService proactiveSuggestionService;

    /**
     * Field name MORA da matchuje ime bean-a u AssistantConfig
     * (assistantObjectMapper) — Spring resolution by name jer Lombok
     * {@code @RequiredArgsConstructor} ne propagira {@code @Qualifier}.
     */
    private final ObjectMapper assistantObjectMapper;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatRequestDto request) {
        return assistantService.chat(userResolver.resolveCurrent(), request);
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationListItemDto>> listConversations() {
        return ResponseEntity.ok(assistantService.listConversations(userResolver.resolveCurrent()));
    }

    @GetMapping("/conversations/{uuid}/messages")
    public ResponseEntity<List<MessageDto>> getMessages(@PathVariable UUID uuid) {
        return ResponseEntity.ok(assistantService.getMessages(userResolver.resolveCurrent(), uuid));
    }

    @DeleteMapping("/conversations/{uuid}")
    public ResponseEntity<Void> softDelete(@PathVariable UUID uuid) {
        assistantService.softDelete(userResolver.resolveCurrent(), uuid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversations/{uuid}/clear")
    public ResponseEntity<Void> clearMessages(@PathVariable UUID uuid) {
        assistantService.clearMessages(userResolver.resolveCurrent(), uuid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/health")
    public ResponseEntity<HealthDto> health() {
        return ResponseEntity.ok(assistantService.health());
    }

    /* ================ Phase 5 — Voice output (Kokoro TTS) ================ */

    /**
     * Body za POST /assistant/tts. Drzano u istom fajlu jer je samo jedan
     * endpoint i nema potrebe za zasebnim DTO modulom.
     */
    @Data
    public static class TtsRequestBody {
        @NotBlank
        @Size(max = 5000, message = "Tekst max 5000 chars")
        private String text;
        private String voice;       // null = default ("af_bella")
        private String lang;        // null = default ("en-us")
        private Double speed;       // null = 1.0
    }

    /**
     * Phase 5 — proaktivne sugestije za usera (idle funds, stale orders, ...).
     * FE povlaci on-demand (npr. pri otvaranju panel-a) i prikazuje kao
     * tooltip iznad FAB-a. Server-side analiza, nema spam-a.
     */
    @GetMapping("/suggestions")
    public ResponseEntity<List<ProactiveSuggestionService.ProactiveSuggestion>> suggestions() {
        var user = userResolver.resolveCurrent();
        return ResponseEntity.ok(
                proactiveSuggestionService.getSuggestions(user.userId(), user.userRole())
        );
    }

    /**
     * Phase 5 — multimodal upload (audio ILI slika). Gemma 4 native ASR za
     * audio (16kHz mono WAV) i image-to-text za slike. Ollama prosledjuje
     * media kroz {@code images} polje u messages array-u (issue ollama#15333).
     *
     * - audio: korisnik prica → mic → MediaRecorder PCM/WAV → BE → base64 →
     *   Ollama → Gemma 4 transkribuje + odgovara u istom turn-u
     * - image: PDF racun, screenshot grafika, ... → BE → base64 → Gemma 4
     *
     * Format vraca isti SSE stream kao /chat — FE deli istu stream parser
     * logiku. Razlika: BE pre LLM poziva ubacuje "audio" flag u user message
     * koji asistent koristi za sopstveni transcript.
     */
    @PostMapping(value = "/chat-multipart", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatMultipart(
            @RequestPart(value = "media", required = false) org.springframework.web.multipart.MultipartFile media,
            @RequestPart("message") String message,
            @RequestPart(value = "conversationUuid", required = false) String conversationUuid,
            @RequestPart(value = "agenticMode", required = false) String agenticMode,
            @RequestPart(value = "useTools", required = false) String useTools,
            @RequestPart(value = "pageContext", required = false) String pageContextJson
    ) {
        ChatRequestDto request = new ChatRequestDto();
        request.setMessage(message == null || message.isBlank()
                ? "(Korisnik je poslao audio/slika fajl — molim te transkribuj i odgovori.)"
                : message);
        if (conversationUuid != null && !conversationUuid.isBlank()) {
            try {
                request.setConversationUuid(UUID.fromString(conversationUuid));
            } catch (IllegalArgumentException ignored) { /* novi UUID */ }
        }
        request.setUseTools(useTools == null || "true".equalsIgnoreCase(useTools));
        request.setAgenticMode("true".equalsIgnoreCase(agenticMode));

        // Page context — opciono multipart polje sa JSON sadrzajem; tih fail
        // ako je malformed, ide se bez konteksta.
        if (pageContextJson != null && !pageContextJson.isBlank()) {
            try {
                PageContextDto pc = assistantObjectMapper.readValue(pageContextJson, PageContextDto.class);
                request.setPageContext(pc);
            } catch (Exception ignored) { /* invalid JSON — ignorisi */ }
        }

        if (media != null && !media.isEmpty()) {
            try {
                String b64 = java.util.Base64.getEncoder().encodeToString(media.getBytes());
                request.setMediaBase64(java.util.List.of(b64));
            } catch (java.io.IOException e) {
                // Tih fail — message ide bez media
            }
        }
        return assistantService.chat(userResolver.resolveCurrent(), request);
    }

    /**
     * POST /assistant/tts — generise audio iz teksta i strim-uje WAV bytes.
     * FE moze da uzme response kao Blob i prosledi <audio> tag-u.
     */
    @PostMapping(value = "/tts", produces = "audio/wav")
    public ResponseEntity<byte[]> tts(@Valid @RequestBody TtsRequestBody body) {
        double speed = body.getSpeed() == null ? 1.0 : body.getSpeed();
        byte[] wav = kokoroTtsClient.synthesize(body.getText(), body.getVoice(), body.getLang(), speed);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(wav);
    }
}
