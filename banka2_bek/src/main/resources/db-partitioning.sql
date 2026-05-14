-- ============================================================================
-- PostgreSQL particionisanje za visoko-volumne tabele.
-- ============================================================================
-- Pokrece se rucno (DBA operacija) jer migrira postojece tabele:
--   psql -h db -U banka2user -d banka2 -f db-partitioning.sql
--
-- Strategija: rename original tabele na *_legacy, kreiraj particionisanu sa
-- istom strukturom (PARTITION BY RANGE), kreiraj 3 mesecne particije
-- (proslost + tekuci + buducnost), prebaci podatke INSERT-om, drop legacy.
--
-- Hibernate ne razlikuje particionisanu od obicne tabele — radi normalno
-- kroz @Entity. Particije se odrzavaju iz `PartitionMaintenanceService`-a.
-- ============================================================================

BEGIN;

-- ─── transactions ────────────────────────────────────────────────────────
-- Particionisemo po `timestamp` koloni (default-uje na NOW() pri INSERT-u).
-- Particionisanje bezbedno zahteva da je particionisuca kolona u PRIMARY KEY-u.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
               WHERE c.relname = 'transactions' AND n.nspname = 'public'
               AND c.relkind = 'r')  -- 'r' = regular table; 'p' = partitioned
       AND NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                       WHERE c.relname = 'transactions' AND n.nspname = 'public'
                       AND c.relkind = 'p')
    THEN
        ALTER TABLE transactions RENAME TO transactions_legacy;

        EXECUTE format($f$
            CREATE TABLE transactions (LIKE transactions_legacy INCLUDING DEFAULTS INCLUDING CONSTRAINTS)
            PARTITION BY RANGE (timestamp);
        $f$);

        -- Pocetna particija pokriva sve podatke iz legacy tabele.
        EXECUTE format($f$
            CREATE TABLE transactions_legacy_archive
            PARTITION OF transactions
            FOR VALUES FROM (MINVALUE) TO ('%s-01');
        $f$, to_char(date_trunc('month', NOW()), 'YYYY-MM'));

        INSERT INTO transactions SELECT * FROM transactions_legacy;
        DROP TABLE transactions_legacy;
    END IF;
END $$;

-- ─── orders_p (orders je rezervisana rec u nekim PG verzijama) ───────────
-- JPA mapira na "orders" tabelu pod navodnicima. Particionisemo zasebnu kopiju
-- pod imenom orders_p (da bi entity mapping ostao netaknut, koristimo VIEW).

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
               WHERE c.relname = 'orders' AND n.nspname = 'public'
               AND c.relkind = 'r')
    THEN
        -- Kreiraj particionisanu mirror tabelu.
        EXECUTE format($f$
            CREATE TABLE IF NOT EXISTS orders_p
            (LIKE orders INCLUDING DEFAULTS INCLUDING CONSTRAINTS)
            PARTITION BY RANGE (created_at);
        $f$);

        -- Inicijalna pocetna particija (sva istorija).
        EXECUTE format($f$
            CREATE TABLE IF NOT EXISTS orders_p_archive
            PARTITION OF orders_p
            FOR VALUES FROM (MINVALUE) TO ('%s-01');
        $f$, to_char(date_trunc('month', NOW()), 'YYYY-MM'));
    END IF;
END $$;

-- ─── interbank_messages ──────────────────────────────────────────────────
-- Audit log za inter-bank protokol. Particionisemo po created_at — high-volume
-- tabela u produkciji (svaki 2PC ciklus = 3 message-a).

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
               WHERE c.relname = 'interbank_messages' AND n.nspname = 'public'
               AND c.relkind = 'r')
    THEN
        ALTER TABLE interbank_messages RENAME TO interbank_messages_legacy;

        EXECUTE format($f$
            CREATE TABLE interbank_messages (LIKE interbank_messages_legacy INCLUDING DEFAULTS INCLUDING CONSTRAINTS)
            PARTITION BY RANGE (created_at);
        $f$);

        EXECUTE format($f$
            CREATE TABLE interbank_messages_archive
            PARTITION OF interbank_messages
            FOR VALUES FROM (MINVALUE) TO ('%s-01');
        $f$, to_char(date_trunc('month', NOW()), 'YYYY-MM'));

        INSERT INTO interbank_messages SELECT * FROM interbank_messages_legacy;
        DROP TABLE interbank_messages_legacy;
    END IF;
END $$;

-- ─── Kreiraj mesecne particije za tekuci + sledeca 3 meseca ──────────────
-- (PartitionMaintenanceService ovo radi automatski na startup-u i kroz cron,
-- ali ovde ih kreiramo eksplicitno za inicijalni setup.)

DO $$
DECLARE
    target_month date;
    partition_suffix text;
    partition_from date;
    partition_to date;
    base_table text;
BEGIN
    FOR offset_months IN 0..3 LOOP
        target_month := date_trunc('month', NOW())::date + (offset_months || ' months')::interval;
        partition_from := target_month;
        partition_to := target_month + INTERVAL '1 month';
        partition_suffix := to_char(target_month, 'YYYY_MM');

        FOR base_table IN SELECT unnest(ARRAY['transactions', 'orders_p', 'interbank_messages']) LOOP
            IF EXISTS (SELECT 1 FROM pg_class WHERE relname = base_table AND relkind = 'p') THEN
                EXECUTE format(
                    'CREATE TABLE IF NOT EXISTS %I_%s PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
                    base_table, partition_suffix, base_table, partition_from, partition_to
                );
            END IF;
        END LOOP;
    END LOOP;
END $$;

COMMIT;

-- ─── Provera: lista particija ────────────────────────────────────────────
-- SELECT
--     parent.relname AS parent_table,
--     child.relname AS partition,
--     pg_get_expr(child.relpartbound, child.oid) AS bound
-- FROM pg_inherits
-- JOIN pg_class parent ON parent.oid = pg_inherits.inhparent
-- JOIN pg_class child  ON child.oid  = pg_inherits.inhrelid
-- WHERE parent.relname IN ('transactions', 'orders_p', 'interbank_messages')
-- ORDER BY parent.relname, child.relname;
