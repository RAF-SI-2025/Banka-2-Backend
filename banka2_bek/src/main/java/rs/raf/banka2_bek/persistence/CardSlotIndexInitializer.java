package rs.raf.banka2_bek.persistence;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * P2.3 — kreira PARTIAL UNIQUE INDEX na cards tabeli posle Hibernate
 * schema migration-a.
 *
 * JPA {@code @Index} ne podrzava partial WHERE klauzulu, a custom DDL
 * je jedini portabilan nacin u PostgreSQL-u da se ogranici "max N
 * aktivnih kartica po (account, client) paru".
 *
 * Index garantuje da:
 *   * (account_id, client_id, card_slot) je jedinstven samo medju
 *     karticama koje nisu DEACTIVATED — ako klijent deaktivira karticu
 *     i napravi novu, slot moze biti reuse-ovan.
 *   * Drugi paralelni createCard ne moze da napravi karticu sa istim
 *     slotom za isti par — DB ce odbiti drugi insert sa duplicate-key
 *     greskom (race-condition prevencija nezavisno od service-level
 *     {@code checkCardLimit}).
 *
 * H2 (test profile) ne podrzava partial unique index sintaksu, pa
 * ce i tamo "preskociti" tihi try-catch.
 */
@Service
@ConditionalOnProperty(name = "banka2.indexes.card-slot.enabled",
        havingValue = "true", matchIfMissing = true)
public class CardSlotIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(CardSlotIndexInitializer.class);

    private static final String INDEX_NAME = "uk_cards_active_slot";
    private static final String CREATE_INDEX_SQL = """
            CREATE UNIQUE INDEX IF NOT EXISTS %s
            ON cards (account_id, client_id, card_slot)
            WHERE status <> 'DEACTIVATED'
            """.formatted(INDEX_NAME);

    private final JdbcTemplate jdbcTemplate;

    public CardSlotIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureSlotIndex() {
        try {
            jdbcTemplate.execute(CREATE_INDEX_SQL);
            log.info("P2.3: PARTIAL UNIQUE INDEX {} provided on cards", INDEX_NAME);
        } catch (Exception e) {
            // H2 (test) ne podrzava partial WHERE u CREATE UNIQUE INDEX-u —
            // i drugi failure modes (privilegije, tabela jos ne postoji) ne
            // smemo da prosledimo iz @PostConstruct jer rusimo app start.
            log.warn("Could not create {} (likely H2 test profile): {}",
                    INDEX_NAME, e.getMessage());
        }
    }
}
