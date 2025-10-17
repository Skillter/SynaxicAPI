#!/bin/bash

set -e

# --- Configuration ---
MAIN_IP_FILE="config/main_vps_ip.txt"

# --- Helper Functions ---
initial_install() {
    echo "======================================"
    echo "Synaxic Replica VPS Initial Setup"
    echo "======================================"

    cd "$(dirname "$0")/.."

    echo ">>> Updating system packages and installing dependencies..."
    sudo apt-get update
    sudo apt-get install -y apt-transport-https ca-certificates curl openssl postgresql-client

    # Install Docker
    if ! command -v docker &> /dev/null; then
        echo ">>> Installing Docker..."
        curl -fsSL https://download.docker.com/linux/debian/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
        echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
        sudo apt-get update
        sudo apt-get install -y docker-ce docker-ce-cli containerd.io
        sudo usermod -aG docker $USER
        echo "[OK] Docker installed successfully."
    else
        echo "[OK] Docker is already installed."
    fi

    # Install Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        echo ">>> Installing Docker Compose..."
        sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
        sudo chmod +x /usr/local/bin/docker-compose
        echo "[OK] Docker Compose installed successfully."
    else
        echo "[OK] Docker Compose is already installed."
    fi

    echo ""
    echo "======================================"
    echo "Prerequisites installed successfully!"
    echo "======================================"
    echo ""
    echo "IMPORTANT: Before continuing, you must:"
    echo "  1. Copy the .env file from the main VPS to this server"
    echo "  2. Copy the redis/tls/ directory from the main VPS to this server"
    echo ""
    echo "Then run: $0 --configure <main_vps_ip>"
}

configure_replica() {
    local main_ip=$1

    if [ -z "$main_ip" ]; then
        echo "[ERROR] Main VPS IP is required."
        echo "Usage: $0 --configure <main_vps_ip>"
        exit 1
    fi

    echo "======================================"
    echo "Configuring Replica VPS"
    echo "======================================"
    echo "Main VPS IP: $main_ip"
    echo ""

    # Verify prerequisites
    if [ ! -f ".env" ]; then
        echo "[ERROR] .env file not found. Copy it from the main VPS first."
        exit 1
    fi

    if [ ! -d "redis/tls" ] || [ ! -f "redis/tls/redis.crt" ]; then
        echo "[ERROR] redis/tls directory not found or incomplete. Copy it from the main VPS first."
        exit 1
    fi

    # Source .env file
    export $(grep -v '^#' .env | xargs)

    # Add main VPS IP if not exists
    if ! grep -q "POSTGRES_MASTER_HOST=" .env; then
        echo "POSTGRES_MASTER_HOST=${main_ip}" >> .env
    else
        sed -i "s|^POSTGRES_MASTER_HOST=.*|POSTGRES_MASTER_HOST=${main_ip}|" .env
    fi

    if ! grep -q "REDIS_MASTER_HOST=" .env; then
        echo "REDIS_MASTER_HOST=${main_ip}" >> .env
    else
        sed -i "s|^REDIS_MASTER_HOST=.*|REDIS_MASTER_HOST=${main_ip}|" .env
    fi

    # Save main IP to file
    mkdir -p "$(dirname "$MAIN_IP_FILE")"
    echo "$main_ip" > "$MAIN_IP_FILE"

    # Create Redis replica configuration
    echo ">>> Creating Redis replica configuration..."
    mkdir -p redis
    cat << EOL > redis/redis-replica.conf
# Redis Replica Configuration

# General
bind 0.0.0.0
protected-mode no
requirepass ${REDIS_PASSWORD}

# TLS Configuration
port 0
tls-port 6380
tls-cert-file /usr/local/etc/redis/tls/redis.crt
tls-key-file /usr/local/etc/redis/tls/redis.key
tls-auth-clients no

# Replication Configuration
replicaof ${main_ip} 6380
masterauth ${REDIS_PASSWORD}

# TLS for replication connection
tls-replication yes

# Replica settings
replica-read-only yes
replica-serve-stale-data yes
replica-priority 100

# Persistence (optional on replica, but recommended)
save 900 1
save 300 10
save 60 10000

dir /data
dbfilename dump.rdb
appendonly yes
appendfilename "appendonly.aof"
appendfsync everysec

# Logging
loglevel notice
logfile ""
EOL
    echo "[OK] Redis replica configuration created."

    # Test connectivity to main VPS
    echo ">>> Testing connectivity to main VPS..."

    if ! nc -zv -w 5 "$main_ip" 5432 2>&1 | grep -q "succeeded"; then
        echo "[WARN] Cannot reach PostgreSQL on $main_ip:5432"
        echo "       Ensure firewall allows connections from this server."
    else
        echo "[OK] PostgreSQL port 5432 is reachable."
    fi

    if ! nc -zv -w 5 "$main_ip" 6380 2>&1 | grep -q "succeeded"; then
        echo "[WARN] Cannot reach Redis on $main_ip:6380"
        echo "       Ensure firewall allows connections from this server."
    else
        echo "[OK] Redis port 6380 is reachable."
    fi

    # Set permissions
    echo ">>> Setting file permissions..."
    sudo chown -R $(id -u):$(id -g) redis postgres 2>/dev/null || true
    chmod 644 redis/tls/redis.crt redis/tls/redis.key redis/tls/truststore.p12 2>/dev/null || true
    chmod +x scripts/init-replica.sh 2>/dev/null || true
    echo "[OK] Permissions set."

    echo ""
    echo "======================================"
    echo "Configuration complete!"
    echo "======================================"
    echo ""
    echo "Next steps:"
    echo "  1. Ensure the replication user exists on the main VPS"
    echo "     (Run './scripts/setup-main-vps.sh --setup-replication' on main VPS)"
    echo ""
    echo "  2. Start the replica services:"
    echo "     docker-compose -f docker-compose.replica.yml up --build -d"
    echo ""
    echo "  3. Check replication status:"
    echo "     ./scripts/check-replication-status.sh replica"
    echo ""
}

check_replication_status() {
    echo "======================================"
    echo "Replica Replication Status"
    echo "======================================"

    if ! docker ps | grep -q synaxic-postgres-replica; then
        echo "[ERROR] PostgreSQL replica container is not running."
        exit 1
    fi

    export $(grep -v '^#' .env | xargs 2>/dev/null || true)
    POSTGRES_USER="${POSTGRES_USER:-synaxic}"
    POSTGRES_DB="${POSTGRES_DB:-synaxic}"

    echo ""
    echo "PostgreSQL Replication Status:"
    echo "--------------------------------------"
    docker exec synaxic-postgres-replica psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<-EOSQL
        -- Check if in recovery mode (standby)
        SELECT
            CASE WHEN pg_is_in_recovery() THEN 'YES - This is a replica'
                 ELSE 'NO - This is a master!'
            END AS is_replica;

        -- Show last WAL received and replayed
        SELECT
            pg_last_wal_receive_lsn() AS last_received,
            pg_last_wal_replay_lsn() AS last_replayed,
            pg_last_xact_replay_timestamp() AS last_replay_time;

        -- Show replication lag
        SELECT
            COALESCE(EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp())), 0) AS lag_seconds;
EOSQL

    echo ""
    echo "Redis Replication Status:"
    echo "--------------------------------------"
    docker exec synaxic-redis-replica redis-cli -a "${REDIS_PASSWORD}" -p 6380 --tls --cacert /usr/local/etc/redis/tls/redis.crt INFO replication 2>/dev/null | grep -E "role|master_host|master_port|master_link_status|slave_repl_offset" || echo "[ERROR] Could not retrieve Redis replication info"

    echo ""
    echo "======================================"
}

# --- Main Script Logic ---
cd "$(dirname "$0")/.."

case "$1" in
    --install)
        initial_install
        ;;
    --configure)
        if [ -z "$2" ]; then
            echo "Usage: $0 --configure <main_vps_ip>"
            exit 1
        fi
        configure_replica "$2"
        ;;
    --set-main-ip)
        if [ -z "$2" ]; then
            echo "Usage: $0 --set-main-ip <main_vps_ip>"
            exit 1
        fi
        configure_replica "$2"
        echo ""
        echo "Configuration updated. Restart services to apply changes:"
        echo "  docker-compose -f docker-compose.replica.yml restart"
        ;;
    --status)
        check_replication_status
        ;;
    *)
        if [ ! -f "redis/tls/redis.key" ]; then
            echo "No existing installation found. Running initial install..."
            initial_install
        else
            echo "Replica VPS Setup Script"
            echo ""
            echo "Usage: $0 [OPTION]"
            echo ""
            echo "Options:"
            echo "  --install              Install prerequisites (Docker, Docker Compose, etc.)"
            echo "  --configure <ip>       Configure replica to connect to main VPS at <ip>"
            echo "  --set-main-ip <ip>     Update main VPS IP and reconfigure"
            echo "  --status               Check replication status"
            echo ""
            echo "Typical workflow:"
            echo "  1. $0 --install"
            echo "  2. Copy .env and redis/tls/ from main VPS"
            echo "  3. $0 --configure <main_vps_ip>"
            echo "  4. docker-compose -f docker-compose.replica.yml up --build -d"
            echo "  5. $0 --status"
        fi
        ;;
esac
