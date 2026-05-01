package rs.raf.banka2_bek.assistant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Per-user, per-minute rate limit za /assistant/chat.
 *
 * Sliding window: drzimo deque timestamps za svakog korisnika; brojimo koliko
 * ih je u poslednjih 60 sekundi. Limit dolazi iz {@code assistant.rate-limit-per-user-per-min}
 * (default 20). Bez external Caffeine dependency — ConcurrentLinkedDeque je dovoljan
 * za ovaj scope.
 *
 * NAPOMENA: kad BE napusti prefiks chat-a sa 4 LLM iteracije, jedan corisnikov
 * "request" je samo jedan tick u rate-limiter-u (ne broji se po HTTP poziva
 * ka Ollama-u). Brojimo pozive korisnika ka {@code POST /assistant/chat}.
 */
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final AssistantProperties properties;
    private final Map<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(Long userId, String userRole) {
        String key = userRole + ":" + userId;
        Duration window = Duration.ofMinutes(1);
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);

        Deque<Instant> deque = windows.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (deque) {
            // Cisti stare unose
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            int limit = properties.getRateLimitPerUserPerMin();
            if (deque.size() >= limit) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}
