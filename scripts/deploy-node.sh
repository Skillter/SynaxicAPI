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

# Run update script (handles sudo internally if needed)
# Note: Only stdout/stderr are redirected, stdin is preserved for password
if ! ./update.sh > /dev/null 2>&1; then
    echo "ERROR: Update script failed"
    exit 2
fi

# Restart services (no --build, just pull new code and restart)
if ! docker-compose -f "$COMPOSE_FILE" up -d --force-recreate > /dev/null 2>&1; then
    echo "ERROR: Docker restart failed"
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

# Wait a bit more for health endpoint to be ready
sleep 5

# Health check on localhost (with retries)
MAX_RETRIES=3
RETRY_COUNT=0
while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if curl -f -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "SUCCESS"
        exit 0
    fi
    RETRY_COUNT=$((RETRY_COUNT + 1))
    sleep 5
done

echo "ERROR: Health check failed after $MAX_RETRIES attempts"
exit 5
