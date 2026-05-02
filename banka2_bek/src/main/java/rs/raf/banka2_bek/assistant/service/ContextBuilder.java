package rs.raf.banka2_bek.assistant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.dto.PageContextDto;
import rs.raf.banka2_bek.assistant.dto.openai.OpenAiMessage;
import rs.raf.banka2_bek.assistant.model.AssistantConversation;
import rs.raf.banka2_bek.assistant.model.AssistantMessage;
import rs.raf.banka2_bek.assistant.model.AssistantMessageRole;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Spaja sve elemente u OpenAI {@code messages} array koji ide ka LLM-u.
 *
 * Redosled:
 * <ol>
 *   <li>System prompt (master + role + page + USER CONTEXT BLOCK)</li>
 *   <li>History — poslednjih N poruka iz konverzacije
 *       (filtrirano: thinking content se EXCLUDUJE — Gemma 4 best practice)</li>
 *   <li>Aktuelna user poruka</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private final PromptRegistry promptRegistry;
    private final UserResolver userResolver;
    private final ContextSanitizer sanitizer;
    private final ConversationSummarizer conversationSummarizer;

    public List<OpenAiMessage> build(UserContext user, PageContextDto page,
                                     AssistantConversation conversation,
                                     String userMessage, int historyWindow,
                                     boolean useReasoning) {
        return build(user, page, conversation, userMessage, historyWindow, useReasoning, null, false);
    }

    /**
     * Phase 5 multimodal overload — kad korisnik posalje audio/sliku, blob
     * stize kao base64 lista i ide u {@code images} polje user message-a
     * (Ollama Gemma 4 multimodal API).
     */
    public List<OpenAiMessage> build(UserContext user, PageContextDto page,
                                     AssistantConversation conversation,
                                     String userMessage, int historyWindow,
                                     boolean useReasoning,
                                     List<String> mediaBase64) {
        return build(user, page, conversation, userMessage, historyWindow, useReasoning, mediaBase64, false);
    }

    /**
     * Phase 4 v3.5 overload — sa agenticOn flagom koji ukljucuje AGENTIC_OVERLAY
     * u system prompt (eksplicitno trazi od modela da poziva write tools).
     */
    public List<OpenAiMessage> build(UserContext user, PageContextDto page,
                                     AssistantConversation conversation,
                                     String userMessage, int historyWindow,
                                     boolean useReasoning,
                                     List<String> mediaBase64,
                                     boolean agenticOn) {
        List<OpenAiMessage> messages = new ArrayList<>();

        String userName = userResolver.resolveName(user.userId(), user.userRole());
        String systemPrompt = promptRegistry.buildSystemPrompt(user, userName, page, useReasoning, agenticOn);
        messages.add(OpenAiMessage.system(systemPrompt));

        // Phase 5 — auto-summary za dugu konverzaciju. Ako je istorija duza
        // od threshold-a, generisemo (ili koristimo kesirani) sumar i dodajemo
        // ga kao additional system poruku PRE poslednjih historyWindow poruka.
        // Tako Gemma 4 E2B (mali model) ne gubi kontekst u dugim razgovorima.
        String summary = conversationSummarizer.maybeSummarize(conversation);
        if (summary != null && !summary.isBlank()) {
            messages.add(OpenAiMessage.system(
                    "REZIME PRETHODNOG RAZGOVORA (do skoro):\n" + summary
            ));
        }

        // History: poslednjih historyWindow poruka, sortirano po id ASC.
        // Ako postoji summary, history zapocinje od poruka koje SU posle
        // summary-jevog cutoff-a (vise nista nije starije nepokrivene).
        List<AssistantMessage> history = conversation.getMessages();
        if (history != null && !history.isEmpty()) {
            int from = Math.max(0, history.size() - historyWindow);
            for (int i = from; i < history.size(); i++) {
                AssistantMessage m = history.get(i);
                if (m.getRole() == AssistantMessageRole.SYSTEM) continue;
                messages.add(toOpenAi(m));
            }
        }

        // Trenutna user poruka — sa multimodal media-om ako postoji
        String sanitizedMsg = sanitizer.sanitize(userMessage);
        if (mediaBase64 != null && !mediaBase64.isEmpty()) {
            messages.add(OpenAiMessage.userWithMedia(sanitizedMsg, mediaBase64));
        } else {
            messages.add(OpenAiMessage.user(sanitizedMsg));
        }
        return messages;
    }

    private OpenAiMessage toOpenAi(AssistantMessage m) {
        String content = m.getContent() != null ? sanitizer.sanitize(m.getContent()) : null;
        return switch (m.getRole()) {
            case USER -> OpenAiMessage.user(content);
            case ASSISTANT -> OpenAiMessage.assistant(content);
            case TOOL -> OpenAiMessage.tool(m.getToolCallId(), content);
            case SYSTEM -> OpenAiMessage.system(content);
        };
    }

    /**
     * Heuristika iz plana §9.8 — prepende `<|think|>` na pocetak system promp-a
     * za slozenija pitanja. Trigger reci na srpskom + duzina poruke.
     */
    public boolean shouldUseReasoning(String userMessage) {
        if (userMessage == null) return false;
        if (userMessage.length() < 30) return false;
        String lower = userMessage.toLowerCase();
        String[] triggers = {"kako", "zasto", "objasni", "razlika", "proveri",
                "uporedi", "analiziraj", "savetuj", "pomozi", "izracunaj", "koliko"};
        for (String t : triggers) {
            if (lower.contains(t)) return true;
        }
        return userMessage.length() > 100;
    }

    public List<OpenAiMessage> emptyIfNull(List<OpenAiMessage> in) {
        return in == null ? Collections.emptyList() : in;
    }
}
