#!/bin/bash
set -e

echo "======================================"
echo "PostgreSQL Replication Status"
echo "======================================"

MODE="${1:-master}"

if [ "$MODE" = "master" ]; then
    echo "Checking MASTER replication status..."
    echo ""

    docker exec synaxic-postgres-prod psql -U "${POSTGRES_USER:-synaxic}" -d "${POSTGRES_DB:-synaxic}" <<-EOSQL
        -- Show connected replicas
        SELECT client_addr, state, sync_state, sent_lsn, write_lsn, flush_lsn, replay_lsn
        FROM pg_stat_replication;

        -- Show replication slots
        SELECT slot_name, slot_type, active, restart_lsn
        FROM pg_replication_slots;
EOSQL

elif [ "$MODE" = "replica" ]; then
    echo "Checking REPLICA replication status..."
    echo ""

    docker exec synaxic-postgres-replica psql -U "${POSTGRES_USER:-synaxic}" -d "${POSTGRES_DB:-synaxic}" <<-EOSQL
        -- Check if in recovery mode (standby)
        SELECT pg_is_in_recovery() AS is_replica;

        -- Show last WAL received and replayed
        SELECT
            pg_last_wal_receive_lsn() AS last_received,
            pg_last_wal_replay_lsn() AS last_replayed,
            pg_last_xact_replay_timestamp() AS last_replay_time;

        -- Show replication lag
        SELECT
            EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp())) AS lag_seconds;
EOSQL

else
    echo "Usage: $0 [master|replica]"
    exit 1
fi

echo ""
echo "======================================"
