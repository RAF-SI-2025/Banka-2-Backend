package rs.raf.banka2_bek.assistant.tool;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry svih ToolHandler bean-ova. Spring autowire-uje listu, mi
 * filtriramo po isEnabled() i exposujemo dispatch po imenu.
 *
 * Day 1 stub: skuplja sve handler-e koji su Spring bean-ovi, ali jos nema
 * konkretnih implementacija — ToolHandler bean-ovi se dodaju u Day 2-3.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ToolRegistry {

    private final List<ToolHandler> handlers;
    private final AssistantProperties properties;

    private final Map<String, ToolHandler> byName = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        if (!properties.getTools().isEnabled()) {
            log.info("Arbitro tools disabled (assistant.tools.enabled=false)");
            return;
        }
        for (ToolHandler h : handlers) {
            if (!h.isEnabled()) continue;
            ToolHandler prev = byName.put(h.name(), h);
            if (prev != null) {
                log.warn("Duplicate tool name '{}'; replacing {} with {}",
                        h.name(), prev.getClass().getSimpleName(), h.getClass().getSimpleName());
            }
        }
        log.info("Arbitro tools registered: {}", byName.keySet());
    }

    public Optional<ToolHandler> get(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public Map<String, ToolHandler> getAll() {
        return Collections.unmodifiableMap(byName);
    }
}
