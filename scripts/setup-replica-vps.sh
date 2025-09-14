#!/bin/bash

set -e

echo ">>> Starting Synaxic Replica VPS Setup..."

# Ensure the script operates from the project root directory
cd "$(dirname "$0")/.."

# 1. System Update and Dependency Installation
echo ">>> Updating system packages and installing dependencies..."
sudo apt-get update
sudo apt-get install -y apt-transport-https ca-certificates curl software-properties-common

# 2. Install Docker and Docker Compose
if ! command -v docker &> /dev/null
then
    echo ">>> Installing Docker..."
    curl -fsSL https://download.docker.com/linux/debian/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" | sudo tee /etc/apt/sources/list.d/docker.list > /dev/null
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

# 3. Check for required files
if [ ! -f ".env" ] || [ ! -d "redis/tls" ] || [ ! -f "redis/tls/redis.crt" ] || [ ! -f "redis/tls/redis.key" ]; then
    echo "[ERROR] Missing required files. Please copy '.env' and the 'redis/tls' directory from the main VPS to this project root before running this script."
    exit 1
fi

# Safely load variables from .env file
if [ -f ".env" ]; then
    export $(grep -v '^#' .env | xargs)
fi

# 4. Prompt for Main VPS IP
read -p "Enter the Public IP address of the Main VPS: " MAIN_VPS_IP

if [ -z "$MAIN_VPS_IP" ]; then
    echo "[ERROR] Main VPS IP cannot be empty."
    exit 1
fi

# 5. Create .env for Replica
echo ">>> Configuring .env.replica for replica connection..."
cat << EOL | tr -d '\r' > .env.replica
# Production Environment Variables for Synaxic API (Replica VPS)
POSTGRES_DB=${POSTGRES_DB}
POSTGRES_USER=${POSTGRES_USER}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
REDIS_PASSWORD=${REDIS_PASSWORD}

# --- Application Configuration ---
# Point to the main VPS for primary data sources
SPRING_DATASOURCE_URL=jdbc:postgresql://${MAIN_VPS_IP}:5432/${POSTGRES_DB}
SPRING_DATA_REDIS_URL=rediss://${MAIN_VPS_IP}:6380?sslVerificationMode=NONE

# Replace with your actual Google OAuth credentials
GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET}
EOL
echo "[OK] .env.replica created."

# 6. Create Redis Replica Configuration
echo ">>> Creating Redis replica configuration file (redis/redis-replica.conf)..."
mkdir -p redis
cat << EOL | tr -d '\r' > redis/redis-replica.conf
port 6379
replicaof ${MAIN_VPS_IP} 6380
masterauth ${REDIS_PASSWORD}
requirepass ${REDIS_PASSWORD}

# TLS Configuration to connect to master
tls-cert-file /usr/local/etc/redis/tls/redis.crt
tls-key-file /usr/local/etc/redis/tls/redis.key
tls-replication yes
tls-auth-clients no
EOL
echo "[OK] redis-replica.conf created."

# 7. Create Docker Compose for Replica
echo ">>> Creating Docker Compose file for replica (docker-compose.replica.yml)..."
cat << EOL | tr -d '\r' > docker-compose.replica.yml
services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: synaxic-app-replica
    env_file: .env.replica
    ports:
      - "8080:8080"
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
      - ./redis/tls/redis.crt:/usr/local/etc/redis/tls/redis.crt
      - ./redis/tls/redis.key:/usr/local/etc/redis/tls/redis.key
    restart: unless-stopped

volumes:
  redis-data-replica:
EOL
echo "[OK] docker-compose.replica.yml created."

# 8. Final Instructions
echo ""
echo "=================================================="
echo "Replica VPS setup is complete!"
echo "--------------------------------------------------"
echo "Next Steps:"
echo "1. A 'docker-compose.replica.yml' file and supporting configs have been created."
echo "2. The application on this server is configured to use the main VPS at ${MAIN_VPS_IP} as its primary database and cache."
echo "3. A local Redis replica has been configured to mirror the main Redis instance over a secure TLS connection."
echo ""
echo "4. You can start the services on this replica server by running:"
echo "   docker-compose -f docker-compose.replica.yml up -d --build"
echo "=================================================="
echo "Note: For a full failover setup, you would need to configure PostgreSQL streaming replication manually."
echo "If you are running this for the first time, you may need to run 'newgrp docker' or log out and log back in to use docker without sudo."