#!/bin/bash

# Update script - pulls latest changes and prepares for deployment
# Returns: 0 on success, non-zero on failure
# This script should handle sudo operations internally when needed

set -e

# Navigate to project directory
cd ~/SynaxicAPI

# Check if git repository exists
if [ ! -d ".git" ]; then
    echo "ERROR: Not a git repository"
    exit 1
fi

# Stash any local changes to avoid conflicts
if [ -n "$(git status --porcelain 2>/dev/null)" ]; then
    git stash push -m "Auto-stash before deployment $(date)" > /dev/null 2>&1 || true
fi

# Fetch latest changes
if ! git fetch origin > /dev/null 2>&1; then
    echo "ERROR: Failed to fetch latest changes"
    exit 2
fi

# Determine current branch and pull from appropriate remote
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "production")

# Pull latest changes for current branch
if ! git pull origin "$CURRENT_BRANCH" > /dev/null 2>&1; then
    echo "ERROR: Failed to pull latest changes from $CURRENT_BRANCH"
    exit 3
fi

# Ensure script files are executable
chmod +x scripts/*.sh > /dev/null 2>&1 || true

# Check if Docker daemon is running and restart if needed
if ! docker info > /dev/null 2>&1; then
    echo "WARNING: Docker daemon not responding"
    # Try to restart docker (requires sudo)
    if command -v sudo &> /dev/null; then
        echo "$1" | sudo -S systemctl restart docker > /dev/null 2>&1 || true
        sleep 5
    fi
fi

# Clean up any dangling Docker images to free space
docker image prune -f > /dev/null 2>&1 || true

# Check disk space and warn if low
DISK_USAGE=$(df / | awk 'NR==2 {print $5}' | sed 's/%//')
if [ "$DISK_USAGE" -gt 85 ]; then
    echo "WARNING: Disk usage is ${DISK_USAGE}% - consider cleanup"
fi

# Verify essential files exist
if [ ! -f "docker-compose.prod.yml" ]; then
    echo "ERROR: docker-compose.prod.yml not found"
    exit 4
fi

# Check if .env files exist and create placeholders if missing
if [ ! -f ".env" ]; then
    echo "WARNING: .env file missing - creating placeholder"
    cat > .env << EOF
# Environment variables - this should be populated with actual values
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
EOF
fi

echo "Update completed successfully"
exit 0