#!/bin/bash

# Deployment helper script - runs on each VPS node
# Returns: 0 on success, non-zero on failure
# NO SENSITIVE OUTPUT - only status codes

set -e

# Navigate to project directory
cd ~/SynaxicAPI

# Check for required tools
if ! command -v curl &> /dev/null; then
    echo "ERROR: curl not installed"
    exit 6
fi

# Detect which docker-compose file to use
# First try by checking running containers
if docker ps --format '{{.Names}}' 2>/dev/null | grep -q 'synaxic-postgres-prod'; then
    COMPOSE_FILE="docker-compose.prod.yml"
elif docker ps --format '{{.Names}}' 2>/dev/null | grep -q 'synaxic-postgres-replica'; then
    COMPOSE_FILE="docker-compose.replica.yml"
elif docker ps --format '{{.Names}}' 2>/dev/null | grep -q 'synaxic-app-node'; then
    COMPOSE_FILE="docker-compose.app-only.yml"
else
    # Fallback: Check which compose files exist and use the most specific one
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

# Ensure scripts are executable
chmod +x scripts/*.sh > /dev/null 2>&1 || true

# Run update script (handles sudo internally if needed)
# Check if password is provided as first argument
if [ -n "$1" ]; then
    # Password provided as argument
    if ! ./update.sh "$1" > /dev/null 2>&1; then
        echo "ERROR: Update script failed"
        exit 2
    fi
else
    # No password provided as argument
    if ! ./update.sh > /dev/null 2>&1; then
        echo "ERROR: Update script failed"
        exit 2
    fi
fi

# Rebuild and restart services (rebuild to include updated static files)
if ! docker-compose -f "$COMPOSE_FILE" up -d --build --force-recreate > /dev/null 2>&1; then
    echo "ERROR: Docker rebuild failed"
    exit 3
fi

# Wait for services to stabilize (longer wait due to rebuild)
sleep 20

# Verify containers are running
RUNNING=$(docker ps --format '{{.Names}}' 2>/dev/null | grep -c synaxic- || echo "0")
if [ "$RUNNING" -eq 0 ]; then
    echo "ERROR: No containers running"
    exit 4
fi

# Health check on localhost (with retries)
# Spring Boot can take 60-90 seconds to start
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
