#!/bin/bash
# Bootstrap script za read replica kontejner.
# Pokrece se kao entrypoint (umesto standardnog postgres entrypoint-a).
# Pri prvom boot-u, prazan data direktorijum, koristi pg_basebackup da
# inicijalno klonira primary-jevu bazu, pa startuje postgres u standby mode-u.
# Kad data dir nije prazan (rerun), samo startuje postgres.

set -e

DATA_DIR="${PGDATA:-/var/lib/postgresql/data}"
PRIMARY_HOST="${PRIMARY_HOST:-db}"
PRIMARY_PORT="${PRIMARY_PORT:-5432}"
PRIMARY_DB="${PRIMARY_DB:-banka2}"
PRIMARY_USER="${PRIMARY_USER:-banka2user}"
PRIMARY_PASSWORD="${PRIMARY_PASSWORD:-banka2pass}"
REPLICATION_USER="${REPLICATION_USER:-replicator}"
REPLICATION_PASSWORD="${REPLICATION_PASSWORD:-replicator_pass}"
REPLICATION_SLOT="${REPLICATION_SLOT:-banka2_replica_slot}"

# Self-healing: ako je prethodni boot primary-ja pao na CRLF/bash^M problemu
# (ili je bilo koji razlog drugaciji da `01-replication-setup.sh` nije uspeo),
# replication user + slot ne postoje, pa pg_basebackup faila sa
#   "FATAL: role 'replicator' does not exist".
# Zato proveravamo i kreiramo ako nedostaju, koristeci primary admin kredencijale.
ensure_replication_setup() {
    echo "[replica-init] Provera replication user-a i slot-a na primary-ju..."

    # Cekaj primary da prihvata konekcije sa admin user-om.
    until PGPASSWORD="$PRIMARY_PASSWORD" pg_isready -h "$PRIMARY_HOST" -p "$PRIMARY_PORT" -U "$PRIMARY_USER" -d "$PRIMARY_DB"; do
        echo "[replica-init] Cekam primary admin konekciju..."
        sleep 2
    done

    PGPASSWORD="$PRIMARY_PASSWORD" psql -h "$PRIMARY_HOST" -p "$PRIMARY_PORT" -U "$PRIMARY_USER" -d "$PRIMARY_DB" -v ON_ERROR_STOP=1 <<-EOSQL
        DO \$\$
        BEGIN
            IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$REPLICATION_USER') THEN
                CREATE USER $REPLICATION_USER WITH REPLICATION ENCRYPTED PASSWORD '$REPLICATION_PASSWORD';
                RAISE NOTICE 'Kreiran replication user $REPLICATION_USER (samo-ozdravnja iz nepunog primary init-a).';
            END IF;
        END
        \$\$;

        SELECT pg_create_physical_replication_slot('$REPLICATION_SLOT')
        WHERE NOT EXISTS (
            SELECT 1 FROM pg_replication_slots WHERE slot_name = '$REPLICATION_SLOT'
        );
EOSQL

    echo "[replica-init] Replication user + slot OK."

    # Self-healing pg_hba.conf — primary mora imati 'host replication ...' pravilo
    # da bi prihvatio konekcije od replicator user-a. Ako 01-replication-setup.sh
    # nije zavrsio (npr. CRLF/bash^M iz prvog boot-a), pg_hba nema entry pa
    # pg_basebackup pada sa "no pg_hba.conf entry for replication connection".
    #
    # Koristi COPY ... TO PROGRAM (Postgres superuser feature za izvrsavanje
    # shell komandi na server side) da idempotentno doda entry — grep -q proverava
    # da li vec postoji, ako ne, tee -a dopise. SELECT pg_reload_conf() reload-uje
    # config bez restart-a. Banka2user je SUPERUSER (docker-entrypoint to default-uje
    # za POSTGRES_USER), pa ima privilegiju da pokrene COPY TO PROGRAM.
    echo "[replica-init] Provera pg_hba.conf entry-ja na primary-ju..."
    PGPASSWORD="$PRIMARY_PASSWORD" psql -h "$PRIMARY_HOST" -p "$PRIMARY_PORT" -U "$PRIMARY_USER" -d "$PRIMARY_DB" -v ON_ERROR_STOP=1 <<-EOSQL
        COPY (SELECT 'host    replication    $REPLICATION_USER    172.16.0.0/12    md5')
        TO PROGRAM 'grep -qE "^host[[:space:]]+replication[[:space:]]+$REPLICATION_USER" \$PGDATA/pg_hba.conf || tee -a \$PGDATA/pg_hba.conf >/dev/null';
        SELECT pg_reload_conf();
EOSQL
    echo "[replica-init] pg_hba.conf OK (entry dodat ili vec postoji), config reload-ovan."
}

if [ ! -s "$DATA_DIR/PG_VERSION" ]; then
    echo "[replica-init] Prazan data dir — pokrecem pg_basebackup iz $PRIMARY_HOST:$PRIMARY_PORT..."

    # Pre pg_basebackup-a osiguraj da primary ima replication user + slot.
    # Ovo pokriva slucaj gde je primary inicijalizovan polovicno (npr. CRLF
    # init script bug iz 04.05.2026 vece-6 koji je sprecio kreaciju ovih).
    ensure_replication_setup

    # Klon-uj primary u nas data dir.
    PGPASSWORD="$REPLICATION_PASSWORD" pg_basebackup \
        -h "$PRIMARY_HOST" \
        -p "$PRIMARY_PORT" \
        -U "$REPLICATION_USER" \
        -D "$DATA_DIR" \
        -P -R -X stream \
        -S "$REPLICATION_SLOT"

    # pg_basebackup -R automatski kreira standby.signal i upisuje primary_conninfo
    # u postgresql.auto.conf. To je sve sto je potrebno za hot standby.

    chown -R postgres:postgres "$DATA_DIR"
    chmod 0700 "$DATA_DIR"

    echo "[replica-init] Bazni backup zavrsen. Startujem postgres u standby mode-u."
fi

# Standardno postgres pokretanje (drop privileges na postgres user-a se desava unutra).
exec docker-entrypoint.sh postgres
