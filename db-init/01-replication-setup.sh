#!/bin/bash
# Init script za primary PG instance — kreira replication user-a, dodaje
# pravila u pg_hba.conf, podiže max_wal_senders, kreira physical replication slot.
# Pokrece se SAMO pri prvom inicijalnom boot-u (postgres:16-alpine docker-entrypoint
# automatski poziva sve .sh / .sql u /docker-entrypoint-initdb.d/ pri praznoj data dir-i).

set -e

# Replication user — ima samo REPLICATION privilegiju, nema pristup nikakvim tabelama.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER replicator WITH REPLICATION ENCRYPTED PASSWORD 'replicator_pass';
    SELECT pg_create_physical_replication_slot('banka2_replica_slot');
EOSQL

# pg_hba.conf — replication user moze da se konektuje iz docker network-a.
# 172.16.0.0/12 pokriva default Docker bridge (172.17.x) + custom networks
# (banka-2-backend_default je tipicno 172.18.x ili 172.19.x).
cat >> "$PGDATA/pg_hba.conf" <<-EOF
host    replication    replicator    172.16.0.0/12    md5
EOF

# postgresql.conf — wal_level=replica je vec default, ali eksplicitno setujemo.
cat >> "$PGDATA/postgresql.conf" <<-EOF
wal_level = replica
max_wal_senders = 5
max_replication_slots = 5
hot_standby = on
EOF

echo "[init] Replication setup zavrsen: replicator user + replication slot kreirani."
