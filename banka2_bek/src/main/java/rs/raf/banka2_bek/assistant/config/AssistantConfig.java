package rs.raf.banka2_bek.assistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bean providers za Arbitro asistent.
 *
 * Razlozi za nazvane (named) bean-ove:
 * <ul>
 *   <li>{@code assistantTaskExecutor} — Spring auto-config kreira
 *       {@code applicationTaskExecutor} I {@code taskScheduler}, oba
 *       implementiraju {@link TaskExecutor} → autowire by type je
 *       ambiguous. Pravimo svoj namenski pool.</li>
 *   <li>{@code assistantObjectMapper} — neki integration testovi rade sa
 *       {@code @WebMvcTest} ili custom konfiguracijom koja iskljucuje
 *       Jackson auto-config. Pravljenjem zasebnog bean-a osiguravamo da
 *       Arbitro ima ObjectMapper i u tim test kontekstima.</li>
 * </ul>
 *
 * <p>NAPOMENA: Arbitro HTTP klijenti (LlmHttpClient, WikipediaToolClient,
 * RagToolClient) NE koriste Spring RestClient. Ranije pokusana implementacija
 * sa RestClient + custom HttpMessageConverter-ima nije pouzdano serijalizovala
 * body preko Docker network-a (FastAPI je primao "input: null" 422 greske).
 * Klijenti su prebaceni na {@link java.net.http.HttpClient} — low-level Java 11+
 * API koji deterministicki salje string body preko zice.
 */
@Configuration
public class AssistantConfig {

    @Bean(name = "assistantTaskExecutor")
    public TaskExecutor assistantTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("arbitro-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Bean(name = "assistantObjectMapper")
    public ObjectMapper assistantObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
