#!/bin/bash

set -e

echo ">>> Starting Synaxic Main VPS Setup..."

cd "$(dirname "$0")/.."

echo ">>> Updating system packages and installing dependencies..."
sudo apt-get update
sudo apt-get install -y apt-transport-https ca-certificates curl software-properties-common openssl

if ! command -v docker &> /dev/null
then
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

if ! command -v docker-compose &> /dev/null
then
    echo ">>> Installing Docker Compose..."
    sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
    echo "[OK] Docker Compose installed successfully."
else
    echo "[OK] Docker Compose is already installed."
fi

echo ">>> Managing credentials in .env file..."

if [ ! -f ".env" ]; then
    cp .env.example .env
    echo "[OK] .env file created from .env.example."
fi

if [ -f ".env" ]; then
    export $(grep -v '^#' .env | xargs)
fi

generate_password_if_empty() {
    local var_name="$1"
    local current_val=$(grep "^${var_name}=" .env | cut -d'=' -f2)
    if [ -z "$current_val" ]; then
        echo ">>> Generating new password for ${var_name}..."
        local new_pass=$(openssl rand -hex 32)
        sed -i "s/^${var_name}=.*/${var_name}=${new_pass}/" .env
    fi
}

generate_password_if_empty "POSTGRES_PASSWORD"
generate_password_if_empty "REDIS_PASSWORD"
generate_password_if_empty "GRAFANA_ADMIN_PASSWORD"

echo ">>> Setting Docker user permissions..."
if ! grep -q "DOCKER_UID=" .env; then
    echo -e "\nDOCKER_UID=\nDOCKER_GID=" >> .env
fi
sed -i "s/^DOCKER_UID=.*/DOCKER_UID=$(id -u)/" .env
sed -i "s/^DOCKER_GID=.*/DOCKER_GID=$(id -g)/" .env

echo "[OK] .env file is configured."

if [ -f ".env" ]; then
    export $(grep -v '^#' .env | xargs)
fi

echo ">>> Generating self-signed TLS certificates for Redis..."
mkdir -p redis/tls
if [ -f "redis/tls/redis.key" ] && [ -f "redis/tls/redis.crt" ]; then
    echo "[WARN] Redis TLS certificates already exist. Skipping generation."
else
    openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
        -keyout redis/tls/redis.key -out redis/tls/redis.crt \
        -subj "/C=US/ST=CA/L=SF/O=Synaxic/CN=$(curl -s ifconfig.me || echo 'localhost')"
    chmod 640 redis/tls/redis.key
    echo "[OK] TLS certificates generated in redis/tls/."
fi

echo ">>> Creating Java truststore for the application..."
if [ -f "redis/tls/truststore.p12" ]; then
    echo "[WARN] Java truststore already exists. Skipping creation."
else
    openssl pkcs12 -export -in redis/tls/redis.crt -inkey redis/tls/redis.key \
        -out redis/tls/truststore.p12 -name redis-cert \
        -passout pass:changeit
    echo "[OK] Java truststore created at redis/tls/truststore.p12"
fi

echo ">>> Creating Redis configuration file (redis/redis.conf)..."
mkdir -p redis
cat << EOL | tr -d '\r' > redis/redis.conf
bind 0.0.0.0
protected-mode no
requirepass ${REDIS_PASSWORD}
port 0
tls-port 6380
tls-cert-file /usr/local/etc/redis/tls/redis.crt
tls-key-file /usr/local/etc/redis/tls/redis.key
tls-auth-clients no
EOL
echo "[OK] redis.conf created in redis/."

echo ""
echo "=================================================="
echo "Main VPS setup is complete!"
echo "--------------------------------------------------"
echo "Next Steps:"
echo "1. (Optional) For GeoIP features, download 'GeoLite2-City.mmdb' from MaxMind"
echo "   and place it in 'src/main/resources/'."
echo ""
echo "2. IMPORTANT: Copy the following files to your replica VPS project directory:"
echo "   - The entire 'redis/tls' directory."
echo "   - The updated '.env' file."
echo ""
echo "3. Your Main VPS Public IP is: $(curl -s ifconfig.me || echo 'Could not determine IP')"
echo "4. To start services, run: docker-compose -f docker-compose.prod.yml up --build -d"
echo "=================================================="
echo "If this is your first time using Docker, run 'newgrp docker' or log out and back in to use docker without sudo."