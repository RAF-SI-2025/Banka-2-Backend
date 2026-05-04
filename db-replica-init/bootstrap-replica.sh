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
REPLICATION_USER="${REPLICATION_USER:-replicator}"
REPLICATION_PASSWORD="${REPLICATION_PASSWORD:-replicator_pass}"
REPLICATION_SLOT="${REPLICATION_SLOT:-banka2_replica_slot}"

if [ ! -s "$DATA_DIR/PG_VERSION" ]; then
    echo "[replica-init] Prazan data dir — pokrecem pg_basebackup iz $PRIMARY_HOST:$PRIMARY_PORT..."

    # Cekaj primary da bude dostupan (zdrav).
    until PGPASSWORD="$REPLICATION_PASSWORD" pg_isready -h "$PRIMARY_HOST" -p "$PRIMARY_PORT" -U "$REPLICATION_USER"; do
        echo "[replica-init] Cekam primary da bude spreman..."
        sleep 2
    done

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
