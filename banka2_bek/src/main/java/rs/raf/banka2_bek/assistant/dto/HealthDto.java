package rs.raf.banka2_bek.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthDto {
    /** "ollama" ili "lmstudio" */
    private String provider;
    /** Konfigurisan model id (npr. "gemma4:e4b") */
    private String model;
    private boolean llmReachable;
    private boolean wikipediaToolReachable;
    private boolean ragToolReachable;
    /** Phase 5 — Kokoro TTS sidecar reachability. */
    private boolean ttsReachable;
}
