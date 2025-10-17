#!/bin/bash
set -e

echo "======================================"
echo "PostgreSQL Master Setup"
echo "======================================"

# This script should be run on the MASTER VPS
# It creates the replication user and configures the master for replication

REPLICATOR_USER="${POSTGRES_REPLICATOR_USER:-replicator}"
REPLICATOR_PASSWORD="${POSTGRES_REPLICATOR_PASSWORD}"
POSTGRES_USER="${POSTGRES_USER:-synaxic}"
POSTGRES_DB="${POSTGRES_DB:-synaxic}"

if [ -z "$REPLICATOR_PASSWORD" ]; then
    echo "ERROR: POSTGRES_REPLICATOR_PASSWORD is not set"
    exit 1
fi

echo "Creating replication user '$REPLICATOR_USER'..."

# Connect to PostgreSQL and create replication user
docker exec -it synaxic-postgres-prod psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<-EOSQL
    -- Create replication user if not exists
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$REPLICATOR_USER') THEN
            CREATE ROLE $REPLICATOR_USER WITH REPLICATION LOGIN PASSWORD '$REPLICATOR_PASSWORD';
            RAISE NOTICE 'Replication user created';
        ELSE
            RAISE NOTICE 'Replication user already exists';
        END IF;
    END
    \$\$;

    -- Grant necessary permissions
    GRANT CONNECT ON DATABASE $POSTGRES_DB TO $REPLICATOR_USER;

    -- Show replication slots
    SELECT * FROM pg_replication_slots;
EOSQL

echo "======================================"
echo "Master setup complete!"
echo "======================================"
echo "Replication user: $REPLICATOR_USER"
echo "Next steps:"
echo "1. Ensure firewall allows connections from replica VPS on port 5432"
echo "2. Run the replica setup on the replica VPS"
