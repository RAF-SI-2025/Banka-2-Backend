package rs.raf.banka2_bek.interbank.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * T5 – Spring konfiguracija za inter-bank HTTP klijent.
 *
 * Registruje:
 *  - ObjectMapper sa JavaTimeModule (za LocalDateTime serijalizaciju)
 *  - RestClient sa timeout konfiguracijom
 */
@Configuration
public class InterbankConfig {

    @Value("${interbank.client.timeout-seconds:10}")
    private int timeoutSeconds;

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

}