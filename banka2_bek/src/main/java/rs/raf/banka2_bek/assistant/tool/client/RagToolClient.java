package rs.raf.banka2_bek.assistant.tool.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.List;
import java.util.Map;

/**
 * Klijent za RAG FastAPI sidecar (Banka-2-Tools/rag-service).
 *
 * Koristi {@link java.net.http.HttpClient} (Java 11+) — vidi WikipediaToolClient
 * komentar za razloge (Spring 7 RestClient body serializacija problemi).
 *
 *   POST /search  {query, top_k}  -> {results: [{text, source, celina, score}, ...]}
 *   GET  /health
 */
@Component
@Slf4j
public class RagToolClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public RagToolClient(AssistantProperties properties,
                         @Qualifier("assistantObjectMapper") ObjectMapper objectMapper) {
        this.baseUrl = properties.getTools().getRag().getUrl();
        this.objectMapper = objectMapper;
        this.timeout = Duration.ofMillis(properties.getTools().getRag().getTimeoutMs());
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public Map<String, Object> search(String query, int topK) {
        String json;
        try {
            json = objectMapper.writeValueAsString(Map.of("query", query, "top_k", topK));
        } catch (JsonProcessingException e) {
            log.warn("RAG tool /search body serialization failed: {}", e.getMessage());
            return Map.of("error", "Body serialization failed: " + e.getMessage(),
                          "results", List.of());
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/search"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                log.warn("RAG tool /search HTTP {}: {}", resp.statusCode(), resp.body());
                return Map.of("error", "RAG tool HTTP " + resp.statusCode() + ": " + resp.body(),
                              "results", List.of());
            }
            return objectMapper.readValue(resp.body(), MAP_TYPE);
        } catch (Exception e) {
            log.warn("RAG tool /search failed: {}", e.getMessage());
            return Map.of("error", "RAG tool unreachable: " + e.getMessage(),
                          "results", List.of());
        }
    }

    public Map<String, Object> health() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                return objectMapper.readValue(resp.body(), MAP_TYPE);
            }
            return Map.of("status", "unreachable", "code", resp.statusCode());
        } catch (Exception e) {
            log.debug("RAG tool /health failed: {}", e.getMessage());
            return Map.of("status", "unreachable", "error", e.getMessage());
        }
    }

    public boolean ping() {
        return "ok".equals(health().get("status"));
    }
}
