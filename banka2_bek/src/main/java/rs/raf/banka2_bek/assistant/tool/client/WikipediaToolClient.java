package rs.raf.banka2_bek.assistant.tool.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
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
import java.util.Map;

/**
 * Klijent za Wikipedia FastAPI sidecar (Banka-2-Tools/wikipedia-service).
 *
 * Koristi {@link java.net.http.HttpClient} (Java 11+) umesto Spring RestClient
 * jer Spring 7 RestClient body() pattern nije pouzdano serijalizovao body
 * preko Docker network-a (FastAPI je primao "input: null" 422 greske).
 * java.net.http je low-level i deterministicki — string body se uvek salje.
 *
 * Endpointi:
 *   POST /search   {query, lang, limit}     -> {results: [...]}
 *   POST /summary  {title, lang, sentences} -> {title, lang, summary, ...}
 *   GET  /health
 */
@Component
@Slf4j
public class WikipediaToolClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public WikipediaToolClient(AssistantProperties properties,
                               @Qualifier("assistantObjectMapper") ObjectMapper objectMapper) {
        this.baseUrl = properties.getTools().getWikipedia().getUrl();
        this.objectMapper = objectMapper;
        this.timeout = Duration.ofMillis(properties.getTools().getWikipedia().getTimeoutMs());
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public Map<String, Object> search(String query, String lang, int limit) {
        return postJson("/search", Map.of("query", query, "lang", lang, "limit", limit));
    }

    public Map<String, Object> summary(String title, String lang, int sentences) {
        return postJson("/summary", Map.of("title", title, "lang", lang, "sentences", sentences));
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
            log.debug("Wikipedia tool ping failed: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> postJson(String path, Map<String, Object> body) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            log.warn("Wikipedia tool {} body serialization failed: {}", path, e.getMessage());
            return Map.of("error", "Body serialization failed: " + e.getMessage());
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                log.warn("Wikipedia tool {} HTTP {}: {}", path, resp.statusCode(), resp.body());
                return Map.of("error", "Wikipedia tool HTTP " + resp.statusCode() + ": " + resp.body());
            }
            return objectMapper.readValue(resp.body(), MAP_TYPE);
        } catch (Exception e) {
            log.warn("Wikipedia tool {} failed: {}", path, e.getMessage());
            return Map.of("error", "Wikipedia tool unreachable: " + e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        // java.net.http.HttpClient nema close() do Java 21+; resource cleanup je auto.
    }
}
