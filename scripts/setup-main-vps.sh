#!/bin/bash

set -e

echo ">>> Starting Synaxic Main VPS Setup..."

# Ensure the script operates from the project root directory
cd "$(dirname "$0")/.."

# 1. System Update and Dependency Installation
echo ">>> Updating system packages and installing dependencies..."
sudo apt-get update
sudo apt-get install -y apt-transport-https ca-certificates curl software-properties-common openssl

# 2. Install Docker and Docker Compose
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

# 3. Create or Update Environment File (.env) in project root
echo ">>> Managing credentials in .env file..."

# Create the file from .env.example if it doesn't exist
if [ ! -f ".env" ]; then
    cp .env.example .env
    echo "[OK] .env file created from .env.example."
fi

# Safely load variables from .env file
if [ -f ".env" ]; then
    export $(grep -v '^#' .env | xargs)
fi

# Generate passwords ONLY if they are not already set
if [ -z "$POSTGRES_PASSWORD" ]; then
    echo ">>> Generating new PostgreSQL password..."
    POSTGRES_PASSWORD=$(openssl rand -hex 32)
    sed -i "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=${POSTGRES_PASSWORD}/" .env
fi

if [ -z "$REDIS_PASSWORD" ]; then
    echo ">>> Generating new Redis password..."
    REDIS_PASSWORD=$(openssl rand -hex 32)
    sed -i "s/^REDIS_PASSWORD=.*/REDIS_PASSWORD=${REDIS_PASSWORD}/" .env
fi

if [ -z "$GRAFANA_ADMIN_PASSWORD" ]; then
    echo ">>> Generating new Grafana admin password..."
    GRAFANA_ADMIN_PASSWORD=$(openssl rand -hex 32)
    sed -i "s/^GRAFANA_ADMIN_PASSWORD=.*/GRAFANA_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD}/" .env
fi

# Add DOCKER_UID/DOCKER_GID for Docker permissions
echo ">>> Setting Docker user permissions..."
# Check if DOCKER_UID/DOCKER_GID are already in the file, if not, add them
if ! grep -q "DOCKER_UID=" .env; then
    echo -e "\n# Docker User Permissions\nDOCKER_UID=\nDOCKER_GID=" >> .env
fi
# Set the current user's UID and GID
sed -i "s/^DOCKER_UID=.*/DOCKER_UID=$(id -u)/" .env
sed -i "s/^DOCKER_GID=.*/DOCKER_GID=$(id -g)/" .env

echo "[OK] .env file is configured."

# Re-source the file to ensure generated passwords are in the environment
if [ -f ".env" ]; then
    export $(grep -v '^#' .env | xargs)
fi

# 4. Generate Self-Signed TLS Certificates for Redis
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

# Create a Java truststore for the application
echo ">>> Creating Java truststore for the application..."
openssl pkcs12 -export -in redis/tls/redis.crt -inkey redis/tls/redis.key \
    -out redis/tls/truststore.p12 -name redis-cert \
    -passout pass:changeit
echo "[OK] Java truststore created at redis/tls/truststore.p12"

# 5. Create Redis Configuration File
echo ">>> Creating Redis configuration file (redis/redis.conf)..."
mkdir -p redis
cat << EOL | tr -d '\r' > redis/redis.conf
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
EOL
echo "[OK] redis.conf created in redis/."

# 6. Final Instructions
echo ""
echo "=================================================="
echo "Main VPS setup is complete!"
echo "--------------------------------------------------"
echo "Next Steps:"
echo "1. IMPORTANT: Copy the following files to your replica VPS before running its setup script:"
echo "   - The generated '.env' file."
echo "   - The TLS certificates: 'redis/tls/redis.crt' and 'redis/tls/redis.key'."
echo ""
echo "2. Your Main VPS Public IP is: $(curl -s ifconfig.me || echo 'Could not determine IP')"
echo "3. Your passwords are in the .env file."
echo ""
echo "4. You can start the services on this main server by running:"
echo "   docker-compose -f docker-compose.prod.yml up -d --build"
echo "=================================================="
echo "If you are running this for the first time, you may need to run 'newgrp docker' or log out and log back in to use docker without sudo."