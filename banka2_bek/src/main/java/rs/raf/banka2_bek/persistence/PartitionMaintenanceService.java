package rs.raf.banka2_bek.persistence;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Odrzava mesecne particije na particionisanim tabelama.
 * <p>
 * Particionisane tabele su definisane Flyway migracijom kao
 * {@code PARTITION BY RANGE (timestamp_column)}. Ovaj servis kreira
 * buduce mesecne particije unapred (default 3 meseca) tako da INSERT-i
 * ne pucaju "no partition for value" greskom kad nastupi novi mesec.
 * <p>
 * Pored toga, opcionalno odbacuje (DETACH) stare particije starije od
 * konfigurisanog broja meseci. Detach-ovane tabele ostaju kao samostalne
 * tabele u bazi (tako da podaci nisu izgubljeni — mogu se arhivirati ili
 * obrisati ekspl.).
 * <p>
 * Bez {@code pg_partman} extension-a — koristi obican SQL i Spring
 * {@code @Scheduled}, sto je portabilno preko sve PostgreSQL instance.
 */
@Service
@ConditionalOnProperty(name = "banka2.partitions.enabled", havingValue = "true", matchIfMissing = true)
public class PartitionMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenanceService.class);
    private static final DateTimeFormatter PARTITION_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy_MM");

    /** Tabele koje su particionisane po mesecnoj kolumi. */
    private static final List<PartitionedTable> PARTITIONED_TABLES = List.of(
            new PartitionedTable("transactions", "timestamp"),
            new PartitionedTable("orders_p", "created_at"),
            new PartitionedTable("interbank_messages", "created_at")
    );

    /** Broj meseci unapred za koje kreiramo particije (uz tekuci mesec). */
    private static final int FUTURE_PARTITIONS = 3;

    private final JdbcTemplate jdbcTemplate;

    public PartitionMaintenanceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Pri pokretanju aplikacije proveri da postoje particije za tekuci i
     * sledeca {@code FUTURE_PARTITIONS} meseca. Bezbedno je pozvati vise
     * puta (CREATE IF NOT EXISTS pattern).
     */
    @PostConstruct
    public void ensurePartitionsOnStartup() {
        try {
            ensureUpcomingPartitions();
        } catch (Exception e) {
            // Ako particionisana tabela jos ne postoji (Flyway migracija nije
            // pokrenuta ili je iskljucena), ne ruzi pokretanje aplikacije.
            log.warn("Particije nisu kreirane na startup-u: {}", e.getMessage());
        }
    }

    /**
     * Cron job — svakog 25. u mesecu u 02:00 osigurava da particija za
     * sledeci mesec postoji. Vremenski razmak od ~5 dana je sigurnosna
     * margina ako cron failne ili je instanca down-ovana.
     */
    @Scheduled(cron = "0 0 2 25 * *")
    @Transactional
    public void scheduledPartitionCreation() {
        log.info("Cron: kreiranje particija za buduce mesece...");
        ensureUpcomingPartitions();
    }

    void ensureUpcomingPartitions() {
        YearMonth start = YearMonth.now();
        for (int offset = 0; offset <= FUTURE_PARTITIONS; offset++) {
            YearMonth target = start.plusMonths(offset);
            for (PartitionedTable table : PARTITIONED_TABLES) {
                createPartitionIfMissing(table, target);
            }
        }
    }

    private void createPartitionIfMissing(PartitionedTable table, YearMonth month) {
        String partitionName = "%s_%s".formatted(table.name, month.format(PARTITION_NAME_FORMAT));
        LocalDate from = month.atDay(1);
        LocalDate to = month.plusMonths(1).atDay(1);

        // PostgreSQL ne podrzava "CREATE TABLE IF NOT EXISTS ... PARTITION OF",
        // ali kreiranje vec postojece particije baca duplicate_table — hvatamo i ignorisemo.
        String sql = """
                CREATE TABLE IF NOT EXISTS %s
                PARTITION OF %s
                FOR VALUES FROM ('%s') TO ('%s');
                """.formatted(partitionName, table.name, from, to);

        try {
            jdbcTemplate.execute(sql);
            log.debug("Particija {} kreirana ili vec postoji.", partitionName);
        } catch (Exception e) {
            // Verovatno: tabela `table.name` nije particionisana (Flyway nije pokrenut).
            // Ne padaj — drugi tabele i dalje mogu da se kreiraju.
            log.warn("Ne mogu da kreiram particiju {}: {}", partitionName, e.getMessage());
        }
    }

    private record PartitionedTable(String name, String partitionColumn) {}
}
