package rs.raf.banka2_bek.interbank.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class InterbankConfig {

    @Bean(name = "interbankObjectMapper")
    @Primary
    @SuppressWarnings("deprecation") // WRITE_BIGDECIMAL_AS_PLAIN i dalje radi (replacement tek u Jackson 3.0)
    public ObjectMapper interbankObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // §2.5 — MonetaryValue.amount mora biti BigDecimal, ne float/double.
                // USE_BIG_DECIMAL_FOR_FLOATS prevodi sve JSON brojeve sa decimalom u BigDecimal
                // (nikad u Double). WRITE_BIGDECIMAL_AS_PLAIN sprecava scientific notaciju
                // tipa "1.5E2" — uvek "150.00".
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    @Bean
    public RestClient interbankRestClient(){
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // §2.9 retry semantika — read timeout veci od 10s da nam ne timeout-uje
        // sinhroni §3.6 GET /accept dok partner zavrsava 2PC interno.
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
