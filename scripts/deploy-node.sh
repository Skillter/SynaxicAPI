#!/bin/bash

# Deployment helper script - runs on each VPS node
# Returns: 0 on success, non-zero on failure
# NO SENSITIVE OUTPUT - only status codes

set -e

# Navigate to project directory
cd ~/SynaxicAPI

# Detect which docker-compose file to use
if docker ps --format '{{.Names}}' 2>/dev/null | grep -q 'synaxic-postgres-prod'; then
    COMPOSE_FILE="docker-compose.prod.yml"
elif docker ps --format '{{.Names}}' 2>/dev/null | grep -q 'synaxic-postgres-replica'; then
    COMPOSE_FILE="docker-compose.replica.yml"
elif docker ps --format '{{.Names}}' 2>/dev/null | grep -q 'synaxic-app-node'; then
    COMPOSE_FILE="docker-compose.app-only.yml"
else
    echo "ERROR: Could not detect node type"
    exit 1
fi

# Run update script (handles sudo internally if needed)
if ! ./update.sh > /dev/null 2>&1; then
    echo "ERROR: Update script failed"
    exit 2
fi

# Rebuild and restart services
if ! docker-compose -f "$COMPOSE_FILE" up --build -d > /dev/null 2>&1; then
    echo "ERROR: Docker restart failed"
    exit 3
fi

# Wait for services to stabilize
sleep 15

# Verify containers are running
RUNNING=$(docker ps --format '{{.Names}}' 2>/dev/null | grep -c synaxic- || echo "0")
if [ "$RUNNING" -eq 0 ]; then
    echo "ERROR: No containers running"
    exit 4
fi

# Health check on localhost
if ! curl -f -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "ERROR: Health check failed"
    exit 5
fi

echo "SUCCESS"
exit 0
