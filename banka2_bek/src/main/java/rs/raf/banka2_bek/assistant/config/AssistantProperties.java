package rs.raf.banka2_bek.assistant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Arbitro asistent konfiguracija.
 * Default provider je Ollama (port 11434) — LM Studio (port 1234) je fallback
 * zbog otvorenog tool_calls bug-a u LM Studio Gemma 4 (mlx-lm#1096).
 *
 * Reference: Info o predmetu/LLM_Asistent_Plan.txt v3.2 (29.04.2026).
 */
@Component
@ConfigurationProperties(prefix = "assistant")
@Getter
@Setter
public class AssistantProperties {

    private boolean enabled = true;
    private String name = "Arbitro";
    private String provider = "ollama";
    private String baseUrl = "http://host.docker.internal:11434/v1";
    /**
     * Default model — pokazuje na "-gpu" derivat sa Modelfile PARAMETER
     * num_gpu 999 (kreira ga ollama-pull init kontejner pri prvom startu).
     * Forsira sve layer-e u VRAM (8GB) umesto 3GB auto-split-a.
     * Override-uj na "gemma4:e2b" ako Tools stack nije pokrenut sa init.
     */
    private String model = "gemma4:e2b-gpu";
    private String apiKey = "ollama";

    /**
     * v3.5: Token limit povecan sa 2048 → 8192 jer je Luka prijavio "kad
     * dobaci limit dobije se network error". Gemma 4 E4B podrzava 128K
     * context, max-tokens kontrolise samo response duzinu — 8192 je
     * dovoljno za detaljne instrukcije + multi-paragraph odgovore.
     */
    private int maxTokens = 8192;
    private double temperature = 1.0;
    private double topP = 0.95;
    private int topK = 64;
    private int contextWindow = 32768;
    /**
     * v3.5: SseEmitter timeout 0 znaci NEMA timeout-a — sprecava
     * AsyncRequestTimeoutException za long-running stream-ove sa Phase 4
     * agentic flow-om (preview → user confirm moze potrajati 5+ min).
     * Klijent kontrolise duzinu kroz AbortController. Default ostaje
     * 120000ms za chat (non-agentic) iz backward-compat razloga.
     */
    private int timeoutMs = 120_000;
    private int rateLimitPerUserPerMin = 20;
    private int conversationRetentionDays = 7;
    private int historyWindowSize = 8;
    /**
     * Phase 5 — auto-summary se okida kad konverzacija predje
     * {@code summaryTriggerThreshold} poruka. Helper LLM poziv koji
     * kondenzuje poslednjih N-historyWindowSize starih poruka u jedan
     * "context summary" koji ide u system prompt umesto literally history-ja.
     * Ako je 0, summary funkcionalnost je iskljucena.
     */
    private int summaryTriggerThreshold = 16;
    private int summaryMaxTokens = 400;
    private int healthCheckIntervalSeconds = 60;

    private final Reasoning reasoning = new Reasoning();
    private final Tools tools = new Tools();
    private final Agentic agentic = new Agentic();

    @Getter
    @Setter
    public static class Reasoning {
        private boolean enabled = true;
        private boolean heuristic = true;
        private int budgetTokens = 2048;
    }

    @Getter
    @Setter
    public static class Tools {
        private boolean enabled = true;
        private int maxIterations = 5;
        private final Wikipedia wikipedia = new Wikipedia();
        private final Rag rag = new Rag();
        private final Calculator calculator = new Calculator();
        private final Internal internal = new Internal();

        @Getter
        @Setter
        public static class Wikipedia {
            private String url = "http://wikipedia-tool:8090";
            private int timeoutMs = 10_000;
        }

        @Getter
        @Setter
        public static class Rag {
            private String url = "http://rag-tool:8091";
            private int timeoutMs = 8_000;
        }

        @Getter
        @Setter
        public static class Calculator {
            private boolean enabled = true;
        }

        @Getter
        @Setter
        public static class Tts {
            private boolean enabled = true;
            private String url = "http://host.docker.internal:8092";
            private int timeoutMs = 60_000;
            private String defaultVoice = "af_bella";
            private String defaultLang = "en-us";
        }

        private final Tts tts = new Tts();

        @Getter
        @Setter
        public static class Internal {
            private boolean balanceSummary = true;
            private boolean recentOrders = true;
            private boolean exchangeRate = true;
            private boolean calculator = true;
            private boolean portfolioSummary = false;
            private boolean activeOtcOffers = false;
            private boolean loanSummary = false;
        }
    }

    /**
     * Phase 4 (v3.5) agentic mode konfiguracija. Default OFF — opt-in po useru
     * u FE settings dropdown-u. BE proverava {@code agenticMode} polje u
     * ChatRequestDto-u; bez explicit on, write tool-ovi se NE registruju u
     * tool dispatcher-u. Defense in depth: BE validira na vise mesta da je
     * mode aktivan pre nego sto se write akcija pripremi.
     */
    @Getter
    @Setter
    public static class Agentic {
        private boolean enabled = true;
        /** Default state ako klijent ne posalje agenticMode polje. Sigurno OFF. */
        private boolean enabledByDefault = false;
        /** TTL za PENDING agent action — auto EXPIRED ako user ne potvrdi. */
        private int actionTtlMin = 5;
        /** Max agentic akcija po useru po minuti. Krsce od chat-a (20). */
        private int rateLimitPerMin = 5;
        /** Cron za auto-EXPIRED scheduler — svake 1 minute. */
        private String expirationScheduleCron = "0 * * * * *";
    }
}
