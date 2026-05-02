package rs.raf.banka2_bek.assistant.tool.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP klijent za Kokoro TTS sidecar (Phase 5 voice output).
 *
 * Endpoint: POST {url}/tts vraca audio/wav bytes.
 *
 * Koristi {@link java.net.http.HttpClient} (paritet sa LlmHttpClient /
 * RagToolClient / WikipediaToolClient).
 */
@Component
@Slf4j
public class KokoroTtsClient {

    private final AssistantProperties properties;
    private final ObjectMapper assistantObjectMapper;
    private final HttpClient http;
    private final String baseUrl;
    private final Duration timeout;

    public KokoroTtsClient(AssistantProperties properties,
                           @Qualifier("assistantObjectMapper") ObjectMapper assistantObjectMapper) {
        this.properties = properties;
        this.assistantObjectMapper = assistantObjectMapper;
        this.baseUrl = properties.getTools().getTts().getUrl();
        this.timeout = Duration.ofMillis(properties.getTools().getTts().getTimeoutMs());
        // HTTP/1.1 obavezno — uvicorn (FastAPI) odbacuje upgrade u HTTP/2
        // sa "Invalid HTTP request received" warning-om i 422 Unprocessable
        // Entity (body=null). Isti pattern kao LlmHttpClient/RagToolClient.
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Sinhrono generise audio iz teksta. Vraca raw WAV bytes (24kHz mono PCM 16-bit).
     */
    public byte[] synthesize(String text, String voice, String lang, double speed) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        body.put("voice", voice == null ? properties.getTools().getTts().getDefaultVoice() : voice);
        body.put("lang", lang == null ? properties.getTools().getTts().getDefaultLang() : lang);
        body.put("speed", speed);

        String json;
        try {
            json = assistantObjectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new TtsException("Failed to serialize TTS request: " + e.getMessage(), e);
        }

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/tts"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "audio/wav")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                String errBody = new String(resp.body(), StandardCharsets.UTF_8);
                throw new TtsException("Kokoro TTS HTTP " + resp.statusCode() + ": " + errBody);
            }
            return resp.body();
        } catch (TtsException e) {
            throw e;
        } catch (Exception e) {
            throw new TtsException("Kokoro TTS unreachable: " + e.getMessage(), e);
        }
    }

    public boolean ping() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            log.debug("Kokoro TTS ping failed: {}", e.getMessage());
            return false;
        }
    }

    public static class TtsException extends RuntimeException {
        public TtsException(String msg) { super(msg); }
        public TtsException(String msg, Throwable cause) { super(msg, cause); }
    }
}
