package rs.raf.banka2_bek.actuary.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActuaryLimitResetSchedulerTest {

    @Mock
    private ActuaryInfoRepository actuaryInfoRepository;

    @InjectMocks
    private ActuaryLimitResetScheduler scheduler;

    @Nested
    @DisplayName("resetDailyLimits")
    class ResetDailyLimits {

        @Test
        @DisplayName("calls resetAllUsedLimits on the repository exactly once")
        void callsResetAllUsedLimitsOnce() {
            when(actuaryInfoRepository.resetAllUsedLimits()).thenReturn(10);

            scheduler.resetDailyLimits();

            verify(actuaryInfoRepository, times(1)).resetAllUsedLimits();
        }

        @Test
        @DisplayName("works when zero actuaries are reset")
        void worksWhenZeroActuariesReset() {
            when(actuaryInfoRepository.resetAllUsedLimits()).thenReturn(0);

            scheduler.resetDailyLimits();

            verify(actuaryInfoRepository, times(1)).resetAllUsedLimits();
        }

        @Test
        @DisplayName("works when many actuaries are reset")
        void worksWhenManyActuariesReset() {
            when(actuaryInfoRepository.resetAllUsedLimits()).thenReturn(200);

            scheduler.resetDailyLimits();

            verify(actuaryInfoRepository, times(1)).resetAllUsedLimits();
        }

        @Test
        @DisplayName("does not propagate exception from repository")
        void doesNotPropagateException() {
            when(actuaryInfoRepository.resetAllUsedLimits())
                    .thenThrow(new RuntimeException("DB error"));

            assertThatNoException().isThrownBy(() -> scheduler.resetDailyLimits());

            verify(actuaryInfoRepository, times(1)).resetAllUsedLimits();
        }

        @Test
        @DisplayName("catches all exception subtypes")
        void catchesAllExceptionSubtypes() {
            when(actuaryInfoRepository.resetAllUsedLimits())
                    .thenThrow(new IllegalStateException("illegal state"));

            assertThatNoException().isThrownBy(() -> scheduler.resetDailyLimits());
        }
    }
}
