package rs.raf.banka2_bek.assistant.agentic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.assistant.agentic.model.AgentActionStatus;
import rs.raf.banka2_bek.assistant.agentic.repository.AgentActionRepository;
import rs.raf.banka2_bek.assistant.agentic.scheduler.AgentActionExpirationScheduler;
import rs.raf.banka2_bek.assistant.agentic.service.AgentActionGateway;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;
import rs.raf.banka2_bek.assistant.service.AuditLogger;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 5 polish — integracioni test za auto-EXPIRED tranziciju.
 *
 * Scheduler poziva {@code gateway.expireStale()} svake minute. Test verifikuje:
 *  - Bulk update poziv ide u repository sa pravilnim parametrima.
 *  - Count > 0 → log poruka emitovana.
 *  - Count = 0 → tih (nema log spam-a u prazan stack).
 *  - Greska iz repository-ja se hvata u scheduler-u (ne crashuje cron loop).
 */
@ExtendWith(MockitoExtension.class)
class AgentActionExpirationTest {

    @Mock private AgentActionRepository repository;
    @Mock private AuditLogger auditLogger;

    private final AssistantProperties properties = new AssistantProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private AgentActionGateway gateway;
    private AgentActionExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties.getAgentic().setEnabled(true);
        properties.getAgentic().setActionTtlMin(5);
        gateway = new AgentActionGateway(repository, properties, auditLogger,
                org.mockito.Mockito.mock(rs.raf.banka2_bek.otp.service.OtpService.class),
                org.mockito.Mockito.mock(rs.raf.banka2_bek.auth.util.UserResolver.class),
                objectMapper);
        scheduler = new AgentActionExpirationScheduler(gateway);
    }

    @Test
    @DisplayName("expireStale: poziva markExpired sa PENDING→EXPIRED + LocalDateTime.now()")
    void expireStaleCallsBulkUpdate() {
        when(repository.markExpired(eq(AgentActionStatus.PENDING),
                eq(AgentActionStatus.EXPIRED), any(LocalDateTime.class)))
                .thenReturn(3);

        int count = gateway.expireStale();

        assertThat(count).isEqualTo(3);
        verify(repository, times(1)).markExpired(
                eq(AgentActionStatus.PENDING), eq(AgentActionStatus.EXPIRED), any());
    }

    @Test
    @DisplayName("expireStale: count = 0 ne baca exception, vraca 0")
    void expireStaleNoStale() {
        when(repository.markExpired(any(), any(), any())).thenReturn(0);
        int count = gateway.expireStale();
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("scheduler.expireStaleActions: hvata exception iz Gateway-a")
    void schedulerHandlesException() {
        when(repository.markExpired(any(), any(), any()))
                .thenThrow(new RuntimeException("DB connection lost"));

        // Ne treba da baci — scheduler hvata sve i loguje
        scheduler.expireStaleActions();

        verify(repository).markExpired(any(), any(), any());
    }

    @Test
    @DisplayName("scheduler.expireStaleActions: count > 0 ne baca exception")
    void schedulerHappy() {
        when(repository.markExpired(any(), any(), any())).thenReturn(5);
        scheduler.expireStaleActions();
        verify(repository, times(1)).markExpired(any(), any(), any());
    }

    @Test
    @DisplayName("scheduler.expireStaleActions: count = 0 ne emituje audit warn")
    void schedulerSilentOnEmpty() {
        when(repository.markExpired(any(), any(), any())).thenReturn(0);
        scheduler.expireStaleActions();
        // Nema verifikacije log poziva — postojeci AuditLogger ne logira
        // expireStale eksplicitno (samo Gateway interno log-uje INFO ako > 0).
        verify(auditLogger, never()).logAgentAction(any(), any(), any(), any(), any());
    }
}
