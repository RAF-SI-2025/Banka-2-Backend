package rs.raf.banka2_bek.option.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import rs.raf.banka2_bek.option.service.OptionGeneratorService;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class OptionSchedulerIntegrationTest {

    @Autowired
    private OptionScheduler optionScheduler;

    @MockitoBean
    private OptionGeneratorService optionGeneratorService;

    @Test
    @DisplayName("dailyOptionMaintenance runs successfully in Spring context with empty DB")
    void dailyMaintenanceRunsInSpringContext() {
        assertThatNoException().isThrownBy(() -> optionScheduler.dailyOptionMaintenance());

        verify(optionGeneratorService, times(1)).generateAllOptions();
    }

    @Test
    @DisplayName("dailyOptionMaintenance does not fail when generator throws exception")
    void doesNotFailWhenGeneratorThrows() {
        doThrow(new RuntimeException("generation error"))
                .when(optionGeneratorService).generateAllOptions();

        assertThatNoException().isThrownBy(() -> optionScheduler.dailyOptionMaintenance());
    }
}
