#!/bin/bash

set -e

# --- Configuration ---
MAIN_IP_FILE="config/main_vps_ip.txt"

# --- Helper Function ---
update_configs_with_main_ip() {
    local main_ip=$1
    if [ -z "$main_ip" ]; then
        echo "[ERROR] Main VPS IP is required."
        exit 1
    fi

    echo ">>> Updating configuration files to use Main VPS at $main_ip..."
    export $(grep -v '^#' .env | xargs)

    # --- Docker Compose ---
    [ -d "docker-compose.replica.yml" ] && rm -rf "docker-compose.replica.yml"
    cat << EOL > docker-compose.replica.yml
services:
  app-replica:
    build: {context: ., dockerfile: Dockerfile}
    container_name: synaxic-app-replica
    ports: ["8080:8080"]
    volumes: ["./redis/tls:/app/redis-tls"]
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:postgresql://${main_ip}:5432/\${POSTGRES_DB}
      - SPRING_DATASOURCE_USERNAME=\${POSTGRES_USER}
      - SPRING_DATASOURCE_PASSWORD=\${POSTGRES_PASSWORD}
      - SPRING_DATA_REDIS_MASTER_HOST=${main_ip}
      - SPRING_DATA_REDIS_PORT=6380
      - SPRING_DATA_REDIS_PASSWORD=\${REDIS_PASSWORD}
      - SPRING_DATA_REDIS_SSL_ENABLED=true
      - SPRING_DATA_REDIS_SLAVE_HOSTS=rediss://redis-replica:6379
      - SYNAXIC_REDIS_READ_MODE=SLAVE
      - GOOGLE_CLIENT_ID=\${GOOGLE_CLIENT_ID}
      - GOOGLE_CLIENT_SECRET=\${GOOGLE_CLIENT_SECRET}
    restart: unless-stopped
  redis-replica:
    image: redis:7-alpine
    container_name: synaxic-redis-replica
    command: redis-server /usr/local/etc/redis/redis-replica.conf
    ports: ["6379:6379"]
    volumes: ["redis-data-replica:/data", "./redis/redis-replica.conf:/usr/local/etc/redis/redis-replica.conf", "./redis/tls:/usr/local/etc/redis/tls"]
    restart: unless-stopped
  postgres-replica:
    image: postgres:16
    container_name: synaxic-postgres-replica
    env_file: .env
    volumes: ["postgres-data-replica:/var/lib/postgresql/data", "./postgres/replica/entrypoint.sh:/docker-entrypoint-initdb.d/init-replica.sh"]
    restart: unless-stopped
volumes:
  redis-data-replica:
  postgres-data-replica:
EOL
    echo "[OK] docker-compose.replica.yml updated."

    # --- Redis Replica Config ---
    mkdir -p redis
    [ -d "redis/redis-replica.conf" ] && rm -rf "redis/redis-replica.conf"
    cat << EOL > redis/redis-replica.conf
port 6379
replicaof ${main_ip} 6380
masterauth ${REDIS_PASSWORD}
requirepass ${REDIS_PASSWORD}
tls-replication yes
tls-cert-file /usr/local/etc/redis/tls/redis.crt
tls-key-file /usr/local/etc/redis/tls/redis.key
tls-cacert-file /usr/local/etc/redis/tls/redis.crt
tls-auth-clients no
EOL
    echo "[OK] redis-replica.conf updated."

    # --- PostgreSQL Replica Entrypoint ---
    mkdir -p postgres/replica
    [ -d "postgres/replica/entrypoint.sh" ] && rm -rf "postgres/replica/entrypoint.sh"
    cat << EOL > postgres/replica/entrypoint.sh
#!/bin/bash
set -e
if [ -n "\$(ls -A /var/lib/postgresql/data)" ]; then
    echo "PostgreSQL data directory already exists, skipping replica setup."
    exec postgres
fi
echo "Initializing as a replica..."
PGPASSWORD="\${POSTGRES_PASSWORD}" pg_basebackup -h ${main_ip} -p 5432 -U replicator -D /var/lib/postgresql/data -Fp -Xs -R
{
    echo "hot_standby = on"
    echo "primary_conninfo = 'host=${main_ip} port=5432 user=replicator password=\${POSTGRES_PASSWORD} sslmode=prefer'"
    echo "promote_trigger_file = '/tmp/promote_now'"
} >> /var/lib/postgresql/data/postgresql.conf
exec postgres
EOL
    chmod +x postgres/replica/entrypoint.sh
    echo "[OK] PostgreSQL replica entrypoint updated."

    # --- Set Correct Permissions ---
    echo ">>> Setting ownership for config directories..."
    sudo chown -R $(id -u):$(id -g) redis postgres
    echo "[OK] Permissions set."

    echo ""
    echo "Configuration updated to point to Main VPS at $main_ip."
    echo "Restart your services to apply the changes: docker-compose -f docker-compose.replica.yml up -d --force-recreate"
}

# --- Main Script Logic ---
cd "$(dirname "$0")/.."

if [[ "$1" == "--set-main-ip" ]]; then
    if [ -z "$2" ]; then echo "Usage: $0 --set-main-ip <ip_address>"; exit 1; fi
    mkdir -p "$(dirname "$MAIN_IP_FILE")"
    echo "$2" > "$MAIN_IP_FILE"
    update_configs_with_main_ip "$2"
    exit 0
fi

if [ ! -f "docker-compose.replica.yml" ]; then
    echo ">>> Running initial setup for Replica VPS..."
    sudo apt-get update && sudo apt-get install -y curl postgresql-client
    if ! command -v docker &> /dev/null; then echo "Installing Docker..."; curl -fsSL https://download.docker.com/linux/debian/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg; echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null; sudo apt-get update; sudo apt-get install -y docker-ce docker-ce-cli containerd.io; sudo usermod -aG docker $USER; fi
    if ! command -v docker-compose &> /dev/null; then echo "Installing Docker Compose..."; sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose; sudo chmod +x /usr/local/bin/docker-compose; fi

    if [ ! -f ".env" ] || [ ! -d "redis/tls" ]; then
        echo "[ERROR] Missing required files. Copy '.env' and 'redis/tls' from the main VPS first."
        exit 1
    fi

    local main_ip=""
    if [ -f "$MAIN_IP_FILE" ]; then
        main_ip=$(cat "$MAIN_IP_FILE")
    else
        read -p ">>> Enter the Public IP address of the Main VPS: " main_ip
        mkdir -p "$(dirname "$MAIN_IP_FILE")"
        echo "$main_ip" > "$MAIN_IP_FILE"
    fi
    update_configs_with_main_ip "$main_ip"
    echo "Initial setup complete. To start services, run: docker-compose -f docker-compose.replica.yml up --build -d"
else
    echo "Existing installation found. To change the Main VPS IP, run:"
    echo "$0 --set-main-ip <new_ip_address>"
fi