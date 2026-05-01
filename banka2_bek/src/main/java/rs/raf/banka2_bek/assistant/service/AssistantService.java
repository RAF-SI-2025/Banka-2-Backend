package rs.raf.banka2_bek.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;
import rs.raf.banka2_bek.assistant.dto.ChatRequestDto;
import rs.raf.banka2_bek.assistant.dto.ConversationListItemDto;
import rs.raf.banka2_bek.assistant.dto.HealthDto;
import rs.raf.banka2_bek.assistant.dto.MessageDto;
import rs.raf.banka2_bek.assistant.dto.PageContextDto;
import rs.raf.banka2_bek.assistant.dto.openai.OpenAiChatChunk;
import rs.raf.banka2_bek.assistant.dto.openai.OpenAiChatRequest;
import rs.raf.banka2_bek.assistant.dto.openai.OpenAiChatResponse;
import rs.raf.banka2_bek.assistant.dto.openai.OpenAiMessage;
import rs.raf.banka2_bek.assistant.dto.openai.OpenAiToolCall;
import rs.raf.banka2_bek.assistant.model.AssistantConversation;
import rs.raf.banka2_bek.assistant.model.AssistantMessage;
import rs.raf.banka2_bek.assistant.model.AssistantMessageRole;
import rs.raf.banka2_bek.assistant.repository.AssistantConversationRepository;
import rs.raf.banka2_bek.assistant.repository.AssistantMessageRepository;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.ToolHandler;
import rs.raf.banka2_bek.assistant.tool.ToolRegistry;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.assistant.tool.client.KokoroTtsClient;
import rs.raf.banka2_bek.assistant.tool.client.LlmHttpClient;
import rs.raf.banka2_bek.assistant.tool.client.RagToolClient;
import rs.raf.banka2_bek.assistant.tool.client.WikipediaToolClient;
import rs.raf.banka2_bek.assistant.agentic.dto.AgentActionPreviewDto;
import rs.raf.banka2_bek.assistant.agentic.service.AgentActionGateway;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Glavni Arbitro service.
 *
 * Tool-use loop pattern (plan §10.9):
 *   1) Persist user message
 *   2) Build context (system + history + user msg)
 *   3) Iteracija (max N):
 *      a) POST /v1/chat/completions sa stream=false
 *      b) Ako finish_reason == "tool_calls" → dispatch tools, append tool messages, loop
 *      c) Inace → break, krece finalni stream
 *   4) Streaming finalnog odgovora token-po-token
 *   5) Persist assistant message + done event
 *
 * Reasoning (plan §9.8): ako shouldUseReasoning(userMessage) → prepend `<|think|>`
 * u system prompt; BE parsira `<|channel>thought` markere i NIKAD ne prosledjuje
 * sirov thinking content ka FE-u (Claude Code stil — samo thinking_start /
 * thinking_end event-i).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantService {

    private final AssistantConversationRepository conversationRepository;
    private final AssistantMessageRepository messageRepository;
    private final ToolRegistry toolRegistry;
    private final AssistantProperties properties;
    private final LlmHttpClient llmHttpClient;
    private final WikipediaToolClient wikipediaToolClient;
    private final RagToolClient ragToolClient;
    private final KokoroTtsClient kokoroTtsClient;
    private final ContextBuilder contextBuilder;
    private final ContextSanitizer sanitizer;
    private final RateLimiter rateLimiter;
    private final AuditLogger auditLogger;
    private final AgentActionGateway agentActionGateway;
    // Phase 4 v3.5: deterministicki extractor za auto-resolution
    // (sender account, recipient account-by-name) — bypass-uje LLM za
    // poznate akcije i daje brz, pouzdan tool_call.
    private final rs.raf.banka2_bek.account.repository.AccountRepository accountRepository;
    private final rs.raf.banka2_bek.payment.repository.PaymentRecipientRepository paymentRecipientRepository;
    private final rs.raf.banka2_bek.client.repository.ClientRepository clientRepository;
    // Phase 4.5: interactive wizard za sve write akcije (Plati Milici, Kupi 5 AAPL, ...)
    private final rs.raf.banka2_bek.assistant.wizard.service.WizardService wizardService;
    // Phase 4.6: TRUE agentic intent classifier (LLM bira tool, ne regex).
    private final rs.raf.banka2_bek.assistant.wizard.service.IntentClassifier intentClassifier;
    // Imena polja matchuju imena @Bean-ova iz AssistantConfig — Spring resolve-uje
    // by name kad ima vise istog tipa (Lombok @RequiredArgsConstructor ne moze
    // da prosledi @Qualifier na konstruktor parametre).
    private final ObjectMapper assistantObjectMapper;
    private final TaskExecutor assistantTaskExecutor;

    /* ============================== CHAT ENDPOINT ============================== */

    public SseEmitter chat(UserContext user, ChatRequestDto request) {
        // v3.5: timeoutMs == 0 → null SseEmitter timeout (NEMA timeout-a, sprecava
        // AsyncRequestTimeoutException za long-running agentic flow-ove). Klijent
        // kontrolise duzinu kroz AbortController.
        Long timeout = properties.getTimeoutMs() <= 0 ? null : (long) properties.getTimeoutMs();
        SseEmitter emitter = timeout == null ? new SseEmitter() : new SseEmitter(timeout);
        emitter.onTimeout(() -> log.debug("ARBITRO chat timeout user={}:{}", user.userRole(), user.userId()));
        emitter.onError(t -> log.debug("ARBITRO chat onError: {}", t.getMessage()));

        if (!rateLimiter.tryAcquire(user.userId(), user.userRole())) {
            auditLogger.logRateLimit(user);
            SseEvents.error(emitter, "rate_limited",
                    "Previse zahteva. Pokusaj ponovo za minut.");
            emitter.complete();
            return emitter;
        }

        assistantTaskExecutor.execute(() -> runChat(user, request, emitter));
        return emitter;
    }

    @Transactional
    protected void runChat(UserContext user, ChatRequestDto request, SseEmitter emitter) {
        long startMs = System.currentTimeMillis();
        AssistantConversation conv = null;
        List<String> toolsUsed = new ArrayList<>();
        AtomicInteger totalTokens = new AtomicInteger(0);
        AtomicInteger reasoningChars = new AtomicInteger(0);

        try {
            conv = findOrCreateConversation(user, request.getConversationUuid());

            // Persist user message
            String userText = sanitizer.sanitize(request.getMessage());
            persistMessage(conv, AssistantMessageRole.USER, userText, null, null,
                    request.getPageContext());

            boolean useReasoning = properties.getReasoning().isEnabled()
                    && properties.getReasoning().isHeuristic()
                    && contextBuilder.shouldUseReasoning(request.getMessage());

            boolean useTools = properties.getTools().isEnabled()
                    && (request.getUseTools() == null || request.getUseTools());
            // Phase 4 v3.5: agentic mode — registruje WriteToolHandler-e u
            // tool list-i samo ako je explicit ON. Default OFF (sigurniji).
            boolean agenticOn = properties.getAgentic().isEnabled()
                    && Boolean.TRUE.equals(request.getAgenticMode());
            List<Map<String, Object>> tools = useTools ? toolDefinitions(agenticOn) : null;

            // Phase 5 multimodal + Phase 4 agentic: media ide u Ollama 'images' polje;
            // agenticOn ukljucuje AGENTIC_OVERLAY u system prompt (override-uje
            // pravilo "NE izvrsavas transakcije").
            List<OpenAiMessage> messages = contextBuilder.build(user, request.getPageContext(), conv,
                    request.getMessage(), properties.getHistoryWindowSize(), useReasoning,
                    request.getMediaBase64(), agenticOn);

            if (useReasoning) SseEvents.thinkingStart(emitter);

            // Phase 4.6: TRUE agentic intent detection.
            //
            // Tok:
            //   1. LLM (Gemma 4 E2B) sa minimalnim tool schemas (samo name +
            //      jedna recenica opisa, BEZ parametara) i tool_choice="required"
            //      bira koji tool najbolje matchuje user intent.
            //   2. Ako LLM uspesno odluci → wizard.start(toolName) preuzima
            //      i vodi korisnika kroz parametre interaktivno.
            //   3. Ako LLM ne uspe (vrati nepoznat ili nista) → fallback na
            //      stari regex {@code detectForcedTool} pattern matching.
            //
            // Razlog izdvajanja klasifikatora od glavne LLM petlje: kraci prompt
            // (~500 tokena) cini da Gemma 4 E2B pouzdano honoruje
            // tool_choice="required". Inace ide 1500+ tokena sa role overlay-om
            // + master prompt-om i model ignorise tool_choice.
            String forcedFirstTool = null;
            if (agenticOn) {
                forcedFirstTool = intentClassifier.classify(request.getMessage(), user)
                        .orElse(null);
                if (forcedFirstTool == null) {
                    // Regex fallback — pokriva slucajeve kad je LLM klasifikator
                    // pao (timeout, nepoznat tool, ...). 95% slucajeva LLM
                    // bolje, ali regex je deterministican backup.
                    forcedFirstTool = detectForcedTool(request.getMessage());
                    if (forcedFirstTool != null) {
                        log.info("ARBITRO IntentClassifier missed, regex fallback hit: {}",
                                forcedFirstTool);
                    }
                }
            }
            log.info("ARBITRO agentic={} forcedTool={} userMsg='{}'", agenticOn, forcedFirstTool,
                    request.getMessage() != null && request.getMessage().length() > 60
                        ? request.getMessage().substring(0, 60) + "..." : request.getMessage());

            // Phase 4.5: interactive wizard launcher.
            // Kada je agentic mode ON i intent detektovan, pokrecemo wizard koji
            // postavlja korisniku korak-po-korak pitanja sa biranjem opcija u
            // chat-u (umesto da forsiramo Gemma 4 da emit-uje tool_call). Wizard
            // template definise sve potrebne slot-ove (racun, primalac, iznos,
            // ...) i automatski preskace one koje moze da popuni iz user poruke.
            //
            // Tok:
            //   1. wizard.start(toolName, user, conv, msg) → prvi SlotPromptDto
            //   2. Emit agent_choice SSE event sa tim prompt-om
            //   3. Persist assistant message ("Pitam korisnika sa kog racuna...")
            //   4. Zatvori SSE stream — sledeci korak ide preko REST endpoint-a
            //      POST /assistant/wizard/{id}/select
            //
            // Razlog ovog pristupa: Gemma 4 E2B emit-uje tool_calls u 5+
            // razlicitih nesistematicnih formata; wizard pristup je
            // deterministican i daje korisniku puni nadzor koraka.
            if (forcedFirstTool != null && wizardService != null) {
                java.util.Optional<rs.raf.banka2_bek.assistant.wizard.dto.SlotPromptDto> firstPrompt =
                        wizardService.start(forcedFirstTool, user, conv.getConversationUuid(),
                                request.getMessage());
                if (firstPrompt.isPresent()) {
                    SseEvents.agentChoice(emitter, firstPrompt.get());
                    String shortHelpMsg = "Pitam te korak-po-korak da kreiramo: "
                            + firstPrompt.get().title() + ".";
                    persistMessage(conv, AssistantMessageRole.ASSISTANT, shortHelpMsg, null,
                            null, request.getPageContext());
                    long elapsed = System.currentTimeMillis() - startMs;
                    SseEvents.done(emitter, null, conv.getConversationUuid().toString(),
                            0, elapsed, 0);
                    log.info("ARBITRO_AUDIT wizard_start user={}:{} conv={} tool={} latencyMs={}",
                            user.userRole(), user.userId(), conv.getConversationUuid(),
                            forcedFirstTool, elapsed);
                    emitter.complete();
                    return;
                }
            }

            // Phase 4 v3.5: deterministicki short-circuit — kad je forsiran
            // tool prepoznat I mozemo extract-ovati parametre iz user poruke
            // pouzdanim regex-om, INJECT syntetski tool_call kao "fake LLM
            // response" za prvu iteraciju. Razlog: Gemma 4 E2B emit-uje tool
            // calls u 5+ razlicitih formata koje OpenAI-compat ne konvertuje.
            // Synthetic injection → 100% pouzdano + brzo (0s LLM call).
            OpenAiChatResponse syntheticFirstResp = null;
            if (forcedFirstTool != null) {
                Map<String, Object> extracted = extractParamsForTool(forcedFirstTool, request.getMessage(), user);
                if (extracted != null && !extracted.isEmpty()) {
                    log.info("ARBITRO short-circuit forced tool={} extracted={}",
                            forcedFirstTool, extracted);
                    String json;
                    try {
                        json = assistantObjectMapper.writeValueAsString(extracted);
                    } catch (JsonProcessingException e) {
                        json = "{}";
                    }
                    OpenAiToolCall synthetic = new OpenAiToolCall(
                            "call_short_" + System.currentTimeMillis(),
                            "function",
                            new OpenAiToolCall.Function(forcedFirstTool, json)
                    );
                    OpenAiMessage synthMsg = OpenAiMessage.assistantWithTools(null, List.of(synthetic));
                    OpenAiChatResponse.Choice synthChoice = new OpenAiChatResponse.Choice(
                            0, synthMsg, "tool_calls"
                    );
                    syntheticFirstResp = new OpenAiChatResponse(
                            "synthetic-" + System.currentTimeMillis(),
                            properties.getModel(),
                            List.of(synthChoice),
                            null
                    );
                }
            }

            // Tool-use loop
            int maxIter = properties.getTools().getMaxIterations();
            String finalContent = null;
            for (int iter = 0; iter < maxIter; iter++) {
                OpenAiChatResponse resp;
                // Iter 0 sa short-circuit-om: koristi synth response umesto LLM poziva.
                if (iter == 0 && syntheticFirstResp != null) {
                    resp = syntheticFirstResp;
                } else {
                    // Forsiraj tool SAMO u prvoj iteraciji (ako nije short-circuited).
                    String forced = (iter == 0) ? forcedFirstTool : null;
                    List<OpenAiMessage> messagesForCall = (forced != null)
                            ? buildLeanMessagesForForcedTool(request.getMessage())
                            : messages;
                    try {
                        resp = llmHttpClient.chatNonStream(buildRequest(messagesForCall, tools, false, forced));
                    } catch (LlmHttpClient.LlmHttpException ex) {
                        log.error("LLM provider unreachable", ex);
                        SseEvents.error(emitter, "llm_unreachable",
                                "AI provajder nije dostupan: " + ex.getMessage());
                        auditLogger.logError(user, conv.getConversationUuid().toString(), ex.getMessage());
                        emitter.complete();
                        return;
                    }
                }

                if (resp == null || resp.choices() == null || resp.choices().isEmpty()) {
                    finalContent = "(nema odgovora)";
                    break;
                }
                OpenAiChatResponse.Choice choice = resp.choices().get(0);
                OpenAiMessage assistantMsg = choice.message();
                String reason = choice.finishReason();

                // Parse content + thinking content (samo broji char count za audit).
                // Ollama Gemma 4 emituje thinking u `reasoning` polju umesto inline
                // u content sa <|channel>thought markerima — uzimamo effective.
                String rawContent = assistantMsg.effectiveContent();
                ContentParts parts = stripThinking(rawContent);
                reasoningChars.addAndGet(parts.thinkingChars);
                if (assistantMsg.reasoning() != null && !assistantMsg.reasoning().isBlank()
                        && (assistantMsg.content() == null || assistantMsg.content().isBlank())) {
                    // Ollama je vec razdvojio thinking u zaseban field — tretiraj ga kao
                    // reasoning chars i ne vracaj ga FE-u (Claude Code stil — nikad sirov)
                    reasoningChars.addAndGet(assistantMsg.reasoning().length());
                }

                // Phase 4 v3.5: fallback parser za Gemma 4 emit-ovan
                // tool_code format kad OpenAI-compat layer ne pretvori u
                // tool_calls. Gemma emit-uje u 2 varijante:
                //   1. [tool_code]functionName(arg=val)[/tool_code]  (klasican)
                //   2. [functionName(arg=val)]                       (skraceno)
                // Oba podrzana — kriticno za forsiran tool_choice na malom modelu.
                List<OpenAiToolCall> effectiveToolCalls = assistantMsg.toolCalls();
                if ((effectiveToolCalls == null || effectiveToolCalls.isEmpty())
                        && parts.cleanContent != null
                        && parts.cleanContent.contains("[")
                        && parts.cleanContent.contains("(")) {
                    List<OpenAiToolCall> parsed = parseGemmaToolCodeBlocks(parts.cleanContent);
                    if (!parsed.isEmpty()) {
                        effectiveToolCalls = parsed;
                        // Skini emit-ovane blokove iz content-a da ne idu kao tekst FE-u
                        String cleaned = parts.cleanContent
                                .replaceAll("(?s)\\[tool_code\\].*?\\[/tool_code\\]\\s*", "")
                                .replaceAll("\\[[a-zA-Z_][a-zA-Z0-9_]*\\s*\\([^)]*\\)\\s*\\]\\s*", "")
                                .trim();
                        parts = new ContentParts(cleaned, parts.thinkingChars);
                        reason = "tool_calls";
                    }
                }

                if (effectiveToolCalls != null && !effectiveToolCalls.isEmpty()
                        && "tool_calls".equals(reason)) {
                    if (useReasoning) {
                        SseEvents.thinkingEnd(emitter);
                        useReasoning = false;
                    }
                    // Append assistant msg with tool_calls + dispatch every tool
                    messages.add(OpenAiMessage.assistantWithTools(parts.cleanContent, assistantMsg.toolCalls()));
                    boolean previewedAgentic = false;
                    for (OpenAiToolCall tc : assistantMsg.toolCalls()) {
                        if (tc.function() == null) continue;
                        String name = tc.function().name();
                        Map<String, Object> args = parseArgs(tc.function().arguments());
                        SseEvents.toolCall(emitter, name, args);
                        toolsUsed.add(name);

                        ToolHandler handler = toolRegistry.get(name).orElse(null);

                        // Phase 4 v3.5: ako je tool WriteToolHandler i agenticOn,
                        // ne izvrsavamo direktno — kreiraj AgentAction + emit preview
                        // event. LLM dobija syntetic tool_result da zna da je preview
                        // poslat korisniku.
                        if (agenticOn && handler instanceof WriteToolHandler write) {
                            Map<String, Object> result;
                            boolean ok = true;
                            try {
                                AgentActionPreviewDto preview = agentActionGateway.createPending(
                                        conv.getConversationUuid().toString(),
                                        name, args, user, write);
                                SseEvents.actionPreview(emitter, preview);
                                result = Map.of(
                                        "status", "PREVIEW_SHOWN_TO_USER",
                                        "actionUuid", preview.getActionUuid(),
                                        "summary", preview.getSummary() == null ? "" : preview.getSummary(),
                                        "message", "Korisniku je prikazan preview akcije; sacekaj njegovu potvrdu (POTVRDI ili ODBACI). Nemoj predlagati istu akciju ponovo."
                                );
                                previewedAgentic = true;
                            } catch (AgentActionGateway.AgenticDisabledException e) {
                                ok = false;
                                result = Map.of("error", "AGENTIC_DISABLED",
                                        "message", "Agentic mode nije aktivan — predlozi #action:goto: link umesto.");
                            } catch (AgentActionGateway.AgenticRateLimitedException e) {
                                ok = false;
                                result = Map.of("error", "AGENTIC_RATE_LIMITED",
                                        "message", e.getMessage());
                            } catch (Exception e) {
                                ok = false;
                                result = Map.of("error", "PREVIEW_FAILED",
                                        "message", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                                log.warn("Agentic preview '{}' failed: {}", name, e.getMessage());
                            }
                            auditLogger.logToolCall(conv.getConversationUuid().toString(),
                                    name + "::preview", ok, 0);
                            SseEvents.toolResult(emitter, name, ok, summarizeResult(result));
                            String resultJson = jsonOrEmpty(result);
                            messages.add(OpenAiMessage.tool(tc.id(), resultJson));
                            continue;
                        }

                        // Standardni read-only tool — direktan dispatch
                        Map<String, Object> result;
                        long t0 = System.currentTimeMillis();
                        boolean ok = true;
                        try {
                            result = dispatchTool(name, args, user);
                        } catch (Exception e) {
                            ok = false;
                            result = Map.of("error", "Tool execution failed: " + e.getMessage());
                            log.warn("ARBITRO tool '{}' failed: {}", name, e.getMessage());
                        }
                        auditLogger.logToolCall(conv.getConversationUuid().toString(), name, ok,
                                System.currentTimeMillis() - t0);
                        SseEvents.toolResult(emitter, name, ok, summarizeResult(result));
                        emitSourceIfApplicable(emitter, name, result);

                        String resultJson = jsonOrEmpty(result);
                        messages.add(OpenAiMessage.tool(tc.id(), resultJson));
                    }
                    // Ako smo emit-ovali agentic preview, dovoljno je jedna iteracija
                    // — ne treba LLM da nastavlja petlju (cekamo confirm).
                    if (previewedAgentic) break;
                    continue;
                }

                // No tool calls — final answer
                finalContent = parts.cleanContent;
                if (useReasoning) {
                    SseEvents.thinkingEnd(emitter);
                    useReasoning = false;
                }
                break;
            }

            if (finalContent == null) {
                // Ako smo izasli iz petlje zbog agentic preview-a, ostavljamo
                // kratku poruku — pravi tool result ce stici posle confirm/reject-a.
                boolean hadPreview = toolsUsed.stream().anyMatch(t -> {
                    ToolHandler h = toolRegistry.get(t).orElse(null);
                    return h instanceof WriteToolHandler;
                });
                finalContent = hadPreview
                        ? "Pripremio sam akciju za izvrsenje. Pogledaj preview modal i klikni POTVRDI ili ODBACI."
                        : "(nije moguce dobiti odgovor — max iteracija dostignut)";
            }

            // Phase 5 optimizacija — pravi token-by-token streaming iz Ollame
            // umesto post-hoc word splitting-a. chatStream() vraca delta chunks
            // koje pakujemo u batch-ove od ~5 reci za smanjen SSE overhead.
            //
            // NAPOMENA: stream se ne aktivira ako:
            //   - finalContent je placeholder (agentic preview break, nema sta da streamujemo)
            //   - useStreaming=false u properties (debug toggle)
            // U tim slucajevima fallback na batch word emit ostaje.
            boolean shouldStream = !finalContent.startsWith("(nije moguce")
                    && !finalContent.startsWith("Pripremio sam akciju");
            if (shouldStream) {
                StringBuilder streamBatch = new StringBuilder();
                int[] batchCount = {0};
                try {
                    // Re-pozovi LLM sa stream=true SAMO za final iteraciju (bez tools)
                    llmHttpClient.chatStream(buildRequest(messages, null, true), chunk -> {
                        if (chunk.choices() == null || chunk.choices().isEmpty()) return;
                        OpenAiChatChunk.Choice c = chunk.choices().get(0);
                        if (c.delta() == null || c.delta().content() == null) return;
                        String delta = c.delta().content();
                        if (delta.isEmpty()) return;
                        streamBatch.append(delta);
                        // Flush kad batch dostigne ~5 reci ili 24 chars
                        if (delta.contains(" ") || streamBatch.length() >= 24) {
                            SseEvents.token(emitter, streamBatch.toString());
                            totalTokens.incrementAndGet();
                            streamBatch.setLength(0);
                            batchCount[0]++;
                        }
                    });
                } catch (Exception streamErr) {
                    log.warn("Token streaming failed, falling back to batch emit: {}",
                            streamErr.getMessage());
                    streamBatch.setLength(0);
                    batchCount[0] = 0;
                }
                // Flush preostalo
                if (streamBatch.length() > 0) {
                    SseEvents.token(emitter, streamBatch.toString());
                    totalTokens.incrementAndGet();
                }
                // Ako streaming nije ucitao ni jedan chunk (offline ili greska), fallback
                if (batchCount[0] == 0 && streamBatch.length() == 0 && totalTokens.get() == 0) {
                    fallbackBatchEmit(finalContent, emitter, totalTokens);
                }
            } else {
                fallbackBatchEmit(finalContent, emitter, totalTokens);
            }

            // Persist assistant message
            AssistantMessage assistantPersisted = persistMessage(conv, AssistantMessageRole.ASSISTANT,
                    finalContent, null, null, request.getPageContext());

            long latency = System.currentTimeMillis() - startMs;
            SseEvents.done(emitter, assistantPersisted.getId(),
                    conv.getConversationUuid().toString(),
                    totalTokens.get(), latency, reasoningChars.get());

            auditLogger.logChat(user, conv.getConversationUuid().toString(),
                    request.getPageContext() != null ? request.getPageContext().getRoute() : "",
                    request.getMessage() != null ? request.getMessage().length() : 0,
                    finalContent.length(), reasoningChars.get(), toolsUsed, latency, true);

            emitter.complete();
        } catch (Exception e) {
            log.error("ARBITRO chat failed", e);
            SseEvents.error(emitter, "internal_error", e.getMessage() != null ? e.getMessage() : "unknown");
            if (conv != null) {
                auditLogger.logError(user, conv.getConversationUuid().toString(),
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
            emitter.completeWithError(e);
        }
    }

    /* ============================== CRUD ============================== */

    @Transactional(readOnly = true)
    public List<ConversationListItemDto> listConversations(UserContext user) {
        return conversationRepository
                .findByUserIdAndUserRoleAndDeletedAtIsNullOrderByUpdatedAtDesc(user.userId(), user.userRole())
                .stream()
                .map(c -> ConversationListItemDto.builder()
                        .conversationUuid(c.getConversationUuid())
                        .title(c.getTitle())
                        .messageCount(messageRepository.countByConversationId(c.getId()))
                        .updatedAt(c.getUpdatedAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MessageDto> getMessages(UserContext user, UUID conversationUuid) {
        AssistantConversation conv = loadOwnedConversation(user, conversationUuid);
        return messageRepository.findByConversationIdOrderByIdAsc(conv.getId())
                .stream()
                .filter(m -> m.getRole() != AssistantMessageRole.SYSTEM
                        && m.getRole() != AssistantMessageRole.TOOL)
                .map(this::toMessageDto)
                .toList();
    }

    @Transactional
    public void softDelete(UserContext user, UUID conversationUuid) {
        AssistantConversation conv = loadOwnedConversation(user, conversationUuid);
        conv.setDeletedAt(LocalDateTime.now());
        conversationRepository.save(conv);
    }

    @Transactional
    public void clearMessages(UserContext user, UUID conversationUuid) {
        AssistantConversation conv = loadOwnedConversation(user, conversationUuid);
        messageRepository.deleteByConversationId(conv.getId());
    }

    public HealthDto health() {
        // Defensive: svaki ping poziv u zaseban try/catch (Throwable) — ako
        // neki klijent baci unexpected gresku (NoClassDefFoundError, OOM,
        // LinkageError), health endpoint ostaje stabilan i vraca pun JSON
        // umesto 400 sa praznim message-om (vidi runda 30.04 popodne).
        boolean llm = safelyPing(llmHttpClient::ping, "llm");
        boolean wiki = safelyPing(wikipediaToolClient::ping, "wikipedia");
        boolean rag = safelyPing(ragToolClient::ping, "rag");
        boolean tts = properties.getTools().getTts().isEnabled()
                && safelyPing(kokoroTtsClient::ping, "tts");
        return HealthDto.builder()
                .provider(properties.getProvider())
                .model(properties.getModel())
                .llmReachable(llm)
                .wikipediaToolReachable(wiki)
                .ragToolReachable(rag)
                .ttsReachable(tts)
                .build();
    }

    /**
     * Fallback emit kad streaming iz Ollame ne radi — koristi vec dobijen
     * finalContent iz non-stream tool dispatch petlje i emit-uje ga
     * batch-ovan po 5 reci.
     */
    private void fallbackBatchEmit(String content, SseEmitter emitter, AtomicInteger totalTokens) {
        if (content == null || content.isEmpty()) return;
        String[] words = content.split("(?<=\\s)");
        StringBuilder batch = new StringBuilder();
        int batched = 0;
        for (String word : words) {
            if (word.isEmpty()) continue;
            batch.append(word);
            batched++;
            totalTokens.incrementAndGet();
            if (batched >= 5) {
                SseEvents.token(emitter, batch.toString());
                batch.setLength(0);
                batched = 0;
            }
        }
        if (batch.length() > 0) {
            SseEvents.token(emitter, batch.toString());
        }
    }

    private boolean safelyPing(java.util.function.BooleanSupplier ping, String label) {
        try {
            return ping.getAsBoolean();
        } catch (Throwable t) {
            log.warn("Health ping '{}' failed unexpectedly: {}", label, t.toString());
            return false;
        }
    }

    /* ============================== INTERNAL HELPERS ============================== */

    private AssistantConversation findOrCreateConversation(UserContext user, UUID providedUuid) {
        if (providedUuid != null) {
            // FETCH JOIN messages eager — runChat radi na async thread-u
            // van transakcije pa lazy access baca LazyInitializationException
            // pri drugoj poruci. Vidi AssistantConversationRepository javadoc.
            AssistantConversation existing = conversationRepository
                    .findByConversationUuidWithMessages(providedUuid)
                    .orElse(null);
            if (existing != null && existing.getDeletedAt() == null
                    && existing.getUserId().equals(user.userId())
                    && existing.getUserRole().equals(user.userRole())) {
                return existing;
            }
        }
        AssistantConversation conv = AssistantConversation.builder()
                .userId(user.userId())
                .userRole(user.userRole())
                .build();
        return conversationRepository.save(conv);
    }

    private AssistantConversation loadOwnedConversation(UserContext user, UUID uuid) {
        AssistantConversation conv = conversationRepository.findByConversationUuid(uuid)
                .orElseThrow(() -> new EntityNotFoundException("Konverzacija ne postoji"));
        if (conv.getDeletedAt() != null
                || !conv.getUserId().equals(user.userId())
                || !conv.getUserRole().equals(user.userRole())) {
            throw new EntityNotFoundException("Konverzacija ne postoji");
        }
        return conv;
    }

    private AssistantMessage persistMessage(AssistantConversation conv, AssistantMessageRole role,
                                            String content, String toolCallId, String toolCallsJson,
                                            PageContextDto page) {
        AssistantMessage msg = AssistantMessage.builder()
                .conversation(conv)
                .role(role)
                .content(content == null ? "" : content)
                .toolCallId(toolCallId)
                .toolCalls(toolCallsJson)
                .pageRoute(page != null ? page.getRoute() : null)
                .pageName(page != null ? page.getPageName() : null)
                .build();
        AssistantMessage saved = messageRepository.save(msg);
        conv.setUpdatedAt(LocalDateTime.now());
        // Auto-set title from first user message
        if (role == AssistantMessageRole.USER && conv.getTitle() == null) {
            String preview = content == null ? "Razgovor" : content.trim();
            conv.setTitle(preview.length() > 80 ? preview.substring(0, 80) + "…" : preview);
        }
        conversationRepository.save(conv);
        return saved;
    }

    private MessageDto toMessageDto(AssistantMessage m) {
        return MessageDto.builder()
                .id(m.getId())
                .role(m.getRole())
                .content(m.getContent())
                .pageRoute(m.getPageRoute())
                .pageName(m.getPageName())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private OpenAiChatRequest buildRequest(List<OpenAiMessage> messages,
                                            List<Map<String, Object>> tools, boolean stream) {
        return buildRequest(messages, tools, stream, null);
    }

    /**
     * Phase 4 v3.5 overload — kad detektujemo akcione kljucne reci u user
     * poruci a agentic je ON, FORSIRAMO konkretan tool kombinacijom dve
     * tehnike (Ollama 0.22 OpenAI-compat ne honors {@code tool_choice} object
     * form za Gemma 4):
     * <ol>
     *   <li>Filtriramo {@code tools} listu na SAMO taj jedan tool</li>
     *   <li>Postavimo {@code tool_choice="required"} (standardni OpenAI flag)</li>
     * </ol>
     * Time se Gemma 4 E2B prisiljava da pozove jedini dostupan tool umesto
     * da odgovara plain tekstom.
     */
    private OpenAiChatRequest buildRequest(List<OpenAiMessage> messages,
                                            List<Map<String, Object>> tools, boolean stream,
                                            String forcedToolName) {
        List<Map<String, Object>> effectiveTools = tools;
        Object toolChoice;
        if (tools == null || tools.isEmpty()) {
            toolChoice = null;
        } else if (forcedToolName != null) {
            // Filtriraj na samo forsiran tool — Gemma 4 ne moze da skrene
            // u drugi tool ako ovaj jedini ima.
            effectiveTools = tools.stream()
                    .filter(t -> {
                        Object fn = t.get("function");
                        if (fn instanceof Map<?, ?> fm) {
                            return forcedToolName.equals(fm.get("name"));
                        }
                        return false;
                    })
                    .toList();
            if (effectiveTools.isEmpty()) {
                // Forsiran tool ne postoji u listi (npr. agentic OFF)
                effectiveTools = tools;
                toolChoice = "auto";
            } else {
                toolChoice = "required";
            }
        } else {
            toolChoice = "auto";
        }
        return new OpenAiChatRequest(
                properties.getModel(),
                messages,
                effectiveTools != null && !effectiveTools.isEmpty() ? effectiveTools : null,
                toolChoice,
                stream,
                properties.getTemperature(),
                properties.getTopP(),
                properties.getTopK(),
                properties.getMaxTokens()
        );
    }

    /**
     * Phase 4 v3.5: minimalan message list kad forsiramo tool. Long master
     * prompt confuses Gemma 4 E2B (5.1B params), pa kad imamo jasan agentic
     * intent, salji samo:
     * <ol>
     *   <li>Vrlo kratak system prompt koji kaze "pozovi forsiran tool"</li>
     *   <li>User-ovu poruku</li>
     * </ol>
     * Tako Gemma 4 E2B pouzdano emit-uje tool_calls (verifikovano direct
     * Ollama testom).
     */
    private List<OpenAiMessage> buildLeanMessagesForForcedTool(String userMessage) {
        List<OpenAiMessage> lean = new ArrayList<>();
        lean.add(OpenAiMessage.system(
                "Pozovi prilozeni tool sa parametrima izvucenim iz korisnikove poruke. " +
                "Odgovori SAMO tool_call-om, bez teksta. " +
                "Ako neki potreban parametar nije eksplicitan, izvuci ga iz konteksta " +
                "(npr. recipientName='Milica Nikolic' iz 'plati Milici 100 RSD')."
        ));
        lean.add(OpenAiMessage.user(sanitizer.sanitize(userMessage)));
        return lean;
    }

    /**
     * Phase 4 v3.5 deterministicki extractor — pokusa da iz user poruke
     * regex-om izvuce parametre potrebne za forsiran tool. Vraca map
     * sa min skupom parametara, ili null ako nije mogao pouzdano da
     * extract-uje. Ako vrati validne parametre, BE bypass-uje LLM call
     * i direktno gradi tool_call (radi kao "fake LLM response").
     *
     * Podrzano: create_payment (najcesci agentic flow).
     * Ostali tools (create_buy_order, block_card itd.) ce ici kroz LLM
     * jer obicno imaju vise opcionalnih parametara koje regex ne
     * pokriva pouzdano.
     */
    private Map<String, Object> extractParamsForTool(String toolName, String userMessage,
                                                      UserContext user) {
        if (userMessage == null || userMessage.isBlank()) return null;
        if ("create_payment".equals(toolName)) {
            return extractCreatePaymentParams(userMessage, user);
        }
        return null;
    }

    /**
     * Iz "Plati Milici Nikolic 100 RSD za rodjendan" + user contextom izvlaci:
     *   fromAccount   — userov primarni RSD racun
     *   toAccount     — primalcev racun iz PaymentRecipient liste (lookup po imenu)
     *   amount        — 100
     *   description   — "za rodjendan" (default "Placanje")
     *   recipientName — "Milici Nikolic"
     *   paymentCode   — "289" (default)
     *
     * Vraca null ako ne moze da rezolvira sender ili recipient — onda nista
     * ne short-circuit-ujemo i fallback na regularan LLM tool-call flow.
     */
    private Map<String, Object> extractCreatePaymentParams(String msg, UserContext user) {
        // 1. Iznos
        java.util.regex.Matcher amountMatch = java.util.regex.Pattern
                .compile("([0-9]+(?:[\\.,][0-9]+)?)\\s*(rsd|din|dinara|eur|usd|chf|gbp)?",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(msg);
        Double amount = null;
        if (amountMatch.find()) {
            try {
                amount = Double.parseDouble(amountMatch.group(1).replace(",", "."));
            } catch (NumberFormatException ignored) { /* skip */ }
        }
        if (amount == null || amount <= 0) return null;

        // 2. Recipient name — izmedju glagola placanja i broja iznosa
        java.util.regex.Matcher recipMatch = java.util.regex.Pattern
                .compile("(?i)\\b(?:plati|uplati|salji|posalji|salj|posalj)\\s+(.+?)\\s+[0-9]")
                .matcher(msg);
        String recipientName = null;
        if (recipMatch.find()) {
            recipientName = recipMatch.group(1).trim();
            recipientName = recipientName.replaceAll("(?i)\\s+(za|od|u|i|sa|na)\\s*$", "");
        }
        if (recipientName == null || recipientName.isBlank()) return null;

        // 3. Svrha
        java.util.regex.Matcher purposeMatch = java.util.regex.Pattern
                .compile("(?i)\\bza\\s+([\\p{L}0-9\\s\\-,.]+?)$")
                .matcher(msg);
        String description = "Placanje";
        if (purposeMatch.find()) {
            description = "za " + purposeMatch.group(1).trim();
        }

        // 4. Sender account — userov prvi aktivan RSD racun
        if (!"CLIENT".equalsIgnoreCase(user.userRole())) return null;
        var clientOpt = clientRepository.findById(user.userId());
        if (clientOpt.isEmpty()) return null;
        var senderAccounts = accountRepository.findByClientId(user.userId());
        final java.math.BigDecimal amountBd = java.math.BigDecimal.valueOf(amount);
        String fromAccount = senderAccounts.stream()
                .filter(a -> a.getAvailableBalance() != null
                        && a.getAvailableBalance().compareTo(amountBd) >= 0)
                .map(a -> a.getAccountNumber())
                .findFirst()
                .orElse(senderAccounts.stream().findFirst().map(a -> a.getAccountNumber()).orElse(null));
        if (fromAccount == null) return null;

        // 5. Recipient account — iz PaymentRecipient liste, lookup po fuzzy match imena
        var recipients = paymentRecipientRepository.findByClientOrderByCreatedAtDesc(clientOpt.get());
        final String normRecipient = normalizeForMatch(recipientName);
        var matched = recipients.stream()
                .filter(r -> {
                    String name = normalizeForMatch(r.getName());
                    if (name == null || normRecipient == null) return false;
                    // Match ako svaka rec iz user-ovog imena postoji u recipient name
                    String[] tokens = normRecipient.split("\\s+");
                    for (String t : tokens) {
                        if (t.length() < 3) continue;
                        // Prefix match (Milici matches Milica, Nikolic matches Nikolic)
                        if (!nameTokenMatches(name, t)) return false;
                    }
                    return true;
                })
                .findFirst();
        if (matched.isEmpty()) {
            log.info("ARBITRO short-circuit: nije nadjen recipient '{}' u listi {} primalaca",
                    recipientName, recipients.size());
            return null;
        }
        String toAccount = matched.get().getAccountNumber();
        String resolvedName = matched.get().getName();

        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("fromAccount", fromAccount);
        params.put("toAccount", toAccount);
        params.put("amount", amount);
        params.put("description", description);
        params.put("recipientName", resolvedName);
        params.put("paymentCode", "289");
        return params;
    }

    /** Normalizuje string za fuzzy match — lowercase + trim + zamena dijakritika. */
    private String normalizeForMatch(String s) {
        if (s == null) return null;
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .trim();
    }

    /**
     * Prefix-bazirani token match (case-insensitive). "milici" matches "milica"
     * (3-char prefix "mil"), "nikolic" matches "nikolic" (full).
     */
    private boolean nameTokenMatches(String haystack, String needle) {
        if (haystack == null || needle == null || needle.length() < 3) return false;
        String prefix = needle.substring(0, Math.min(needle.length(), 4));
        return haystack.contains(prefix);
    }

    /**
     * Heuristika — detektuje akcionu nameru u user poruci i mapira na konkretan
     * write tool. Vraca null ako nema jasne namere (LLM koristi tool_choice="auto").
     * Pozove se SAMO kad je agenticOn=true.
     */
    private String detectForcedTool(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return null;
        String m = userMessage.toLowerCase();
        // Placanje (najsiri keyword set jer je najcesce trazen)
        if (m.matches(".*\\b(plati|uplati|salji|posalji|salj|posalj)\\b.*")) {
            return "create_payment";
        }
        // Buy
        if (m.matches(".*\\b(kupi|buy)\\b.*") && (m.contains("akcij") || m.contains("hartij")
                || m.matches(".*\\b[a-z]{1,5}\\b.*"))) {
            return "create_buy_order";
        }
        // Sell
        if (m.matches(".*\\b(prodaj|sell)\\b.*")) {
            return "create_sell_order";
        }
        // Transfer
        if (m.matches(".*\\b(prebaci|prebaciti|transfer|transferi[sš]i)\\b.*")) {
            return "create_internal_transfer";
        }
        // Card block
        if (m.matches(".*\\bblokiraj\\b.*kartic.*") || m.matches(".*\\bblock\\b.*card.*")) {
            return "block_card";
        }
        if (m.matches(".*\\b(odblokiraj|otkljucaj|unblock)\\b.*kartic.*")) {
            return "unblock_card";
        }
        // Cancel order
        if (m.matches(".*\\b(otkazi|otkaz|cancel)\\b.*\\b(nalog|order)\\b.*")) {
            return "cancel_order";
        }
        // Fund
        if (m.matches(".*\\b(ulozi|invest|ulaz|ulaganj)\\b.*\\b(fond|fund)\\b.*")) {
            return "invest_in_fund";
        }
        if (m.matches(".*\\b(povuci|withdraw|isplati)\\b.*\\b(fond|fund)\\b.*")) {
            return "withdraw_from_fund";
        }
        return null;
    }

    /**
     * Vraca tool definicije za LLM-ov tools array. Ako je {@code agenticOn} false,
     * iskljucujemo {@link WriteToolHandler}-e — LLM ne dobija ni schema ni
     * mogucnost da ih pozove.
     */
    private List<Map<String, Object>> toolDefinitions(boolean agenticOn) {
        return toolRegistry.getAll().values().stream()
                .filter(h -> agenticOn || !(h instanceof WriteToolHandler))
                .map(h -> {
                    ToolDefinition def = h.definition();
                    return def.toOpenAiSpec();
                })
                .toList();
    }

    private Map<String, Object> dispatchTool(String name, Map<String, Object> args, UserContext user) {
        ToolHandler handler = toolRegistry.get(name)
                .orElseThrow(() -> new IllegalArgumentException("Nepoznat alat: " + name));
        if (handler instanceof WriteToolHandler) {
            // Trebalo je biti perehvaceno u runChat() pre dispatch-a — fallback guard.
            throw new IllegalStateException(
                    "WriteToolHandler '" + name + "' ne moze biti dispatch-ovan direktno; " +
                            "agentic gateway ga obrađuje. Da li je agenticOn flag bio resetovan?");
        }
        return handler.execute(args, user);
    }

    private Map<String, Object> parseArgs(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = assistantObjectMapper.readValue(raw, Map.class);
            return map;
        } catch (JsonProcessingException e) {
            log.warn("Invalid tool args JSON, falling back to empty: {}", raw);
            return Map.of();
        }
    }

    /**
     * Phase 4 v3.5: Gemma 4 native tool-call format parser.
     *
     * Gemma 4 cesto emit-uje tool poziv kao tekst u {@code [tool_code]}
     * blokovima umesto u OpenAI-compat tool_calls JSON-u (Ollama 0.22 layer
     * ne pretvara to pouzdano za male modele). Format:
     * <pre>
     * [tool_code]functionName(key1="val1", key2=42, key3=true)[/tool_code]
     * </pre>
     * Parsiramo to kao listu sintetskih {@link OpenAiToolCall} entry-ja
     * koje dispatcher tretira identicno kao native tool_calls.
     *
     * Vraca praznu listu ako nema validnih blokova.
     */
    private List<OpenAiToolCall> parseGemmaToolCodeBlocks(String content) {
        List<OpenAiToolCall> calls = new ArrayList<>();
        if (content == null) return calls;
        // Pokrivamo 2 varijante koje Gemma 4 emit-uje:
        //   1. [tool_code]functionName(args)[/tool_code]
        //   2. [functionName(args)]
        java.util.regex.Pattern[] patterns = {
                java.util.regex.Pattern.compile(
                        "\\[tool_code\\]\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(([^)]*)\\)\\s*\\[/tool_code\\]",
                        java.util.regex.Pattern.DOTALL),
                java.util.regex.Pattern.compile(
                        "\\[\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(([^)]*)\\)\\s*\\]")
        };
        int idx = 0;
        for (java.util.regex.Pattern blockRe : patterns) {
            java.util.regex.Matcher m = blockRe.matcher(content);
            while (m.find()) {
                String fnName = m.group(1);
                // Skip pseudo-funkcije koje ne postoje u registry-ju
                if (toolRegistry.get(fnName).isEmpty()) continue;
                String argsRaw = m.group(2);
                Map<String, Object> args = parseGemmaToolArgs(argsRaw);
                String json;
                try {
                    json = assistantObjectMapper.writeValueAsString(args);
                } catch (JsonProcessingException e) {
                    json = "{}";
                }
                calls.add(new OpenAiToolCall(
                        "call_gemma_" + System.currentTimeMillis() + "_" + idx,
                        "function",
                        new OpenAiToolCall.Function(fnName, json)
                ));
                idx++;
            }
            if (!calls.isEmpty()) break;  // ako prvi pattern matchovao, ne treba drugi
        }
        return calls;
    }

    /**
     * Parsira Python-style kwarg listu iz Gemma tool_code-a.
     * Primer ulaza: {@code recipient="Milica Nikolic", amount=100, active=true}
     */
    private Map<String, Object> parseGemmaToolArgs(String raw) {
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return args;
        // Split na zarez koji NIJE unutar navodnika.
        java.util.regex.Pattern kwRe = java.util.regex.Pattern.compile(
                "([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|true|false|null|[\\-+]?[0-9]+(?:\\.[0-9]+)?)"
        );
        java.util.regex.Matcher m = kwRe.matcher(raw);
        while (m.find()) {
            String key = m.group(1);
            String val = m.group(2);
            args.put(key, parseGemmaArgValue(val));
        }
        return args;
    }

    private Object parseGemmaArgValue(String token) {
        if (token == null) return null;
        if ("true".equals(token)) return Boolean.TRUE;
        if ("false".equals(token)) return Boolean.FALSE;
        if ("null".equals(token)) return null;
        if ((token.startsWith("\"") && token.endsWith("\""))
                || (token.startsWith("'") && token.endsWith("'"))) {
            return token.substring(1, token.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\'", "'")
                    .replace("\\\\", "\\");
        }
        try {
            if (token.contains(".")) return Double.parseDouble(token);
            return Long.parseLong(token);
        } catch (NumberFormatException e) {
            return token;
        }
    }

    private String jsonOrEmpty(Object value) {
        try {
            return assistantObjectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String summarizeResult(Map<String, Object> result) {
        if (result == null) return "";
        if (result.containsKey("error")) return "greska: " + result.get("error");
        if (result.containsKey("results")) {
            Object r = result.get("results");
            int n = r instanceof List<?> list ? list.size() : 0;
            return n + " rezultata";
        }
        if (result.containsKey("summary")) return "sazetak vracen";
        if (result.containsKey("accountCount")) return result.get("accountCount") + " racuna";
        if (result.containsKey("orderCount")) return result.get("orderCount") + " ordera";
        if (result.containsKey("rate")) return "kurs " + result.get("rate");
        if (result.containsKey("expression"))
            return result.get("expression") + " = " + result.get("result");
        return "ok";
    }

    private void emitSourceIfApplicable(SseEmitter emitter, String toolName, Map<String, Object> result) {
        if (result == null) return;
        if (toolName.startsWith("wikipedia_")) {
            Object title = result.get("title");
            if (title != null) {
                SseEvents.source(emitter, "wikipedia", String.valueOf(title), null);
            }
        } else if ("rag_search_spec".equals(toolName)) {
            Object resultsObj = result.get("results");
            if (resultsObj instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Map<?, ?> m) {
                    Object src = m.get("source");
                    Object cel = m.get("celina");
                    String title = "Banka 2 spec — Celina " + (cel != null ? cel : "?");
                    if (src != null) title += " (" + src + ")";
                    SseEvents.source(emitter, "spec", title, null);
                }
            }
        }
    }

    /* ============================== THINKING PARSER ============================== */

    private record ContentParts(String cleanContent, int thinkingChars) {}

    /**
     * Razdvaja Gemma 4 thinking blok od finalnog odgovora.
     * Markeri: {@code <|channel>thought ... <channel|>} ili {@code <think>...</think>}
     * (razlicite verzije Gemma porodice koriste oba).
     */
    private ContentParts stripThinking(String raw) {
        if (raw == null || raw.isEmpty()) return new ContentParts("", 0);
        StringBuilder thinking = new StringBuilder();
        StringBuilder clean = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            int channelStart = raw.indexOf("<|channel>thought", i);
            int thinkStart = raw.indexOf("<think>", i);
            int start = minPositive(channelStart, thinkStart);
            if (start < 0) {
                clean.append(raw, i, raw.length());
                break;
            }
            clean.append(raw, i, start);
            int end;
            if (start == channelStart) {
                end = raw.indexOf("<channel|>", start);
                if (end < 0) { thinking.append(raw, start, raw.length()); break; }
                thinking.append(raw, start, end + "<channel|>".length());
                i = end + "<channel|>".length();
            } else {
                end = raw.indexOf("</think>", start);
                if (end < 0) { thinking.append(raw, start, raw.length()); break; }
                thinking.append(raw, start, end + "</think>".length());
                i = end + "</think>".length();
            }
        }
        return new ContentParts(clean.toString().trim(), thinking.length());
    }

    private static int minPositive(int a, int b) {
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    /* ============================== STREAMING (Phase 2) ============================== */

    /**
     * Optional zapravo-streaming staza (jos ne pozivamo iz {@link #runChat}).
     * Trenutno koristimo non-stream + chunked emit za jednostavnost; ako zelimo
     * da pravo stream-ujemo iz LLM-a, prebacicemo na ovu metodu.
     */
    @SuppressWarnings("unused")
    private void streamFinalAnswer(List<OpenAiMessage> messages, SseEmitter emitter,
                                    AtomicInteger tokenCount) {
        StringBuilder buffer = new StringBuilder();
        llmHttpClient.chatStream(buildRequest(messages, null, true), chunk -> {
            for (OpenAiChatChunk.Choice c : chunk.choices()) {
                if (c.delta() != null && c.delta().content() != null) {
                    buffer.append(c.delta().content());
                    SseEvents.token(emitter, c.delta().content());
                    tokenCount.incrementAndGet();
                }
            }
        });
    }

    /* ============================== UNUSED IMPORTS GUARD ============================== */
    // (Drzi LinkedHashMap import in scope za ako kasnije zatreba u tool spec building-u.)
    @SuppressWarnings("unused")
    private static final Map<String, Object> __KEEP_LINKED = new LinkedHashMap<>();
}
