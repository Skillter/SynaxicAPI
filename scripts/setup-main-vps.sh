#!/bin/bash

set -e

# --- Configuration ---
REPLICA_IP_FILE="nginx/replica_ips.txt"
SSL_CONFIG_FILE=".env.ssl"
SSL_CERT_DIR="/etc/ssl/cloudflare"

# --- Helper Functions ---

setup_ssl_certificates() {
    echo "========================================"
    echo "Cloudflare SSL/TLS Configuration Setup"
    echo "========================================"
    echo ""
    echo "This will configure End-to-End Encryption with Cloudflare (Full Strict mode)."
    echo ""

    # Check if certificates already exist
    local CERTS_EXIST="false"
    if [ -f "$SSL_CERT_DIR/cert.pem" ] && [ -f "$SSL_CERT_DIR/key.pem" ]; then
        echo "[OK] Certificates already exist at $SSL_CERT_DIR"
        CERTS_EXIST="true"
    fi

    # Check if SSL config already exists
    local DOMAIN_CONFIGURED="false"
    local EXISTING_DOMAIN=""
    if [ -f "$SSL_CONFIG_FILE" ]; then
        EXISTING_DOMAIN=$(grep "^DOMAIN=" "$SSL_CONFIG_FILE" | cut -d'=' -f2 | tr -d '"')
        if [ -n "$EXISTING_DOMAIN" ]; then
            echo "[OK] Domain already configured: $EXISTING_DOMAIN"
            DOMAIN_CONFIGURED="true"
        fi
    fi

    # If both certs and domain are already configured, we're done
    if [ "$CERTS_EXIST" = "true" ] && [ "$DOMAIN_CONFIGURED" = "true" ]; then
        echo "[OK] SSL/TLS is already fully configured. Skipping setup."
        return 0
    fi

    # Ask for domain only if not already configured
    local DOMAIN="$EXISTING_DOMAIN"
    if [ "$DOMAIN_CONFIGURED" != "true" ]; then
        read -p "Enter your domain name (e.g., api.example.com): " DOMAIN
        if [ -z "$DOMAIN" ]; then
            echo "[ERROR] Domain cannot be empty."
            return 1
        fi
    fi

    # Ask for certificates only if they don't already exist
    if [ "$CERTS_EXIST" != "true" ]; then
        echo ""
        echo "Next, you need to provide your Cloudflare Origin Certificate."
        echo "Get it from: Cloudflare Dashboard > SSL/TLS > Origin Server > Create Certificate"
        echo ""

        # Prompt for certificate path
        read -p "Enter path to Cloudflare Origin Certificate file (PEM format): " CERT_PATH
        if [ -z "$CERT_PATH" ] || [ ! -f "$CERT_PATH" ]; then
            echo "[ERROR] Certificate file not found at $CERT_PATH"
            return 1
        fi

        # Prompt for private key path
        read -p "Enter path to Cloudflare Private Key file (PEM format): " KEY_PATH
        if [ -z "$KEY_PATH" ] || [ ! -f "$KEY_PATH" ]; then
            echo "[ERROR] Private key file not found at $KEY_PATH"
            return 1
        fi

        # Validate certificate format
        if ! grep -q "BEGIN CERTIFICATE" "$CERT_PATH"; then
            echo "[ERROR] Invalid certificate format. Must start with '-----BEGIN CERTIFICATE-----'"
            return 1
        fi

        if ! grep -q "BEGIN.*KEY" "$KEY_PATH"; then
            echo "[ERROR] Invalid private key format. Must start with '-----BEGIN.*KEY-----'"
            return 1
        fi

        # Create SSL directory and install certificates
        echo ">>> Installing certificates..."
        sudo mkdir -p "$SSL_CERT_DIR"
        sudo cp "$CERT_PATH" "$SSL_CERT_DIR/cert.pem"
        sudo cp "$KEY_PATH" "$SSL_CERT_DIR/key.pem"
        sudo chmod 600 "$SSL_CERT_DIR/key.pem"
        sudo chmod 644 "$SSL_CERT_DIR/cert.pem"
        echo "[OK] Certificates installed to $SSL_CERT_DIR"
    fi

    # Save/update SSL configuration
    cat > "$SSL_CONFIG_FILE" << EOF
DOMAIN="$DOMAIN"
SSL_ENABLED="true"
CERT_PATH="$SSL_CERT_DIR/cert.pem"
KEY_PATH="$SSL_CERT_DIR/key.pem"
EOF
    echo "[OK] SSL configuration saved to $SSL_CONFIG_FILE"
    echo ""
}

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
    [ -d "nginx/nginx.conf" ] && sudo rm -rf "nginx/nginx.conf"

    # Load SSL configuration if it exists
    local SSL_ENABLED="false"
    local DOMAIN="_"
    local CERT_PATH=""
    local KEY_PATH=""
    if [ -f "$SSL_CONFIG_FILE" ]; then
        source "$SSL_CONFIG_FILE"
    fi

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

    # Add HTTPS server block if SSL is enabled
    if [ "$SSL_ENABLED" = "true" ] && [ -n "$CERT_PATH" ] && [ -n "$KEY_PATH" ]; then
        cat << EOL >> nginx/nginx.conf
    }

    # HTTPS server (Cloudflare Full Strict mode)
    server {
        listen 443 ssl http2;
        server_name $DOMAIN;

        ssl_certificate $CERT_PATH;
        ssl_certificate_key $KEY_PATH;
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers HIGH:!aNULL:!MD5;
        ssl_prefer_server_ciphers on;

        location / {
            proxy_pass http://synaxic_api;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto https;
        }
    }

    # HTTP redirect to HTTPS
    server {
        listen 80;
        server_name $DOMAIN;
        return 301 https://\$server_name\$request_uri;
    }
}
EOL
    else
        # HTTP-only configuration
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
    fi
    echo "[OK] nginx.conf updated."

    # --- Prometheus Config ---
    # Note: Prometheus needs to scrape other nodes. Docker's internal DNS can resolve
    # service names, but for external IPs, they must be listed directly.
    # We will assume that for replicas, we need to scrape their public IPs.
    local targets_string="'app-main:8080'"
    for ip in "${ips[@]}"; do targets_string+=", '$ip:8080'"; done
    [ -d "prometheus.prod.yml" ] && sudo rm -rf "prometheus.prod.yml"
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
    [ -d "postgres/master/pg_hba.conf" ] && sudo rm -rf "postgres/master/pg_hba.conf"
    cat << EOL > postgres/master/pg_hba.conf
# TYPE  DATABASE        USER            ADDRESS                 METHOD
# Allow all connections from Docker networks
host    all             all             172.16.0.0/12           scram-sha-256
host    all             all             192.168.0.0/16          scram-sha-256
local   all             all                                     trust
host    all             all             127.0.0.1/32            scram-sha-256
host    all             all             ::1/128                 scram-sha-256
EOL
    for ip in "${ips[@]}"; do echo "host    replication     replicator      $ip/32        scram-sha-256" >> postgres/master/pg_hba.conf; done
    echo "[OK] pg_hba.conf updated."

    # --- Set Correct Permissions ---
    echo ">>> Setting ownership for config directories..."
    sudo chown -R $(id -u):$(id -g) nginx redis postgres
    # Make TLS files readable by Docker containers
    chmod 644 redis/tls/redis.crt redis/tls/redis.key redis/tls/truststore.p12 2>/dev/null || true
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
    sudo apt-get install -y apt-transport-https ca-certificates curl openssl nginx

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
        # Use redis as CN (Docker service name) + SAN for IP
        local main_vps_ip=$(curl -s --max-time 5 https://api.ipify.org 2>/dev/null || \
                            curl -s --max-time 5 https://icanhazip.com 2>/dev/null || \
                            hostname -I | awk '{print $1}' 2>/dev/null || \
                            echo '127.0.0.1')
        # Validate IP
        if [[ "$main_vps_ip" =~ \<.*\> ]]; then
            main_vps_ip="127.0.0.1"
        fi

        # Create cert with CN=redis and SAN for both redis and IP
        cat > redis/tls/openssl.cnf << EOF
[req]
distinguished_name = req_distinguished_name
x509_extensions = v3_req
prompt = no

[req_distinguished_name]
C = US
ST = CA
L = SF
O = Synaxic
CN = redis

[v3_req]
subjectAltName = @alt_names

[alt_names]
DNS.1 = redis
DNS.2 = localhost
IP.1 = ${main_vps_ip}
IP.2 = 127.0.0.1
EOF
        openssl req -x509 -nodes -newkey rsa:2048 -days 365 -keyout redis/tls/redis.key -out redis/tls/redis.crt -config redis/tls/openssl.cnf -extensions v3_req
        rm redis/tls/openssl.cnf
        echo "[OK] TLS certificates generated for CN=redis with SAN."
    else echo "[WARN] Redis TLS certificates already exist."; fi

    if [ ! -f "redis/tls/truststore.p12" ]; then
        openssl pkcs12 -export -in redis/tls/redis.crt -inkey redis/tls/redis.key -out redis/tls/truststore.p12 -name redis-cert -passout pass:changeit
        echo "[OK] Java truststore created."
    else echo "[WARN] Java truststore already exists."; fi

    # Make TLS files readable by Docker containers
    chmod 644 redis/tls/redis.crt redis/tls/redis.key redis/tls/truststore.p12
    echo "[OK] TLS file permissions set."

    echo ">>> Creating base Redis & PostgreSQL configuration files..."

    # Clean up incorrectly created directories
    [ -d "redis/redis.conf" ] && sudo rm -rf "redis/redis.conf"
    [ -d "postgres/master" ] && sudo rm -rf "postgres/master"

    mkdir -p redis postgres/master
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
dir /data
# Disable disk persistence to avoid permission issues
save ""
stop-writes-on-bgsave-error no
EOL
    # Use the comprehensive PostgreSQL master config if it doesn't exist
    if [ ! -f "postgres/master/postgresql.conf" ] || [ ! -s "postgres/master/postgresql.conf" ]; then
        cat << EOL > postgres/master/postgresql.conf
# PostgreSQL Master Configuration for Streaming Replication

# Connection Settings
listen_addresses = '*'
max_connections = 100

# Write Ahead Log (WAL) Settings
wal_level = replica
max_wal_senders = 3
max_replication_slots = 3
wal_keep_size = 256MB

# Archiving (optional but recommended)
archive_mode = on
archive_command = 'test ! -f /var/lib/postgresql/data/archive/%f && cp %p /var/lib/postgresql/data/archive/%f'

# Hot Standby (allows read queries on replica)
hot_standby = on

# Logging
log_destination = 'stderr'
logging_collector = on
log_directory = 'log'
log_filename = 'postgresql-%Y-%m-%d_%H%M%S.log'
log_rotation_age = 1d
log_rotation_size = 100MB
log_line_prefix = '%m [%p] %u@%d '
log_timezone = 'UTC'

# Performance
shared_buffers = 256MB
effective_cache_size = 1GB
maintenance_work_mem = 64MB
checkpoint_completion_target = 0.9
wal_buffers = 16MB
default_statistics_target = 100
random_page_cost = 1.1
effective_io_concurrency = 200
work_mem = 4MB
min_wal_size = 1GB
max_wal_size = 4GB
EOL
    fi
    echo "[OK] Base config files created."

    # Create empty replica IPs file if it doesn't exist
    echo ">>> Ensuring replica management file exists..."
    mkdir -p "$(dirname "$REPLICA_IP_FILE")"
    if [ ! -f "$REPLICA_IP_FILE" ]; then
        touch "$REPLICA_IP_FILE"
        echo "[OK] Created empty replica IPs file at $REPLICA_IP_FILE"
    else
        echo "[OK] Replica IPs file already exists at $REPLICA_IP_FILE"
    fi

    # Ask about Cloudflare SSL setup
    echo ""
    read -p "Do you want to set up Cloudflare SSL/TLS now? (y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        setup_ssl_certificates
    fi

    echo "=================================================="
    echo "Main VPS initial setup is complete!"
    echo "--------------------------------------------------"
    echo "You can now start your services with: docker-compose -f docker-compose.prod.yml up --build -d"
    echo ""
    echo "IMPORTANT: After starting services, run the following to set up replication:"
    echo "  ./scripts/setup-master.sh"
    echo ""
    echo "Use this script with --add-replica, --remove-replica, or --update to manage your cluster."
    echo "=================================================="
}

setup_replication_user() {
    echo "======================================"
    echo "Setting up PostgreSQL Replication User"
    echo "======================================"

    # Check if containers are running
    if ! docker ps | grep -q synaxic-postgres-prod; then
        echo "[ERROR] PostgreSQL container is not running. Start services first:"
        echo "  docker-compose -f docker-compose.prod.yml up -d"
        exit 1
    fi

    # Source .env file
    if [ -f ".env" ]; then
        export $(grep -v '^#' .env | xargs)
    fi

    REPLICATOR_USER="${POSTGRES_REPLICATOR_USER:-replicator}"
    REPLICATOR_PASSWORD="${POSTGRES_REPLICATOR_PASSWORD}"
    POSTGRES_USER="${POSTGRES_USER:-synaxic}"
    POSTGRES_DB="${POSTGRES_DB:-synaxic}"

    # Generate replicator password if not exists
    if [ -z "$REPLICATOR_PASSWORD" ]; then
        echo ">>> Generating replication user password..."
        REPLICATOR_PASSWORD=$(openssl rand -base64 24)
        if ! grep -q "POSTGRES_REPLICATOR_PASSWORD=" .env; then
            echo "POSTGRES_REPLICATOR_PASSWORD=${REPLICATOR_PASSWORD}" >> .env
        else
            sed -i "s|^POSTGRES_REPLICATOR_PASSWORD=.*|POSTGRES_REPLICATOR_PASSWORD=${REPLICATOR_PASSWORD}|" .env
        fi
        echo "[OK] Replication password generated and saved to .env"
    fi

    if [ -z "$REPLICATOR_USER" ] || ! grep -q "POSTGRES_REPLICATOR_USER=" .env; then
        echo "POSTGRES_REPLICATOR_USER=replicator" >> .env
        REPLICATOR_USER="replicator"
    fi

    echo "Creating replication user '$REPLICATOR_USER'..."

    # Create replication user (idempotent)
    docker exec synaxic-postgres-prod psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<-EOSQL 2>&1 | grep -v "already exists" || true
        -- Create replication user if not exists
        DO \$\$
        BEGIN
            IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$REPLICATOR_USER') THEN
                CREATE ROLE $REPLICATOR_USER WITH REPLICATION LOGIN PASSWORD '$REPLICATOR_PASSWORD';
                RAISE NOTICE 'Replication user created';
            ELSE
                -- Update password if user exists
                ALTER ROLE $REPLICATOR_USER WITH PASSWORD '$REPLICATOR_PASSWORD';
                RAISE NOTICE 'Replication user already exists, password updated';
            END IF;
        END
        \$\$;

        -- Grant necessary permissions
        GRANT CONNECT ON DATABASE $POSTGRES_DB TO $REPLICATOR_USER;
EOSQL

    echo ""
    echo "======================================"
    echo "Replication user setup complete!"
    echo "======================================"
    echo "Replication user: $REPLICATOR_USER"
    echo "Password saved in .env file"
    echo ""
    echo "Next steps:"
    echo "1. Ensure firewall allows connections from replica VPS on port 5432"
    echo "2. Copy .env and redis/tls/ to replica VPS"
    echo "3. Run setup-replica-vps.sh on the replica VPS"
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
    --setup-replication)
        setup_replication_user
        ;;
    --setup-ssl)
        setup_ssl_certificates
        if [ $? -eq 0 ]; then
            update_configs
        fi
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
            echo "Usage: $0 [--install|--setup-ssl|--setup-replication|--add-replica <ip>|--remove-replica <ip>|--update]"
            echo ""
            echo "Options:"
            echo "  --install              Run initial installation"
            echo "  --setup-ssl            Configure Cloudflare SSL/TLS certificates"
            echo "  --setup-replication    Configure PostgreSQL replication user (run after starting services)"
            echo "  --add-replica <ip>     Add a replica server IP to the cluster"
            echo "  --remove-replica <ip>  Remove a replica server IP from the cluster"
            echo "  --update               Interactive replica management"
        fi
        ;;
esac