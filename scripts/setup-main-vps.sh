#!/bin/bash

set -e

# --- Configuration ---
REPLICA_IP_FILE="nginx/replica_ips.txt"

# --- Helper Functions ---
update_configs() {
    echo ">>> Updating configuration files based on replica IPs..."

    local ips=()
    if [ -f "$REPLICA_IP_FILE" ]; then
        while IFS= read -r line; do
            # Skip empty lines
            if [ -n "$line" ]; then
                ips+=("$line")
            fi
        done < "$REPLICA_IP_FILE"
    fi
    echo "Found ${#ips[@]} replica IP(s) to configure."

    # --- Nginx Config ---
    mkdir -p nginx
    cat << EOL > nginx/nginx.conf
user www-data;
worker_processes auto;
pid /run/nginx.pid;
include /etc/nginx/modules-enabled/*.conf;

events { worker_connections 768; }

http {
    upstream synaxic_api {
        ip_hash;
        server 127.0.0.1:8080; # Main VPS App (Always local)
EOL
    for ip in "${ips[@]}"; do echo "        server $ip:8080;" >> nginx/nginx.conf; done
    cat << EOL >> nginx/nginx.conf
    }
    server {
        listen 80;
        server_name _;
        location / {
            proxy_pass http://synaxic_api;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto \$scheme;
        }
    }
}
EOL
    echo "[OK] nginx.conf updated."

    # --- Prometheus Config ---
    # Note: Prometheus needs to scrape other nodes. Docker's internal DNS can resolve
    # service names, but for external IPs, they must be listed directly.
    # We will assume that for replicas, we need to scrape their public IPs.
    local targets_string="'app-main:8080'"
    for ip in "${ips[@]}"; do targets_string+=", '$ip:8080'"; done
    cat << EOL > prometheus.prod.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'synaxic-api'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: [${targets_string}]
EOL
    echo "[OK] prometheus.prod.yml updated."

    # --- PostgreSQL HBA Config ---
    mkdir -p postgres/master
    cat << EOL > postgres/master/pg_hba.conf
# TYPE  DATABASE        USER            ADDRESS                 METHOD
# Allow all connections from the local docker network
host    all             all             172.16.0.0/12           scram-sha-256
local   all             all                                     trust
host    all             all             127.0.0.1/32            scram-sha-256
host    all             all             ::1/128                 scram-sha-256
EOL
    for ip in "${ips[@]}"; do echo "host    replication     replicator      $ip/32        scram-sha-256" >> postgres/master/pg_hba.conf; done
    echo "[OK] pg_hba.conf updated."

    # --- Set Correct Permissions ---
    echo ">>> Setting ownership for config directories..."
    sudo chown -R $(id -u):$(id -g) nginx redis postgres
    echo "[OK] Permissions set."

    echo ""
    echo "Configuration files have been updated. Please apply the changes:"
    echo " - Nginx: sudo cp nginx/nginx.conf /etc/nginx/nginx.conf && sudo systemctl restart nginx"
    echo " - Docker services: docker-compose -f docker-compose.prod.yml restart postgres prometheus"
}

initial_install() {
    echo ">>> Starting Synaxic Main VPS Initial Installation..."
    cd "$(dirname "$0")/.."

    echo ">>> Updating system packages and installing dependencies..."
    sudo apt-get update
    sudo apt-get install -y apt-transport-https ca-certificates curl software-properties-common openssl nginx

    if ! command -v docker &> /dev/null; then
        echo ">>> Installing Docker..."
        curl -fsSL https://download.docker.com/linux/debian/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
        echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
        sudo apt-get update
        sudo apt-get install -y docker-ce docker-ce-cli containerd.io
        sudo usermod -aG docker $USER
        echo "[OK] Docker installed successfully. You may need to log out and back in."
    else
        echo "[OK] Docker is already installed."
    fi

    if ! command -v docker-compose &> /dev/null; then
        echo ">>> Installing Docker Compose..."
        sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
        sudo chmod +x /usr/local/bin/docker-compose
        echo "[OK] Docker Compose installed successfully."
    else
        echo "[OK] Docker Compose is already installed."
    fi

    echo ">>> Managing credentials in .env file..."
    if [ ! -f ".env" ]; then cp .env.example .env && echo "[OK] .env file created."; fi

    # Source .env file to load existing variables
    if [ -f ".env" ]; then
        export $(grep -v '^#' .env | xargs)
    fi

    generate_password_if_empty() {
        local var_name="$1"
        # Using eval to get the value of the variable whose name is in var_name
        local current_val=$(eval echo \$"$var_name")
        if [ -z "$current_val" ]; then
            echo ">>> Generating new password for ${var_name}..."
            local new_pass=$(openssl rand -base64 24)
            # Use a different delimiter for sed to avoid issues with special characters in passwords
            sed -i "s|^${var_name}=.*|${var_name}=${new_pass}|" .env
        fi
    }
    generate_password_if_empty "POSTGRES_PASSWORD"
    generate_password_if_empty "REDIS_PASSWORD"
    generate_password_if_empty "GRAFANA_ADMIN_PASSWORD"

    if ! grep -q "DOCKER_UID=" .env; then echo -e "\nDOCKER_UID=\nDOCKER_GID=" >> .env; fi
    sed -i "s|^DOCKER_UID=.*|DOCKER_UID=$(id -u)|" .env
    sed -i "s|^DOCKER_GID=.*|DOCKER_GID=$(id -g)|" .env
    echo "[OK] .env file is configured."

    # Re-source the .env file to get newly generated passwords
    export $(grep -v '^#' .env | xargs)

    echo ">>> Generating self-signed TLS certificates for Redis..."
    mkdir -p redis/tls
    if [ ! -f "redis/tls/redis.key" ]; then
        local main_vps_ip_for_cert=$(curl -s ifconfig.me || echo 'localhost')
        openssl req -x509 -nodes -newkey rsa:2048 -days 365 -keyout redis/tls/redis.key -out redis/tls/redis.crt -subj "/C=US/ST=CA/L=SF/O=Synaxic/CN=${main_vps_ip_for_cert}"
        chmod 640 redis/tls/redis.key
        echo "[OK] TLS certificates generated."
    else echo "[WARN] Redis TLS certificates already exist."; fi

    if [ ! -f "redis/tls/truststore.p12" ]; then
        openssl pkcs12 -export -in redis/tls/redis.crt -inkey redis/tls/redis.key -out redis/tls/truststore.p12 -name redis-cert -passout pass:changeit
        echo "[OK] Java truststore created."
    else echo "[WARN] Java truststore already exists."; fi

    echo ">>> Creating base Redis & PostgreSQL configuration files..."
    mkdir -p redis; mkdir -p postgres/master
    export $(grep -v '^#' .env | xargs)
    cat << EOL > redis/redis.conf
bind 0.0.0.0
protected-mode no
requirepass ${REDIS_PASSWORD}
port 0
tls-port 6380
tls-cert-file /usr/local/etc/redis/tls/redis.crt
tls-key-file /usr/local/etc/redis/tls/redis.key
tls-auth-clients no
# Set the working directory for data persistence
dir /data
EOL
    cat << EOL > postgres/master/postgresql.conf
listen_addresses = '*'
max_connections = 100
shared_buffers = 128MB
dynamic_shared_memory_type = posix
wal_level = replica
max_wal_senders = 10
wal_keep_size = 256MB
hot_standby = on
EOL
    echo "[OK] Base config files created."

    echo "=================================================="
    echo "Main VPS initial setup is complete!"
    echo "--------------------------------------------------"
    echo "You can now start your services with: docker-compose -f docker-compose.prod.yml up --build -d"
    echo "Use this script with --add-replica, --remove-replica, or --update to manage your cluster."
    echo "=================================================="
}

interactive_update() {
    while true; do
        echo ""
        echo "--- Interactive Replica Management ---"
        echo "Current Replica IPs:"
        if [ -f "$REPLICA_IP_FILE" ]; then cat -n "$REPLICA_IP_FILE"; else echo " (none)"; fi
        echo ""
        echo "Choose an option:"
        echo " 1) Add a new replica IP"
        echo " 2) Remove a replica IP (by number)"
        echo " 3) Save changes and exit"
        echo " 4) Exit without saving"
        read -p "Option: " choice

        case $choice in
            1)
                read -p "Enter new replica IP to add: " ip
                if [[ -n "$ip" ]]; then
                    if ! grep -q "^${ip}$" "$REPLICA_IP_FILE" 2>/dev/null; then
                        echo "$ip" >> "$REPLICA_IP_FILE"
                        echo "[OK] Added $ip."
                    else
                        echo "[WARN] IP $ip already exists."
                    fi
                fi
                ;;
            2)
                read -p "Enter the number of the IP to remove: " num
                if [[ "$num" =~ ^[0-9]+$ ]] && [ -f "$REPLICA_IP_FILE" ]; then
                    local total_lines=$(wc -l < "$REPLICA_IP_FILE")
                    if [ "$num" -gt 0 ] && [ "$num" -le "$total_lines" ]; then
                        local ip_to_remove=$(sed -n "${num}p" "$REPLICA_IP_FILE")
                        sed -i "${num}d" "$REPLICA_IP_FILE"
                        echo "[OK] Removed $ip_to_remove."
                    else
                        echo "[ERROR] Invalid number."
                    fi
                fi
                ;;
            3)
                update_configs
                break
                ;;
            4)
                echo "Exiting without changes."
                break
                ;;
            *)
                echo "Invalid option. Please try again."
                ;;
        esac
    done
}

# --- Main Script Logic ---
cd "$(dirname "$0")/.."

case "$1" in
    --install)
        initial_install
        update_configs
        ;;
    --add-replica)
        if [ -z "$2" ]; then echo "Usage: $0 --add-replica <ip_address>"; exit 1; fi
        mkdir -p "$(dirname "$REPLICA_IP_FILE")"
        if ! grep -q "^$2$" "$REPLICA_IP_FILE" 2>/dev/null; then
            echo "$2" >> "$REPLICA_IP_FILE"
            echo "[OK] Added replica IP $2."
            update_configs
        else
            echo "[WARN] Replica IP $2 already exists. No changes made."
        fi
        ;;
    --remove-replica)
        if [ -z "$2" ]; then echo "Usage: $0 --remove-replica <ip_address>"; exit 1; fi
        if [ -f "$REPLICA_IP_FILE" ] && grep -q "^$2$" "$REPLICA_IP_FILE"; then
            sed -i "/^$2$/d" "$REPLICA_IP_FILE"
            echo "[OK] Removed replica IP $2."
            update_configs
        else
            echo "[WARN] Replica IP $2 not found. No changes made."
        fi
        ;;
    --update)
        interactive_update
        ;;
    *)
        if [ ! -f "redis/tls/redis.key" ]; then
            echo "No existing installation found. Running initial install..."
            initial_install
            update_configs
        else
            echo "Existing installation found. Use --install to force re-installation."
            echo "Usage: $0 [--install|--add-replica <ip>|--remove-replica <ip>|--update]"
        fi
        ;;
esac