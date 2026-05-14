package rs.raf.banka2_bek.timeseries;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Konfiguracija InfluxDB klijenta za time-series berzanske podatke.
 * <p>
 * Aktivira se sa {@code banka2.influx.enabled=true}; default je false da
 * BE moze startovati i bez InfluxDB stack-a (graceful degradation —
 * sve berzanske operacije rade i bez InfluxDB-a, samo nemaju time-series
 * persistence za chart).
 */
@Configuration
@ConditionalOnProperty(name = "banka2.influx.enabled", havingValue = "true")
public class InfluxConfig {

    @Bean(destroyMethod = "close")
    public InfluxDBClient influxDBClient(
            @Value("${banka2.influx.url}") String url,
            @Value("${banka2.influx.token}") String token,
            @Value("${banka2.influx.org}") String org,
            @Value("${banka2.influx.bucket}") String bucket) {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
    }

    @Bean
    public WriteApiBlocking writeApi(InfluxDBClient client) {
        return client.getWriteApiBlocking();
    }
}
