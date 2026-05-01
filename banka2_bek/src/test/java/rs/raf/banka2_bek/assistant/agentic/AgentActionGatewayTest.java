package rs.raf.banka2_bek.assistant.agentic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.assistant.agentic.dto.AgentActionPreviewDto;
import rs.raf.banka2_bek.assistant.agentic.dto.ConfirmActionDto;
import rs.raf.banka2_bek.assistant.agentic.model.AgentAction;
import rs.raf.banka2_bek.assistant.agentic.model.AgentActionStatus;
import rs.raf.banka2_bek.assistant.agentic.repository.AgentActionRepository;
import rs.raf.banka2_bek.assistant.agentic.service.AgentActionGateway;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;
import rs.raf.banka2_bek.assistant.service.AuditLogger;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserRole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit testovi za {@link AgentActionGateway} — pokrivaju sve pun lifecycle
 * tranzicije (PENDING → EXECUTED, REJECTED, EXPIRED, FAILED) + idempotency
 * + rate limiting + ownership.
 *
 * Phase 4 v3.5 robust testing strategy.
 */
@ExtendWith(MockitoExtension.class)
class AgentActionGatewayTest {

    @Mock private AgentActionRepository repository;
    @Mock private AuditLogger auditLogger;

    private final AssistantProperties properties = new AssistantProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private AgentActionGateway gateway;

    private UserContext clientUser;
    private TestWriteHandler handler;

    @BeforeEach
    void setUp() {
        properties.getAgentic().setEnabled(true);
        properties.getAgentic().setActionTtlMin(5);
        properties.getAgentic().setRateLimitPerMin(5);
        // Phase 4.6: gateway sad sam verifikuje OTP. Za testove gde TestWriteHandler
        // requiresOtp()=true, OtpService.verify mock mora vratiti verified=true
        // ina ce gateway odbiti pre handler-a sa status FAILED.
        var otpServiceMock = org.mockito.Mockito.mock(rs.raf.banka2_bek.otp.service.OtpService.class);
        org.mockito.Mockito.lenient()
                .when(otpServiceMock.verify(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.Map.of("verified", true));
        // Mock SecurityContext da currentUserEmail() vrati validan email
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "test@example.com", "n/a", java.util.List.of()));
        gateway = new AgentActionGateway(repository, properties, auditLogger,
                otpServiceMock,
                org.mockito.Mockito.mock(rs.raf.banka2_bek.auth.util.UserResolver.class),
                objectMapper);
        clientUser = new UserContext(1L, UserRole.CLIENT);
        handler = new TestWriteHandler();
    }

    @Test
    @DisplayName("createPending: kreira AgentAction sa PENDING statusom + emit-uje preview")
    void createPending_happyPath() {
        when(repository.save(any(AgentAction.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentActionPreviewDto preview = gateway.createPending(
                "conv-1", "create_payment",
                Map.of("fromAccount", "222...111", "amount", 100),
                clientUser, handler);

        assertThat(preview.getActionUuid()).isNotBlank();
        assertThat(preview.getTool()).isEqualTo("create_payment");
        assertThat(preview.getSummary()).isEqualTo("test summary");
        assertThat(preview.isRequiresOtp()).isTrue();
        verify(repository).save(any(AgentAction.class));
    }

    @Test
    @DisplayName("createPending: throws AgenticDisabledException kad je agentic.enabled=false")
    void createPending_disabled() {
        properties.getAgentic().setEnabled(false);
        assertThatThrownBy(() -> gateway.createPending(
                "conv-1", "test", Map.of(), clientUser, handler))
                .isInstanceOf(AgentActionGateway.AgenticDisabledException.class);
    }

    @Test
    @DisplayName("createPending: throws AgenticRateLimitedException posle prevelikog broja akcija")
    void createPending_rateLimited() {
        properties.getAgentic().setRateLimitPerMin(2);
        when(repository.save(any(AgentAction.class))).thenAnswer(inv -> inv.getArgument(0));

        gateway.createPending("conv-1", "test", Map.of(), clientUser, handler);
        gateway.createPending("conv-1", "test", Map.of(), clientUser, handler);
        assertThatThrownBy(() -> gateway.createPending(
                "conv-1", "test", Map.of(), clientUser, handler))
                .isInstanceOf(AgentActionGateway.AgenticRateLimitedException.class);
    }

    @Test
    @DisplayName("confirm: prelaz PENDING → EXECUTED kad handler.executeFinal uspe")
    void confirm_executesSuccessfully() {
        AgentAction action = pendingAction();
        when(repository.findByActionUuid(action.getActionUuid())).thenReturn(Optional.of(action));
        when(repository.save(any(AgentAction.class))).thenAnswer(inv -> inv.getArgument(0));

        ConfirmActionDto request = new ConfirmActionDto("123456", null);
        Map<String, Object> result = gateway.confirm(
                action.getActionUuid(), clientUser, request,
                Map.of("create_payment", handler));

        assertThat(result.get("status")).isEqualTo("EXECUTED");
        assertThat(action.getStatus()).isEqualTo(AgentActionStatus.EXECUTED);
        assertThat(handler.executedWithOtp).isEqualTo("123456");
    }

    @Test
    @DisplayName("confirm: idempotent — dvostruki confirm vraca cached rezultat")
    void confirm_idempotent() {
        AgentAction action = pendingAction();
        action.setStatus(AgentActionStatus.EXECUTED);
        action.setExecutionResultJson("{\"ok\":true}");
        when(repository.findByActionUuid(action.getActionUuid())).thenReturn(Optional.of(action));

        Map<String, Object> result = gateway.confirm(
                action.getActionUuid(), clientUser, new ConfirmActionDto("xxx", null),
                Map.of("create_payment", handler));

        assertThat(result.get("status")).isEqualTo("EXECUTED");
        assertThat(result.get("cached")).isEqualTo(true);
        assertThat(handler.executedCount).isEqualTo(0); // nije izvrsen ponovo
    }

    @Test
    @DisplayName("confirm: ownership check — drugi user dobija EntityNotFoundException")
    void confirm_ownershipFailure() {
        AgentAction action = pendingAction();
        action.setUserId(99L); // drugi user
        when(repository.findByActionUuid(action.getActionUuid())).thenReturn(Optional.of(action));

        assertThatThrownBy(() -> gateway.confirm(
                action.getActionUuid(), clientUser, new ConfirmActionDto("123", null),
                Map.of("create_payment", handler)))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    @Test
    @DisplayName("confirm: requiresOtp=true + null otpCode → status FAILED bez izvrsenja")
    void confirm_missingOtp() {
        AgentAction action = pendingAction();
        when(repository.findByActionUuid(action.getActionUuid())).thenReturn(Optional.of(action));

        Map<String, Object> result = gateway.confirm(
                action.getActionUuid(), clientUser, null,
                Map.of("create_payment", handler));

        assertThat(result.get("status")).isEqualTo("FAILED");
        assertThat(handler.executedCount).isEqualTo(0);
    }

    @Test
    @DisplayName("confirm: handler runtime exception → FAILED status + errorMessage")
    void confirm_handlerThrows() {
        AgentAction action = pendingAction();
        when(repository.findByActionUuid(action.getActionUuid())).thenReturn(Optional.of(action));
        when(repository.save(any(AgentAction.class))).thenAnswer(inv -> inv.getArgument(0));
        handler.throwOnExecute = "Insufficient funds";

        Map<String, Object> result = gateway.confirm(
                action.getActionUuid(), clientUser, new ConfirmActionDto("123", null),
                Map.of("create_payment", handler));

        assertThat(result.get("status")).isEqualTo("FAILED");
        assertThat(action.getStatus()).isEqualTo(AgentActionStatus.FAILED);
        assertThat(action.getErrorMessage()).contains("Insufficient funds");
    }

    @Test
    @DisplayName("reject: PENDING → REJECTED + audit log")
    void reject_setsRejected() {
        AgentAction action = pendingAction();
        when(repository.findByActionUuid(action.getActionUuid())).thenReturn(Optional.of(action));
        when(repository.save(any(AgentAction.class))).thenAnswer(inv -> inv.getArgument(0));

        gateway.reject(action.getActionUuid(), clientUser);

        assertThat(action.getStatus()).isEqualTo(AgentActionStatus.REJECTED);
        verify(auditLogger).logAgentAction(clientUser, action.getActionUuid(),
                action.getToolName(), "REJECTED", null);
    }

    @Test
    @DisplayName("reject: idempotent — non-PENDING ne menja status")
    void reject_nonPending() {
        AgentAction action = pendingAction();
        action.setStatus(AgentActionStatus.EXECUTED);
        when(repository.findByActionUuid(action.getActionUuid())).thenReturn(Optional.of(action));

        gateway.reject(action.getActionUuid(), clientUser);

        assertThat(action.getStatus()).isEqualTo(AgentActionStatus.EXECUTED);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("expireStale: bulk update vrati count zarade EXPIRED akcija")
    void expireStale() {
        when(repository.markExpired(eq(AgentActionStatus.PENDING),
                eq(AgentActionStatus.EXPIRED), any())).thenReturn(3);

        int count = gateway.expireStale();
        assertThat(count).isEqualTo(3);
    }

    /* ========================== HELPERS ========================== */

    private AgentAction pendingAction() {
        return AgentAction.builder()
                .id(1L)
                .actionUuid("uuid-1")
                .conversationUuid("conv-1")
                .toolName("create_payment")
                .parametersJson("{\"amount\":100}")
                .summary("Placanje 100")
                .requiresOtp(true)
                .status(AgentActionStatus.PENDING)
                .userId(1L)
                .userRole(UserRole.CLIENT)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
    }

    private static <T> T eq(T value) { return org.mockito.ArgumentMatchers.eq(value); }

    /** Test double koji prati pozive bez stvarnog state mutacije. */
    private static class TestWriteHandler implements WriteToolHandler {
        int executedCount = 0;
        String executedWithOtp = null;
        String throwOnExecute = null;

        @Override public String name() { return "create_payment"; }
        @Override public boolean requiresOtp() { return true; }
        @Override public List<String> allowedRoles() { return List.of(); }
        @Override
        public rs.raf.banka2_bek.assistant.tool.ToolDefinition definition() {
            return rs.raf.banka2_bek.assistant.tool.ToolDefinition.builder()
                    .name(name()).description("test").build();
        }
        @Override
        public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
            return new PreviewResult("test summary", Map.of("Iznos", 100));
        }
        @Override
        public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
            executedCount++;
            executedWithOtp = otpCode;
            if (throwOnExecute != null) throw new RuntimeException(throwOnExecute);
            return Map.of("paymentId", 42L, "status", "COMPLETED");
        }
    }
}
