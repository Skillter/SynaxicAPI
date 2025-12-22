const API_BASE = window.location.origin;
let currentUser = null;
let keyToDelete = null;

// Color palette for API keys (consistent coloring)
const colorPalette = [
    '#6366f1', '#8b5cf6', '#06b6d4', '#10b981', 
    '#f59e0b', '#ef4444', '#ec4899', '#84cc16'
];

function getKeyColor(keyId) {
    const idStr = String(keyId || 'default');
    let hash = 0;
    for (let i = 0; i < idStr.length; i++) {
        hash = idStr.charCodeAt(i) + ((hash << 5) - hash);
    }
    const index = Math.abs(hash) % colorPalette.length;
    return colorPalette[index];
}

// === AUTHENTICATION LOGIC ===

/**
 * Specialized fetch for Dashboard operations.
 * STRICTLY uses Session Cookies (credentials: 'include') and DOES NOT send API Key headers.
 * This prevents the backend from seeing an API Key and ignoring the Session User.
 */
async function sessionFetch(url, options = {}) {
    const defaultOptions = {
        credentials: 'include', // Send cookies
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        }
    };

    // Merge options
    const finalOptions = { ...defaultOptions, ...options };
    
    // Ensure we DO NOT send Authorization header from localStorage
    if (finalOptions.headers['Authorization']) {
        console.warn('Removing Authorization header from session fetch to prevent auth conflict');
        delete finalOptions.headers['Authorization'];
    }

    const response = await fetch(url, finalOptions);

    // Handle 401/403 (Session Expired) globally
    if (response.status === 401 || response.status === 403) {
        console.log('Session expired or invalid, redirecting to login...');
        window.location.href = '/'; 
        throw new Error('Session expired');
    }

    // Capture rate limit headers for real-time updates (if present)
    const limit = response.headers.get('X-RateLimit-Limit');
    const remaining = response.headers.get('X-RateLimit-Remaining');
    if (limit && remaining && usagePredictor) {
        usagePredictor.updateFromRateLimitHeaders(parseInt(limit), parseInt(remaining));
    }

    return response;
}

// === REAL-TIME USAGE PREDICTOR ===

class RealTimeUsagePredictor {
    constructor() {
        this.limit = 10000;
        this.used = 0;
        this.remaining = 10000;
        this.interval = null;
        this.lastUpdate = Date.now();
    }

    updateServerData(used, limit, remaining) {
        this.limit = limit;
        this.used = used;
        this.remaining = remaining !== undefined ? remaining : Math.max(0, limit - used);
        this.lastUpdate = Date.now();
        this.updateUI();
    }

    updateFromRateLimitHeaders(limit, remaining) {
        // Filter out static resource limits (usually 50,000)
        // We only want to track the Account Limit (usually 10,000)
        if (limit > 20000) { 
            return; // Ignore static asset limits
        }

        const used = Math.max(0, limit - remaining);
        
        // Only update if it makes sense (e.g., usage increased)
        if (used >= this.used) {
            this.limit = limit;
            this.remaining = remaining;
            this.used = used;
            this.lastUpdate = Date.now();
            this.updateUI();
        }
    }

    updateUI() {
        // Update Text Counters
        const usedEl = document.getElementById('account-used');
        const limitEl = document.getElementById('account-limit');
        const percentEl = document.getElementById('donut-percentage');

        if (usedEl) usedEl.textContent = this.used.toLocaleString();
        if (limitEl) limitEl.textContent = this.limit.toLocaleString();
        
        const percent = this.limit > 0 ? (this.used / this.limit) * 100 : 0;
        if (percentEl) percentEl.textContent = `${percent.toFixed(1)}%`;

        // Update Donut Chart SVG
        this.renderDonut(percent);
    }

    renderDonut(percentage) {
        const svg = document.getElementById('donut-chart');
        if (!svg) return;

        // Clear existing dynamic elements (keep background circle)
        const existingPath = svg.querySelector('.donut-progress');
        if (existingPath) existingPath.remove();
        const existingDefs = svg.querySelector('defs');
        if (existingDefs) existingDefs.remove();

        // Constants
        const radius = 120;
        const center = 150;
        const circumference = 2 * Math.PI * radius;
        const offset = circumference - (percentage / 100) * circumference;

        // Create Gradient Definition
        const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');
        defs.innerHTML = `
            <linearGradient id="donutGradient" x1="0%" y1="0%" x2="100%" y2="0%">
                <stop offset="0%" stop-color="#6366f1" />
                <stop offset="100%" stop-color="#ec4899" />
            </linearGradient>
        `;
        svg.appendChild(defs);

        // Create Progress Path
        const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
        circle.setAttribute('class', 'donut-progress');
        circle.setAttribute('cx', center);
        circle.setAttribute('cy', center);
        circle.setAttribute('r', radius);
        circle.setAttribute('fill', 'none');
        circle.setAttribute('stroke', 'url(#donutGradient)');
        circle.setAttribute('stroke-width', '20');
        circle.setAttribute('stroke-linecap', 'round');
        circle.setAttribute('stroke-dasharray', circumference);
        circle.setAttribute('stroke-dashoffset', offset);
        circle.setAttribute('transform', `rotate(-90 ${center} ${center})`);
        
        // Animation style
        circle.style.transition = 'stroke-dashoffset 0.5s ease-out';

        svg.appendChild(circle);
    }
}

const usagePredictor = new RealTimeUsagePredictor();

// === MAIN FUNCTIONS ===

async function checkAuth() {
    try {
        // Use sessionFetch to ensure we send cookies and ignore API keys
        const response = await sessionFetch(`${API_BASE}/v1/auth/me`);
        
        if (response.ok) {
            currentUser = await response.json();
            displayUserProfile(currentUser);
            loadApiKeys();
            loadStats();
            // Refresh stats periodically
            setInterval(loadStats, 10000); 
        } else {
            // Should be handled by sessionFetch throw, but just in case
            window.location.href = '/';
        }
    } catch (error) {
        console.error('Auth check failed:', error);
        // Error is usually handled by redirect in sessionFetch
    }
}

function displayUserProfile(user) {
    const profileEl = document.getElementById('user-profile');
    const initials = user.email.substring(0, 2).toUpperCase();
    
    // Format member since date
    const memberDate = user.memberSince ? new Date(user.memberSince).toLocaleDateString() : 'Unknown';

    profileEl.innerHTML = `
        <div class="user-avatar">${initials}</div>
        <div class="user-details">
            <h3>${user.email}</h3>
            <p>Member since ${memberDate}</p>
            <p style="font-size: 12px; margin-top: 4px; color: var(--text-muted);">ID: ${user.id}</p>
        </div>
    `;
}

async function loadApiKeys() {
    const listEl = document.getElementById('api-keys-list');
    
    try {
        const response = await sessionFetch(`${API_BASE}/v1/auth/api-keys`);
        const keys = await response.json();

        // Update create button state
        const createBtn = document.getElementById('create-key-btn');
        if (createBtn) {
            if (keys.length >= 2) {
                createBtn.disabled = true;
                createBtn.innerHTML = '<span>Limit Reached (2/2)</span>';
                createBtn.title = "Delete a key to create a new one";
            } else {
                createBtn.disabled = false;
                createBtn.innerHTML = '<span>+ Create New Key</span>';
            }
        }

        // Update total keys counter
        document.getElementById('total-keys').textContent = `${keys.length}/2`;

        if (keys.length === 0) {
            listEl.innerHTML = `
                <div style="text-align: center; padding: 20px; color: var(--text-muted);">
                    No API keys found. Create one to get started!
                </div>
            `;
            return;
        }

        listEl.innerHTML = keys.map(key => {
            const usage = key.usageStats || { totalRequests: 0, requestsToday: 0 };
            const color = getKeyColor(key.id);
            const created = new Date(key.createdAt).toLocaleDateString();
            const lastUsed = key.lastUsedAt ? new Date(key.lastUsedAt).toLocaleString() : 'Never';

            return `
                <div class="api-key-card" style="border-left: 4px solid ${color}">
                    <div class="api-key-header">
                        <div class="api-key-info">
                            <div class="api-key-name">${key.name || 'API Key'}</div>
                            <div class="api-key-value">
                                <span>${key.keyPrefix}••••••••</span>
                                <button class="btn btn-text btn-sm" onclick="copyToClipboard('${key.keyPrefix}')" title="Copy Prefix">Copy</button>
                            </div>
                        </div>
                        <div class="api-key-actions">
                            <button class="btn btn-danger btn-sm" onclick="openDeleteKeyModal('${key.id}')">Delete</button>
                        </div>
                    </div>
                    <div class="api-key-stats">
                        <div class="api-key-stat">
                            <div class="api-key-stat-label">Total Requests</div>
                            <div class="api-key-stat-value">${usage.totalRequests.toLocaleString()}</div>
                        </div>
                        <div class="api-key-stat">
                            <div class="api-key-stat-label">Requests Today</div>
                            <div class="api-key-stat-value">${usage.requestsToday.toLocaleString()}</div>
                        </div>
                        <div class="api-key-stat">
                            <div class="api-key-stat-label">Created</div>
                            <div class="api-key-stat-value" style="font-size: 13px">${created}</div>
                        </div>
                        <div class="api-key-stat">
                            <div class="api-key-stat-label">Last Used</div>
                            <div class="api-key-stat-value" style="font-size: 13px">${lastUsed}</div>
                        </div>
                    </div>
                </div>
            `;
        }).join('');

    } catch (error) {
        console.error('Failed to load keys:', error);
        listEl.innerHTML = `<div class="alert alert-error">Failed to load API keys.</div>`;
    }
}

async function loadStats() {
    try {
        // 1. Get detailed account usage
        const usageRes = await sessionFetch(`${API_BASE}/v1/auth/account-usage`);
        if (usageRes.ok) {
            const usageData = await usageRes.json();
            usagePredictor.updateServerData(
                usageData.accountRequestsUsed,
                usageData.accountRateLimit,
                usageData.remainingRequests
            );
        }

        // 2. Get general user stats (total requests, etc.)
        const statsRes = await sessionFetch(`${API_BASE}/v1/auth/stats`);
        if (statsRes.ok) {
            const stats = await statsRes.json();
            document.getElementById('total-requests').textContent = (stats.totalRequests || 0).toLocaleString();
            document.getElementById('requests-today').textContent = (stats.requestsToday || 0).toLocaleString();
        }
    } catch (error) {
        console.error('Stats update failed:', error);
    }
}

// === ACTION FUNCTIONS ===

async function createApiKey() {
    const nameInput = document.getElementById('key-name');
    const name = nameInput.value.trim();
    const btn = document.querySelector('#create-key-modal .btn-primary');
    
    // Loading state
    const originalText = btn.textContent;
    btn.disabled = true;
    btn.textContent = 'Creating...';

    try {
        const response = await sessionFetch(`${API_BASE}/v1/auth/api-key/create`, {
            method: 'POST',
            body: JSON.stringify({ name: name })
        });

        if (response.ok) {
            const data = await response.json();
            
            closeModal('create-key-modal');
            nameInput.value = '';
            
            // Show success modal with full key
            document.getElementById('new-key-value').value = data.key;
            openModal('show-key-modal');
            
            loadApiKeys(); // Refresh list
        } else {
            const err = await response.json();
            alert(err.detail || 'Failed to create key');
        }
    } catch (error) {
        alert('Error creating key: ' + error.message);
    } finally {
        btn.disabled = false;
        btn.textContent = originalText;
    }
}

function openDeleteKeyModal(id) {
    keyToDelete = id;
    openModal('delete-key-modal');
}

async function confirmDeleteKey() {
    if (!keyToDelete) return;
    
    const btn = document.querySelector('#delete-key-modal .btn-danger');
    btn.disabled = true;
    btn.textContent = 'Deleting...';

    try {
        const response = await sessionFetch(`${API_BASE}/v1/auth/api-key/${keyToDelete}`, {
            method: 'DELETE'
        });

        if (response.ok) {
            closeModal('delete-key-modal');
            loadApiKeys();
            loadStats();
        } else {
            alert('Failed to delete key');
        }
    } catch (error) {
        alert('Error deleting key');
    } finally {
        btn.disabled = false;
        btn.textContent = 'Delete Key';
        keyToDelete = null;
    }
}

async function requestDataExport() {
    try {
        const response = await sessionFetch(`${API_BASE}/v1/auth/export-data`);
        if (response.ok) {
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `synaxic-export-${new Date().toISOString().slice(0,10)}.json`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);
        } else {
            alert('Export failed');
        }
    } catch (e) {
        alert('Export error: ' + e.message);
    }
}

function openDeleteAccountModal() {
    document.getElementById('delete-confirm').value = '';
    openModal('delete-account-modal');
}

async function confirmDeleteAccount() {
    const confirmInput = document.getElementById('delete-confirm');
    if (confirmInput.value !== 'DELETE') {
        alert('Please type DELETE to confirm.');
        return;
    }

    try {
        const response = await sessionFetch(`${API_BASE}/v1/auth/delete-account`, {
            method: 'DELETE'
        });
        
        if (response.ok) {
            window.location.href = '/';
        } else {
            alert('Failed to delete account');
        }
    } catch (e) {
        alert('Error: ' + e.message);
    }
}

async function logout() {
    try {
        await sessionFetch(`${API_BASE}/v1/auth/logout`, { method: 'POST' });
    } catch (e) {
        console.error(e);
    } finally {
        window.location.href = '/';
    }
}

// === UTILS ===

function openModal(id) {
    document.getElementById(id).classList.add('active');
}

function closeModal(id) {
    document.getElementById(id).classList.remove('active');
}

function copyNewKey() {
    const input = document.getElementById('new-key-value');
    input.select();
    navigator.clipboard.writeText(input.value);
    
    const btn = event.target;
    const original = btn.textContent;
    btn.textContent = 'Copied!';
    setTimeout(() => btn.textContent = original, 1500);
}

function copyToClipboard(text) {
    navigator.clipboard.writeText(text);
    // Could show a toast here
}

// === INITIALIZATION ===

document.addEventListener('DOMContentLoaded', () => {
    checkAuth();
    
    // Close modals on outside click
    document.querySelectorAll('.modal').forEach(modal => {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) modal.classList.remove('active');
        });
    });
});

