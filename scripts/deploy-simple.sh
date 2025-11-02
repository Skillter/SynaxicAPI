#!/bin/bash

# Simplified deployment script - bypasses update step for now
# Returns: 0 on success, non-zero on failure

set -e

# Navigate to project directory
cd ~/SynaxicAPI

# Ensure scripts are executable (including this script)
echo "Setting script permissions..."
chmod +x scripts/*.sh > /dev/null 2>&1 || true
chmod +x *.sh > /dev/null 2>&1 || true
chmod +x update.sh > /dev/null 2>&1 || true
chmod +x scripts/deploy-simple.sh > /dev/null 2>&1 || true

# Check for required tools
if ! command -v curl &> /dev/null; then
    echo "ERROR: curl not installed"
    exit 6
fi

# Detect which docker-compose file to use
if docker ps --format '{{.Names}}' 2>/dev/null | grep -q 'synaxic-postgres-prod'; then
    COMPOSE_FILE="docker-compose.prod.yml"
elif docker ps --format '{{.Names}}' 2>/dev/null | grep -q 'synaxic-postgres-replica'; then
    COMPOSE_FILE="docker-compose.replica.yml"
elif docker ps --format '{{.Names}}' 2>/dev/null | grep -q 'synaxic-app-node'; then
    COMPOSE_FILE="docker-compose.app-only.yml"
else
    if [ -f "docker-compose.app-only.yml" ]; then
        COMPOSE_FILE="docker-compose.app-only.yml"
    elif [ -f "docker-compose.replica.yml" ]; then
        COMPOSE_FILE="docker-compose.replica.yml"
    elif [ -f "docker-compose.prod.yml" ]; then
        COMPOSE_FILE="docker-compose.prod.yml"
    else
        echo "ERROR: Could not detect node type"
        exit 1
    fi
fi

# Skip update step - go directly to rebuild
echo "Skipping update step - rebuilding services..."

# Rebuild and restart services
if ! docker-compose -f "$COMPOSE_FILE" up -d --build --force-recreate > /dev/null 2>&1; then
    echo "ERROR: Docker rebuild failed"
    exit 3
fi

# Wait for services to stabilize
sleep 20

# Verify containers are running
RUNNING=$(docker ps --format '{{.Names}}' 2>/dev/null | grep -c synaxic- || echo "0")
if [ "$RUNNING" -eq 0 ]; then
    echo "ERROR: No containers running"
    exit 4
fi

# Health check on localhost
MAX_RETRIES=18
RETRY_COUNT=0
echo "Waiting for application to start..."
while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if curl -f -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "SUCCESS"
        exit 0
    fi
    RETRY_COUNT=$((RETRY_COUNT + 1))
    sleep 10
done

echo "ERROR: Health check failed after $((MAX_RETRIES * 10)) seconds"
exit 5