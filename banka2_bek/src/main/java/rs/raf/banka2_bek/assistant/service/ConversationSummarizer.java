package rs.raf.banka2_bek.assistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;
import rs.raf.banka2_bek.assistant.dto.openai.OpenAiChatRequest;
import rs.raf.banka2_bek.assistant.dto.openai.OpenAiChatResponse;
import rs.raf.banka2_bek.assistant.dto.openai.OpenAiMessage;
import rs.raf.banka2_bek.assistant.model.AssistantConversation;
import rs.raf.banka2_bek.assistant.model.AssistantMessage;
import rs.raf.banka2_bek.assistant.model.AssistantMessageRole;
import rs.raf.banka2_bek.assistant.repository.AssistantConversationRepository;
import rs.raf.banka2_bek.assistant.tool.client.LlmHttpClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 5 — auto-summary generator za dugu konverzaciju.
 *
 * Kad istorija predje {@code summaryTriggerThreshold} poruka, ovaj servis
 * pravi LLM poziv ("Sumiraj sledecu konverzaciju u 2-3 recenice") koji se
 * onda koristi kao zamena za literally history u sledecim chat pozivima.
 *
 * Razlog: Gemma 4 E2B (mali 2B effective param model) pati od koherencije
 * u dugim razgovorima jer kontekst lako prevazidje window. Summary sazima
 * "ono sto je bilo" u 200-400 tokens umesto 4000-8000.
 *
 * Trigger: ContextBuilder pita ovaj servis pre buildovanja messages array-a;
 * ako vec postoji summary i <historyWindowSize> novih poruka, koristi summary
 * + samo novije poruke.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConversationSummarizer {

    private final AssistantProperties properties;
    private final LlmHttpClient llmHttpClient;
    private final AssistantConversationRepository conversationRepository;

    /**
     * Generise summary za konverzaciju ako je threshold premasen i jos nije
     * bilo summary-ja, ili je postojeci summary stareji od trenutnog
     * messages window-a.
     *
     * @return novi summary string ili null ako nije bilo potrebe za apdejtom.
     */
    public String maybeSummarize(AssistantConversation conv) {
        int threshold = properties.getSummaryTriggerThreshold();
        if (threshold <= 0) return null; // funkcionalnost iskljucena

        List<AssistantMessage> messages = conv.getMessages();
        if (messages == null || messages.size() < threshold) return null;

        // Ako vec imamo summary za sadasnje poruke i nema novih > windowSize, preskaci
        Long summaryUpTo = conv.getSummaryUpToMessageId();
        Long lastMsgId = messages.get(messages.size() - 1).getId();
        if (summaryUpTo != null && lastMsgId != null) {
            int newMessagesAfterSummary = (int) messages.stream()
                    .filter(m -> m.getId() != null && m.getId() > summaryUpTo)
                    .count();
            if (newMessagesAfterSummary < properties.getHistoryWindowSize()) {
                return conv.getSummary(); // jos nije vreme za novi update
            }
        }

        // Sastavi prompt: sve poruke sem poslednjih historyWindowSize
        int cutoff = Math.max(0, messages.size() - properties.getHistoryWindowSize());
        if (cutoff == 0) return null; // nema sta da rezimirate

        StringBuilder transcript = new StringBuilder();
        for (int i = 0; i < cutoff; i++) {
            AssistantMessage m = messages.get(i);
            if (m.getRole() == AssistantMessageRole.SYSTEM
                    || m.getRole() == AssistantMessageRole.TOOL) continue;
            transcript.append(m.getRole() == AssistantMessageRole.USER ? "User" : "Asistent")
                    .append(": ")
                    .append(m.getContent() == null ? "" : m.getContent().trim())
                    .append("\n");
        }
        if (transcript.length() == 0) return null;

        try {
            List<OpenAiMessage> summaryMessages = new ArrayList<>();
            summaryMessages.add(OpenAiMessage.system(
                    "Ti si asistent koji rezimira bankarske razgovore na srpskom (latinicom). " +
                    "U 3-5 recenica sazmi sledeci razgovor cuvajuci kljucne brojeve (iznose, " +
                    "broj racuna, ticker-e), korisnikove ciljeve, i akcije koje su izvrsene. " +
                    "Pisi sa stanovista treceg lica. Ne dodaji nista sto nije u razgovoru."
            ));
            summaryMessages.add(OpenAiMessage.user("RAZGOVOR:\n\n" + transcript));

            OpenAiChatRequest req = new OpenAiChatRequest(
                    properties.getModel(),
                    summaryMessages,
                    null, null, false,
                    0.3,  // niska temperatura za faktualan rezime
                    0.95,
                    64,
                    properties.getSummaryMaxTokens()
            );
            OpenAiChatResponse resp = llmHttpClient.chatNonStream(req);
            if (resp == null || resp.choices() == null || resp.choices().isEmpty()) {
                return conv.getSummary(); // zadrzi postojeci ako LLM ne odgovori
            }
            String text = resp.choices().get(0).message().effectiveContent();
            if (text == null || text.isBlank()) return conv.getSummary();

            // Persist
            conv.setSummary(text.trim());
            conv.setSummaryUpToMessageId(messages.get(cutoff - 1).getId());
            conversationRepository.save(conv);
            log.info("ARBITRO_SUMMARY conv={} chars={} upToMsgId={}",
                    conv.getConversationUuid(), text.length(),
                    conv.getSummaryUpToMessageId());
            return text;
        } catch (Exception e) {
            log.warn("Conversation summary failed, keeping previous: {}", e.getMessage());
            return conv.getSummary();
        }
    }
}
