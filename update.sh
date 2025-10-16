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

# Force pull latest changes
echo "Fetching latest changes..."
git fetch "$GIT_AUTH_URL"
git reset --hard FETCH_HEAD
git clean -fd

# Make all files executable
sudo chmod -R +x "$SCRIPT_DIR"

echo ""
echo "✓ Repository updated successfully!"
echo ""