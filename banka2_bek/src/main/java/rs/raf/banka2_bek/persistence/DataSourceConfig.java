package rs.raf.banka2_bek.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Read/write splitting konfiguracija.
 * <p>
 * Aplikacija pokrece odvojene HikariCP pool-ove ka primary i read replica
 * instancama PostgreSQL-a. {@code AbstractRoutingDataSource} pri svakom
 * zahtevu za konekciju cita {@code TransactionSynchronizationManager
 * .isCurrentTransactionReadOnly()} flag — vraca primary za default ili
 * write transakcije, replica za {@code @Transactional(readOnly = true)}.
 * <p>
 * {@link LazyConnectionDataSourceProxy} je kriticno: bez njega Spring otvara
 * konekciju u trenutku kad transakcioni framework prvi put trazi connection,
 * sto je PRE nego sto je {@code readOnly} flag postavljen u
 * TransactionSynchronizationManager. Lazy proxy odlaze otvaranje konekcije
 * do prvog statement-a, kad je flag vec dostupan.
 * <p>
 * Konfiguracija se aktivira sa {@code banka2.datasource.replica.enabled=true}
 * (default-uje na false — kad nema replike u stack-u, sve ide na primary).
 */
@Configuration
@ConditionalOnProperty(name = "banka2.datasource.replica.enabled", havingValue = "true")
public class DataSourceConfig {

    /** Marker za RoutingDataSource lookup map. */
    public enum DataSourceRole {
        PRIMARY,
        REPLICA
    }

    @Bean
    public DataSource primaryDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("banka2-primary-pool");
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        return new HikariDataSource(config);
    }

    @Bean
    public DataSource replicaDataSource(
            @Value("${banka2.datasource.replica.url}") String url,
            @Value("${banka2.datasource.replica.username}") String username,
            @Value("${banka2.datasource.replica.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("banka2-replica-pool");
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        // Read replica je u recovery mode-u — sve transakcije moraju biti read-only,
        // postavljamo Hikari da to enforce-uje.
        config.setReadOnly(true);
        return new HikariDataSource(config);
    }

    /**
     * Routing DataSource — bira primary ili replica na osnovu Spring
     * transaction read-only flag-a. {@code @Primary} jer Spring Boot mora da
     * dobije BAS ovaj kao default (ne primaryDataSource direktno).
     */
    @Bean
    @Primary
    public DataSource routingDataSource(DataSource primaryDataSource, DataSource replicaDataSource) {
        Map<Object, Object> targets = new HashMap<>();
        targets.put(DataSourceRole.PRIMARY, primaryDataSource);
        targets.put(DataSourceRole.REPLICA, replicaDataSource);

        AbstractRoutingDataSource routing = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
                return readOnly ? DataSourceRole.REPLICA : DataSourceRole.PRIMARY;
            }
        };
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(primaryDataSource);
        routing.afterPropertiesSet();

        // Wrap u LazyConnectionDataSourceProxy — bez ovog se konekcija otvara
        // pre nego sto je readOnly flag postavljen u TransactionSynchronizationManager,
        // pa bi `determineCurrentLookupKey` uvek vracao PRIMARY.
        return new LazyConnectionDataSourceProxy(routing);
    }
}
