#!/bin/bash
set -e

echo "======================================"
echo "PostgreSQL Replica Initialization"
echo "======================================"

# Environment variables (should be set in .env)
MASTER_HOST="${POSTGRES_MASTER_HOST:-postgres-master}"
MASTER_PORT="${POSTGRES_MASTER_PORT:-5432}"
REPLICATOR_USER="${POSTGRES_REPLICATOR_USER:-replicator}"
REPLICATOR_PASSWORD="${POSTGRES_REPLICATOR_PASSWORD}"

if [ -z "$REPLICATOR_PASSWORD" ]; then
    echo "ERROR: POSTGRES_REPLICATOR_PASSWORD is not set"
    exit 1
fi

PGDATA="${PGDATA:-/var/lib/postgresql/data}"

# Check if data directory is empty or already initialized
if [ -d "$PGDATA" ] && [ "$(ls -A $PGDATA 2>/dev/null)" ]; then
    echo "Data directory already initialized. Checking if this is a replica..."

    # Check for standby.signal file (indicates replica)
    if [ -f "$PGDATA/standby.signal" ]; then
        echo "This is already configured as a replica. Skipping initialization."
        echo "Replication status: $(cat $PGDATA/standby.signal 2>/dev/null || echo 'Active')"
        exit 0
    fi

    # Check if pg_wal directory exists (indicates initialized database)
    if [ -d "$PGDATA/pg_wal" ]; then
        echo "Data directory exists but not configured as replica."
        echo "This appears to be an existing PostgreSQL installation."
        echo "If you want to reinitialize as replica, you must:"
        echo "  1. Stop the container"
        echo "  2. Remove the data volume: docker volume rm synaxic_postgres-replica-data"
        echo "  3. Restart the container"
        exit 0
    fi
fi

echo "Initializing replica from master at $MASTER_HOST:$MASTER_PORT"

# Wait for master to be available
echo "Waiting for master PostgreSQL to be available..."
until pg_isready -h "$MASTER_HOST" -p "$MASTER_PORT" -U "$REPLICATOR_USER" 2>/dev/null; do
    echo "Master not ready yet, waiting..."
    sleep 5
done

echo "Master is available. Starting base backup..."

# Create replication slot on master (idempotent)
echo "Creating replication slot 'replica1_slot' on master..."
SLOT_EXISTS=$(PGPASSWORD="$REPLICATOR_PASSWORD" psql -h "$MASTER_HOST" -p "$MASTER_PORT" -U "$REPLICATOR_USER" -d postgres -tAc \
    "SELECT count(*) FROM pg_replication_slots WHERE slot_name='replica1_slot';" 2>/dev/null || echo "0")

if [ "$SLOT_EXISTS" = "0" ]; then
    PGPASSWORD="$REPLICATOR_PASSWORD" psql -h "$MASTER_HOST" -p "$MASTER_PORT" -U "$REPLICATOR_USER" -d postgres -c \
        "SELECT pg_create_physical_replication_slot('replica1_slot', true);" || {
            echo "[WARN] Could not create replication slot. It may already exist."
        }
    echo "[OK] Replication slot created."
else
    echo "[OK] Replication slot 'replica1_slot' already exists."
fi

# Perform base backup using pg_basebackup
echo "Performing base backup from master..."
PGPASSWORD="$REPLICATOR_PASSWORD" pg_basebackup \
    -h "$MASTER_HOST" \
    -p "$MASTER_PORT" \
    -U "$REPLICATOR_USER" \
    -D "$PGDATA" \
    -Fp \
    -Xs \
    -P \
    -R \
    -S replica1_slot

# Create standby.signal file (pg_basebackup with -R should create this, but ensure it exists)
touch "$PGDATA/standby.signal"

# Update postgresql.auto.conf with connection info
cat >> "$PGDATA/postgresql.auto.conf" <<EOF

# Streaming Replication Configuration
primary_conninfo = 'host=$MASTER_HOST port=$MASTER_PORT user=$REPLICATOR_USER password=$REPLICATOR_PASSWORD application_name=replica1'
primary_slot_name = 'replica1_slot'
EOF

echo "======================================"
echo "Replica initialization complete!"
echo "======================================"
echo "The replica will start streaming from the master."
echo "To promote this replica to master, create the file: /tmp/promote_to_master"
