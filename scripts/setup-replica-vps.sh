#!/bin/bash

set -e

echo ">>> Starting Synaxic Replica VPS Setup..."

cd "$(dirname "$0")/.."

echo ">>> Updating system packages and installing dependencies..."
sudo apt-get update
sudo apt-get install -y apt-transport-https ca-certificates curl software-properties-common

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

if [ ! -f ".env" ] || [ ! -d "redis/tls" ] || [ ! -f "redis/tls/truststore.p12" ]; then
    echo "[ERROR] Missing required files. Please copy '.env' and the 'redis/tls' directory from the main VPS to this project root before running this script."
    exit 1
fi

if [ -f ".env" ]; then
    export $(grep -v '^#' .env | xargs)
fi

read -p "Enter the Public IP address of the Main VPS: " MAIN_VPS_IP

if [ -z "$MAIN_VPS_IP" ]; then
    echo "[ERROR] Main VPS IP cannot be empty."
    exit 1
fi

echo ">>> Creating Docker Compose file for replica (docker-compose.replica.yml)..."
cat << EOL | tr -d '\r' > docker-compose.replica.yml
services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: synaxic-app-replica
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:postgresql://${MAIN_VPS_IP}:5432/${POSTGRES_DB}
      - SPRING_DATASOURCE_USERNAME=${POSTGRES_USER}
      - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
      - SPRING_DATA_REDIS_HOST=${MAIN_VPS_IP}
      - SPRING_DATA_REDIS_PORT=6380
      - SPRING_DATA_REDIS_PASSWORD=${REDIS_PASSWORD}
      - SPRING_DATA_REDIS_SSL_ENABLED=true
      - GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
      - GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET}
    restart: unless-stopped

  redis-replica:
    image: redis:7-alpine
    container_name: synaxic-redis-replica
    command: redis-server /usr/local/etc/redis/redis-replica.conf
    ports:
      - "6379:6379"
    volumes:
      - redis-data-replica:/data
      - ./redis/redis-replica.conf:/usr/local/etc/redis/redis-replica.conf
      - ./redis/tls:/usr/local/etc/redis/tls
    restart: unless-stopped

volumes:
  redis-data-replica:
EOL
echo "[OK] docker-compose.replica.yml created."

echo ">>> Creating Redis replica configuration file (redis/redis-replica.conf)..."
mkdir -p redis
cat << EOL | tr -d '\r' > redis/redis-replica.conf
port 6379
replicaof ${MAIN_VPS_IP} 6380
masterauth ${REDIS_PASSWORD}
requirepass ${REDIS_PASSWORD}
tls-replication yes
tls-cert-file /usr/local/etc/redis/tls/redis.crt
tls-key-file /usr/local/etc/redis/tls/redis.key
tls-auth-clients no
EOL
echo "[OK] redis-replica.conf created."

echo ""
echo "=================================================="
echo "Replica VPS setup is complete!"
echo "--------------------------------------------------"
echo "Next Steps:"
echo "1. The application is configured to use the main VPS at ${MAIN_VPS_IP}."
echo "2. A local Redis replica will mirror the main Redis instance over TLS."
echo ""
echo "3. To start services, run: docker-compose -f docker-compose.replica.yml up --build -d"
echo "=================================================="
echo "Note: For a full failover setup, you would also need to configure PostgreSQL streaming replication separately."
echo "If this is your first time using Docker, run 'newgrp docker' or log out and back in to use docker without sudo."