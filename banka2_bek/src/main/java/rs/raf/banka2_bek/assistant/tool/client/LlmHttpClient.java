package rs.raf.banka2_bek.assistant.tool.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;
import rs.raf.banka2_bek.assistant.dto.openai.OpenAiChatChunk;
import rs.raf.banka2_bek.assistant.dto.openai.OpenAiChatRequest;
import rs.raf.banka2_bek.assistant.dto.openai.OpenAiChatResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * HTTP klijent za OpenAI-compatible Chat Completions endpoint (Ollama / LM Studio).
 *
 * Koristi {@link java.net.http.HttpClient} (Java 11+) jer Spring 7 RestClient
 * body() pattern nije pouzdano serijalizovao body preko Docker network-a
 * — vidi WikipediaToolClient komentar.
 *
 * Daje 3 metode:
 * <ul>
 *   <li>{@link #chatNonStream(OpenAiChatRequest)} — sinhrono za tool-call iteracije</li>
 *   <li>{@link #chatStream(OpenAiChatRequest, Consumer)} — token-po-token finalni odgovor</li>
 *   <li>{@link #ping()} — health check (GET /models)</li>
 * </ul>
 */
@Component
@Slf4j
public class LlmHttpClient {

    private static final String SSE_PREFIX = "data: ";
    private static final String SSE_DONE = "[DONE]";

    private final AssistantProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;

    public LlmHttpClient(AssistantProperties properties,
                         @Qualifier("assistantObjectMapper") ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.baseUrl = properties.getBaseUrl();
        this.apiKey = properties.getApiKey();
        // Phase 5 optimizacija — dedicated thread pool za HTTP klijent.
        // Default JDK HttpClient koristi ForkJoinPool.commonPool() koji deli
        // sa svim ostalim async pozivima u JVM-u. Pri 10+ concurrent agentic
        // chat sesija to je usko grlo. Vlastiti pool sa 16 thread-a daje
        // izolovan throughput za LLM pozive.
        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(
                        16,
                        r -> {
                            Thread t = new Thread(r, "arbitro-llm-http");
                            t.setDaemon(true);
                            return t;
                        });
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .executor(executor)
                .build();
    }

    public OpenAiChatResponse chatNonStream(OpenAiChatRequest request) {
        OpenAiChatRequest forced = new OpenAiChatRequest(
                request.model(), request.messages(), request.tools(), request.toolChoice(),
                Boolean.FALSE, request.temperature(), request.topP(),
                request.topK(), request.maxTokens());
        String json;
        try {
            json = objectMapper.writeValueAsString(forced);
        } catch (JsonProcessingException e) {
            throw new LlmHttpException("LLM request serialization failed: " + e.getMessage(), e);
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                String body = resp.body();
                log.error("LLM non-stream HTTP {}: {}", resp.statusCode(), body);
                throw new LlmHttpException("LLM provider returned " + resp.statusCode() + ": " + body, null);
            }
            return objectMapper.readValue(resp.body(), OpenAiChatResponse.class);
        } catch (LlmHttpException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM non-stream call failed: {}", e.getMessage());
            throw new LlmHttpException("LLM provider unreachable: " + e.getMessage(), e);
        }
    }

    public void chatStream(OpenAiChatRequest request, Consumer<OpenAiChatChunk> onChunk) {
        OpenAiChatRequest forced = new OpenAiChatRequest(
                request.model(), request.messages(), request.tools(), request.toolChoice(),
                Boolean.TRUE, request.temperature(), request.topP(),
                request.topK(), request.maxTokens());
        String json;
        try {
            json = objectMapper.writeValueAsString(forced);
        } catch (JsonProcessingException e) {
            throw new LlmHttpException("LLM stream request serialization failed: " + e.getMessage(), e);
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<java.io.InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                throw new LlmHttpException("LLM provider stream returned " + resp.statusCode(), null);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    if (!line.startsWith(SSE_PREFIX)) continue;
                    String payload = line.substring(SSE_PREFIX.length()).trim();
                    if (SSE_DONE.equals(payload)) break;
                    try {
                        OpenAiChatChunk chunk = objectMapper.readValue(payload, OpenAiChatChunk.class);
                        onChunk.accept(chunk);
                    } catch (JsonProcessingException ex) {
                        log.warn("Skipping malformed SSE chunk: {}", payload);
                    }
                }
            }
        } catch (LlmHttpException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM stream call failed: {}", e.getMessage());
            throw new LlmHttpException("LLM provider unreachable: " + e.getMessage(), e);
        }
    }

    public boolean ping() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/models"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() / 100 == 2;
        } catch (Exception e) {
            log.debug("LLM provider ping failed: {}", e.getMessage());
            return false;
        }
    }

    public Duration timeout() {
        return Duration.ofMillis(properties.getTimeoutMs());
    }

    public String model() {
        return properties.getModel();
    }

    public static class LlmHttpException extends RuntimeException {
        public LlmHttpException(String message, Throwable cause) { super(message, cause); }
    }
}
