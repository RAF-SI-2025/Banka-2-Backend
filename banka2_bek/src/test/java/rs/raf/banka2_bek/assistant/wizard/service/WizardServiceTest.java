package rs.raf.banka2_bek.assistant.wizard.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.assistant.agentic.dto.AgentActionPreviewDto;
import rs.raf.banka2_bek.assistant.agentic.service.AgentActionGateway;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.ToolRegistry;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.assistant.wizard.dto.SlotPromptDto;
import rs.raf.banka2_bek.assistant.wizard.dto.SlotSelectResponseDto;
import rs.raf.banka2_bek.assistant.wizard.model.SlotDefinition;
import rs.raf.banka2_bek.assistant.wizard.model.SlotOption;
import rs.raf.banka2_bek.assistant.wizard.model.SlotType;
import rs.raf.banka2_bek.assistant.wizard.model.WizardTemplate;
import rs.raf.banka2_bek.assistant.wizard.registry.WizardRegistry;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserRole;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WizardServiceTest {

    @Mock
    private WizardRegistry registry;

    @Mock
    private AgentActionGateway gateway;

    @Mock
    private ToolRegistry toolRegistry;

    @InjectMocks
    private WizardService service;

    private UserContext clientUser;
    private WriteToolHandler handlerStub;

    @BeforeEach
    void setUp() {
        clientUser = new UserContext(1L, UserRole.CLIENT);
        handlerStub = new WriteToolHandler() {
            @Override public String name() { return "test_tool"; }
            @Override public ToolDefinition definition() {
                return ToolDefinition.builder().name("test_tool").description("Test").build();
            }
            @Override public WriteToolHandler.PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
                return new WriteToolHandler.PreviewResult("preview", Map.of(), List.of());
            }
            @Override public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
                return Map.of("status", "OK");
            }
        };
    }

    private WizardTemplate buildTwoSlotTemplate() {
        return WizardTemplate.simple(
                "test_tool",
                "Test Wizard",
                List.of(
                        SlotDefinition.builder("color")
                                .prompt("Boja?")
                                .type(SlotType.CHOICE)
                                .options((u, f) -> List.of(
                                        new SlotOption("RED", "Crveno", null),
                                        new SlotOption("BLUE", "Plavo", null)
                                ))
                                .build(),
                        SlotDefinition.builder("size")
                                .prompt("Velicina?")
                                .type(SlotType.NUMBER)
                                .transform(s -> {
                                    try { return Integer.parseInt(s); }
                                    catch (NumberFormatException e) { return null; }
                                })
                                .validate(v -> v == null ? "Mora broj" : null)
                                .build()
                ),
                List.of("CLIENT")
        );
    }

    @Test
    void start_returnsFirstPrompt() {
        WizardTemplate tpl = buildTwoSlotTemplate();
        when(registry.get("test_tool")).thenReturn(Optional.of(tpl));

        Optional<SlotPromptDto> first = service.start("test_tool", clientUser, UUID.randomUUID(), "raw msg");

        assertThat(first).isPresent();
        SlotPromptDto p = first.get();
        assertThat(p.toolName()).isEqualTo("test_tool");
        assertThat(p.slotName()).isEqualTo("color");
        assertThat(p.type()).isEqualTo("CHOICE");
        assertThat(p.options()).hasSize(2);
        assertThat(p.stepIndex()).isEqualTo(1);
        assertThat(p.totalSteps()).isEqualTo(2);
    }

    @Test
    void start_returnsEmpty_whenTemplateMissing() {
        when(registry.get("nope")).thenReturn(Optional.empty());
        Optional<SlotPromptDto> first = service.start("nope", clientUser, UUID.randomUUID(), null);
        assertThat(first).isEmpty();
    }

    @Test
    void start_returnsEmpty_whenRoleNotAllowed() {
        WizardTemplate clientOnly = WizardTemplate.simple("emp_only", "Emp",
                List.of(SlotDefinition.builder("x").prompt("?").type(SlotType.TEXT).build()),
                List.of("EMPLOYEE"));
        when(registry.get("emp_only")).thenReturn(Optional.of(clientOnly));
        Optional<SlotPromptDto> first = service.start("emp_only", clientUser, UUID.randomUUID(), null);
        assertThat(first).isEmpty();
    }

    @Test
    void select_progressesToNextSlot() {
        WizardTemplate tpl = buildTwoSlotTemplate();
        when(registry.get("test_tool")).thenReturn(Optional.of(tpl));

        Optional<SlotPromptDto> first = service.start("test_tool", clientUser, UUID.randomUUID(), null);
        UUID wid = first.orElseThrow().wizardId();

        SlotSelectResponseDto resp = service.select(wid, "color", "RED", clientUser);

        assertThat(resp.status()).isEqualTo("AWAITING_NEXT_SLOT");
        assertThat(resp.nextSlot()).isNotNull();
        assertThat(resp.nextSlot().slotName()).isEqualTo("size");
        assertThat(resp.nextSlot().previousSelections()).hasSize(1);
        // Cosmetic fix verified: label "Crveno" instead of raw value "RED"
        assertThat(resp.nextSlot().previousSelections().get(0).label()).isEqualTo("Crveno");
    }

    @Test
    void select_returnsInvalid_onUnknownChoice() {
        WizardTemplate tpl = buildTwoSlotTemplate();
        when(registry.get("test_tool")).thenReturn(Optional.of(tpl));

        Optional<SlotPromptDto> first = service.start("test_tool", clientUser, UUID.randomUUID(), null);
        UUID wid = first.orElseThrow().wizardId();

        SlotSelectResponseDto resp = service.select(wid, "color", "GREEN", clientUser);

        assertThat(resp.status()).isEqualTo("INVALID");
        assertThat(resp.errorMessage()).isNotNull();
        // Wizard ostaje na istom slotu — re-prompt
        assertThat(resp.nextSlot().slotName()).isEqualTo("color");
    }

    @Test
    void select_completesWizard_dispatchesAgentAction() {
        WizardTemplate tpl = buildTwoSlotTemplate();
        when(registry.get("test_tool")).thenReturn(Optional.of(tpl));
        when(toolRegistry.get("test_tool")).thenReturn(Optional.of(handlerStub));
        AgentActionPreviewDto fakePreview = AgentActionPreviewDto.builder()
                .actionUuid("action-uuid")
                .tool("test_tool")
                .summary("preview")
                .parameters(Map.of("color", "RED", "size", 42))
                .warnings(List.of())
                .requiresOtp(false)
                .build();
        when(gateway.createPending(anyString(), anyString(), any(), any(), any())).thenReturn(fakePreview);

        Optional<SlotPromptDto> first = service.start("test_tool", clientUser, UUID.randomUUID(), null);
        UUID wid = first.orElseThrow().wizardId();
        service.select(wid, "color", "RED", clientUser);
        SlotSelectResponseDto resp = service.select(wid, "size", "42", clientUser);

        assertThat(resp.status()).isEqualTo("COMPLETED");
        assertThat(resp.actionPreview()).isNotNull();
        assertThat(resp.actionPreview().getActionUuid()).isEqualTo("action-uuid");
        verify(gateway).createPending(anyString(), eq("test_tool"), any(), eq(clientUser), eq(handlerStub));
    }

    @Test
    void select_returnsExpired_whenWizardIdUnknown() {
        SlotSelectResponseDto resp = service.select(UUID.randomUUID(), "x", "y", clientUser);
        assertThat(resp.status()).isEqualTo("EXPIRED");
    }

    @Test
    void select_returnsExpired_whenOwnershipMismatches() {
        WizardTemplate tpl = buildTwoSlotTemplate();
        when(registry.get("test_tool")).thenReturn(Optional.of(tpl));

        Optional<SlotPromptDto> first = service.start("test_tool", clientUser, UUID.randomUUID(), null);
        UUID wid = first.orElseThrow().wizardId();

        UserContext otherUser = new UserContext(99L, UserRole.CLIENT);
        SlotSelectResponseDto resp = service.select(wid, "color", "RED", otherUser);

        assertThat(resp.status()).isEqualTo("EXPIRED");
    }

    @Test
    void cancel_removesSession() {
        WizardTemplate tpl = buildTwoSlotTemplate();
        when(registry.get("test_tool")).thenReturn(Optional.of(tpl));

        Optional<SlotPromptDto> first = service.start("test_tool", clientUser, UUID.randomUUID(), null);
        UUID wid = first.orElseThrow().wizardId();

        boolean cancelled = service.cancel(wid, clientUser);
        assertThat(cancelled).isTrue();

        SlotSelectResponseDto afterCancel = service.select(wid, "color", "RED", clientUser);
        assertThat(afterCancel.status()).isEqualTo("EXPIRED");
    }

    @Test
    void cancel_returnsFalse_onWrongOwner() {
        WizardTemplate tpl = buildTwoSlotTemplate();
        when(registry.get("test_tool")).thenReturn(Optional.of(tpl));

        Optional<SlotPromptDto> first = service.start("test_tool", clientUser, UUID.randomUUID(), null);
        UUID wid = first.orElseThrow().wizardId();

        UserContext other = new UserContext(99L, UserRole.CLIENT);
        boolean cancelled = service.cancel(wid, other);
        assertThat(cancelled).isFalse();
    }

    @Test
    void totalSteps_excludesPrefilledSkippedSlots() {
        // Slot B ima prefill koji skipuje (vraca "" sentinel) na osnovu slota A
        WizardTemplate tpl = WizardTemplate.simple(
                "skip_test",
                "Skip Test",
                List.of(
                        SlotDefinition.builder("a")
                                .prompt("A?")
                                .type(SlotType.CHOICE)
                                .options((u, f) -> List.of(new SlotOption("ONLY_A", "Samo A", null)))
                                .build(),
                        SlotDefinition.builder("b")
                                .prompt("B?")
                                .type(SlotType.NUMBER)
                                .prefill((u, f) -> Optional.of((Object) ""))  // uvek skip
                                .build(),
                        SlotDefinition.builder("c")
                                .prompt("C?")
                                .type(SlotType.TEXT)
                                .build()
                ),
                List.of("CLIENT")
        );
        when(registry.get("skip_test")).thenReturn(Optional.of(tpl));

        Optional<SlotPromptDto> first = service.start("skip_test", clientUser, UUID.randomUUID(), null);

        assertThat(first).isPresent();
        // Slot B se preskace, total bi trebalo da bude 2 (A + C), ne 3
        assertThat(first.get().totalSteps()).isEqualTo(2);
    }
}
