package rs.raf.banka2_bek.assistant.wizard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;
import rs.raf.banka2_bek.assistant.dto.openai.OpenAiChatRequest;
import rs.raf.banka2_bek.assistant.dto.openai.OpenAiChatResponse;
import rs.raf.banka2_bek.assistant.dto.openai.OpenAiMessage;
import rs.raf.banka2_bek.assistant.dto.openai.OpenAiToolCall;
import rs.raf.banka2_bek.assistant.tool.client.LlmHttpClient;
import rs.raf.banka2_bek.assistant.wizard.registry.WizardRegistry;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserRole;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IntentClassifierTest {

    @Mock private LlmHttpClient llmHttpClient;
    @Mock private WizardRegistry wizardRegistry;

    private AssistantProperties properties;
    private IntentClassifier classifier;
    private UserContext clientUser;

    @BeforeEach
    void setUp() {
        properties = new AssistantProperties();
        properties.setModel("test-model");
        properties.setTopK(64);
        ObjectMapper mapper = new ObjectMapper();
        classifier = new IntentClassifier(llmHttpClient, properties, mapper, wizardRegistry);
        clientUser = new UserContext(1L, UserRole.CLIENT);
        // Default: svi tools u registry-ju "imaju" wizard template, tako da
        // buildMinimalTools generise non-empty listu i LLM se zaista pozove.
        // Pojedinacni testovi mogu override-ovati za negativne slucajeve.
        when(wizardRegistry.has(anyString())).thenReturn(true);
    }

    private OpenAiChatResponse buildToolCallResponse(String toolName) {
        OpenAiToolCall toolCall = new OpenAiToolCall(
                "call_test", "function",
                new OpenAiToolCall.Function(toolName, "{}")
        );
        OpenAiMessage msg = new OpenAiMessage(
                "assistant", "", List.of(toolCall), null, null, null, null
        );
        OpenAiChatResponse.Choice choice = new OpenAiChatResponse.Choice(0, msg, "tool_calls");
        return new OpenAiChatResponse("test-id", "test-model", List.of(choice), null);
    }

    private OpenAiChatResponse buildContentResponse(String content) {
        OpenAiMessage msg = new OpenAiMessage(
                "assistant", content, null, null, null, null, null
        );
        OpenAiChatResponse.Choice choice = new OpenAiChatResponse.Choice(0, msg, "stop");
        return new OpenAiChatResponse("test-id", "test-model", List.of(choice), null);
    }

    @Test
    void classify_returnsToolName_fromNativeToolCalls() {
        when(wizardRegistry.has("create_payment")).thenReturn(true);
        when(llmHttpClient.chatNonStream(any(OpenAiChatRequest.class)))
                .thenReturn(buildToolCallResponse("create_payment"));

        Optional<String> result = classifier.classify("plati Milici 100 RSD", clientUser);

        assertThat(result).isPresent().contains("create_payment");
    }

    @Test
    void classify_returnsEmpty_forNullOrBlankInput() {
        assertThat(classifier.classify(null, clientUser)).isEmpty();
        assertThat(classifier.classify("", clientUser)).isEmpty();
        assertThat(classifier.classify("   ", clientUser)).isEmpty();
        verify(llmHttpClient, never()).chatNonStream(any());
    }

    @Test
    void classify_returnsEmpty_whenNoToolCalls() {
        when(wizardRegistry.has(any())).thenReturn(true);
        when(llmHttpClient.chatNonStream(any(OpenAiChatRequest.class)))
                .thenReturn(buildContentResponse("Razmisljam o tome..."));

        Optional<String> result = classifier.classify("nesto neodlucno", clientUser);

        assertThat(result).isEmpty();
    }

    @Test
    void classify_returnsEmpty_whenToolNotInRegistry() {
        // Default vraca true (vidi setUp), override-ujemo za pickovan name
        when(wizardRegistry.has("nonexistent_tool")).thenReturn(false);
        when(llmHttpClient.chatNonStream(any(OpenAiChatRequest.class)))
                .thenReturn(buildToolCallResponse("nonexistent_tool"));

        Optional<String> result = classifier.classify("kupi nesto", clientUser);

        assertThat(result).isEmpty();
    }

    @Test
    void classify_parsesGemmaToolCodeFormat() {
        // Gemma 4 sometimes emits [tool_code]name(args)[/tool_code] as text
        when(wizardRegistry.has("block_card")).thenReturn(true);
        when(llmHttpClient.chatNonStream(any(OpenAiChatRequest.class)))
                .thenReturn(buildContentResponse("[tool_code]block_card(cardId=1)[/tool_code]"));

        Optional<String> result = classifier.classify("blokiraj karticu", clientUser);

        assertThat(result).isPresent().contains("block_card");
    }

    @Test
    void classify_parsesGemmaBracketFormat() {
        // Gemma 4 alternative: [name(args)]
        when(wizardRegistry.has("create_buy_order")).thenReturn(true);
        when(llmHttpClient.chatNonStream(any(OpenAiChatRequest.class)))
                .thenReturn(buildContentResponse("[create_buy_order(ticker=AAPL)]"));

        Optional<String> result = classifier.classify("kupi 5 AAPL", clientUser);

        assertThat(result).isPresent().contains("create_buy_order");
    }

    @Test
    void classify_cachesResults_secondCallSkipsLlm() {
        when(wizardRegistry.has("create_payment")).thenReturn(true);
        when(llmHttpClient.chatNonStream(any(OpenAiChatRequest.class)))
                .thenReturn(buildToolCallResponse("create_payment"));

        Optional<String> first = classifier.classify("plati Milici 100", clientUser);
        Optional<String> second = classifier.classify("plati Milici 100", clientUser);

        assertThat(first).isPresent().contains("create_payment");
        assertThat(second).isPresent().contains("create_payment");
        // LLM called only once thanks to LRU cache
        verify(llmHttpClient, times(1)).chatNonStream(any());
    }

    @Test
    void classify_returnsEmpty_whenLlmThrows() {
        when(llmHttpClient.chatNonStream(any())).thenThrow(new RuntimeException("network down"));

        Optional<String> result = classifier.classify("kupi AAPL", clientUser);

        assertThat(result).isEmpty();
    }

    @Test
    void classify_isCaseInsensitiveForCacheKey() {
        when(wizardRegistry.has("create_payment")).thenReturn(true);
        when(llmHttpClient.chatNonStream(any(OpenAiChatRequest.class)))
                .thenReturn(buildToolCallResponse("create_payment"));

        classifier.classify("Plati Milici 100", clientUser);
        classifier.classify("plati milici 100", clientUser);

        verify(llmHttpClient, times(1)).chatNonStream(any());
    }
}
