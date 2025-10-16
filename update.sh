#!/bin/bash

# Load all environment variables from .profile
if [ -f "$HOME/.profile" ]; then
    source "$HOME/.profile"
fi

# Get the directory of this script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# GitHub configuration
GITHUB_REPO="Skillter/SynaxicAPI"
GITHUB_TOKEN="${GITHUB_TOKEN:-}"

# === VALIDATE REQUIRED ENVIRONMENT VARIABLES ===
echo "Checking environment variables..."

if [ -z "$GITHUB_TOKEN" ]; then
    echo ""
    echo "ERROR: GITHUB_TOKEN environment variable is not set!"
    echo ""
    echo "Please set it with:"
    echo "  echo \"export GITHUB_TOKEN='your_github_token_here'\" >> ~/.profile"
    echo "Then re-run this script."
    echo ""
    echo "Get your token at: https://github.com/settings/tokens"
    echo ""
    exit 1
fi

# Configure git to use the token
GIT_AUTH_URL="https://${GITHUB_TOKEN}@github.com/${GITHUB_REPO}.git"

# Change to script directory
cd "$SCRIPT_DIR"

# Fix .git directory permissions first
echo "Fixing repository permissions..."
sudo chown -R $(whoami):$(whoami) "$SCRIPT_DIR/.git" 2>/dev/null || true
sudo chmod -R u+w "$SCRIPT_DIR/.git" 2>/dev/null || true

# === BACKUP GENERATED CONFIG FILES ===
echo "Backing up generated configuration files..."
BACKUP_DIR=$(mktemp -d)
FILES_TO_PRESERVE=(
    ".env"
    "nginx/replica_ips.txt"
    "nginx/nginx.conf"
    "prometheus.prod.yml"
    "prometheus-prod.yml"
    "postgres/master/pg_hba.conf"
    "postgres/master/postgresql.conf"
    "redis/redis.conf"
    "redis/tls/redis.key"
    "redis/tls/redis.crt"
    "redis/tls/truststore.p12"
    "docker-compose.replica.yml"
    "docker-compose.app-only.yml"
    "config/main_vps_ip.txt"
)

for file in "${FILES_TO_PRESERVE[@]}"; do
    if [ -f "$file" ]; then
        mkdir -p "$BACKUP_DIR/$(dirname "$file")"
        sudo cp -p "$file" "$BACKUP_DIR/$file" 2>/dev/null || cp -p "$file" "$BACKUP_DIR/$file"
        echo "  ✓ Backed up: $file"
    elif [ -d "$file" ]; then
        # Handle cases where a file path is actually a directory (incorrect state)
        echo "  ⚠ Skipped (is a directory): $file"
    fi
done

# Force pull latest changes
echo "Fetching latest changes..."
git fetch "$GIT_AUTH_URL"
git reset --hard FETCH_HEAD

# Clean up any incorrectly created directories before git clean
echo "Cleaning up incorrect directory structures..."
for file in "${FILES_TO_PRESERVE[@]}"; do
    if [ -d "$file" ] && [ ! -L "$file" ]; then
        echo "  Removing incorrect directory: $file"
        sudo rm -rf "$file" 2>/dev/null || true
    fi
done

# Use sudo for git clean to handle root-owned files
sudo git clean -fd 2>/dev/null || git clean -fd

# === RESTORE BACKED UP FILES ===
echo "Restoring configuration files..."
for file in "${FILES_TO_PRESERVE[@]}"; do
    if [ -f "$BACKUP_DIR/$file" ]; then
        mkdir -p "$(dirname "$file")"
        cp -p "$BACKUP_DIR/$file" "$file"
        echo "  ✓ Restored: $file"
    fi
done

# Fix TLS file permissions for Docker containers
if [ -f "redis/tls/redis.crt" ]; then
    chmod 644 redis/tls/redis.crt redis/tls/redis.key redis/tls/truststore.p12 2>/dev/null || true
    echo "  ✓ Fixed TLS permissions"
fi

# Clean up backup
rm -rf "$BACKUP_DIR"

# === REGENERATE CONFIG FILES IF NEEDED ===
echo "Checking configuration files..."
if [ -f "nginx/replica_ips.txt" ] && [ ! -f "prometheus.prod.yml" ]; then
    echo ">>> Detected missing config files. Regenerating..."
    if [ -f "scripts/setup-main-vps.sh" ]; then
        # Source the setup script to use its update_configs function
        source scripts/setup-main-vps.sh
        update_configs
        echo "[OK] Configuration files regenerated."
    else
        echo "[WARN] setup-main-vps.sh not found. You may need to run it manually."
    fi
fi

# Make all files executable
sudo chmod -R +x "$SCRIPT_DIR"

echo ""
echo "✓ Repository updated successfully!"
echo ""