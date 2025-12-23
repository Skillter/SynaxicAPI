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

        // Ensure background circle exists
        if (!svg.querySelector('circle')) {
             const bg = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
             bg.setAttribute('cx', '150');
             bg.setAttribute('cy', '150');
             bg.setAttribute('r', '120');
             bg.setAttribute('fill', 'none');
             bg.setAttribute('stroke', 'rgba(99, 102, 241, 0.1)');
             bg.setAttribute('stroke-width', '20');
             svg.appendChild(bg);
        }

        // Clear existing dynamic elements
        const existingPath = svg.querySelector('.donut-progress');
        if (existingPath) existingPath.remove();
        const existingDefs = svg.querySelector('defs');
        if (existingDefs) existingDefs.remove();

        // Constants
        const radius = 120;
        const center = 150;
        const circumference = 2 * Math.PI * radius;
        // Ensure offset is never negative and handles 100% correctly
        const validPercentage = Math.min(Math.max(percentage, 0), 100);
        const offset = circumference - (validPercentage / 100) * circumference;

        // Create Gradient Definition
        const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');
        const gradient = document.createElementNS('http://www.w3.org/2000/svg', 'linearGradient');
        gradient.setAttribute('id', 'donutGradient');
        gradient.setAttribute('x1', '0%');
        gradient.setAttribute('y1', '0%');
        gradient.setAttribute('x2', '100%');
        gradient.setAttribute('y2', '0%');

        const stop1 = document.createElementNS('http://www.w3.org/2000/svg', 'stop');
        stop1.setAttribute('offset', '0%');
        stop1.setAttribute('stop-color', '#6366f1');

        const stop2 = document.createElementNS('http://www.w3.org/2000/svg', 'stop');
        stop2.setAttribute('offset', '100%');
        stop2.setAttribute('stop-color', '#ec4899');

        gradient.appendChild(stop1);
        gradient.appendChild(stop2);
        defs.appendChild(gradient);
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
let statsRefreshInterval = null; // Store interval ID for cleanup

// === MAIN FUNCTIONS ===

async function checkAuth() {
    // Clear any existing interval to prevent memory leaks
    if (statsRefreshInterval !== null) {
        clearInterval(statsRefreshInterval);
        statsRefreshInterval = null;
    }

    try {
        // Use /v1/auth/session instead of /me to ensure we get session data
        const response = await sessionFetch(`${API_BASE}/v1/auth/session`);

        if (response.ok) {
            currentUser = await response.json();
            displayUserProfile(currentUser);
            loadApiKeys();
            loadStats();
            // Refresh stats periodically - store interval ID for cleanup
            statsRefreshInterval = setInterval(loadStats, 10000);
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
    profileEl.innerHTML = ''; // Clear existing content

    const initials = user.email ? user.email.substring(0, 2).toUpperCase() : 'U';

    // Format member since date
    let memberDate = 'Unknown';
    if (user.memberSince) {
        // Handle array format [yyyy, mm, dd, ...] or string
        if (Array.isArray(user.memberSince)) {
             memberDate = new Date(user.memberSince[0], user.memberSince[1]-1, user.memberSince[2]).toLocaleDateString();
        } else {
             memberDate = new Date(user.memberSince).toLocaleDateString();
        }
    }

    // Create avatar
    const avatar = document.createElement('div');
    avatar.className = 'user-avatar';
    avatar.textContent = initials;

    // Create details container
    const details = document.createElement('div');
    details.className = 'user-details';

    // Create name element (sanitized)
    const nameEl = document.createElement('h3');
    nameEl.textContent = user.name || user.email;

    // Create email element (sanitized)
    const emailEl = document.createElement('p');
    emailEl.textContent = user.email;

    // Create member since element
    const memberEl = document.createElement('p');
    memberEl.style.fontSize = '12px';
    memberEl.style.marginTop = '4px';
    memberEl.style.color = 'var(--text-muted)';
    memberEl.textContent = 'Member since: ' + memberDate;

    details.appendChild(nameEl);
    details.appendChild(emailEl);
    details.appendChild(memberEl);

    profileEl.appendChild(avatar);
    profileEl.appendChild(details);
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
            listEl.innerHTML = '';
            const emptyDiv = document.createElement('div');
            emptyDiv.style.textAlign = 'center';
            emptyDiv.style.padding = '20px';
            emptyDiv.style.color = 'var(--text-muted)';
            emptyDiv.textContent = 'No API keys found. Create one to get started!';
            listEl.appendChild(emptyDiv);
            return;
        }

        listEl.innerHTML = '';
        keys.forEach(key => {
            const usage = key.usageStats || { totalRequests: 0, requestsToday: 0 };
            const color = getKeyColor(key.id);
            const created = new Date(key.createdAt).toLocaleDateString();
            const lastUsed = key.lastUsedAt ? new Date(key.lastUsedAt).toLocaleString() : 'Never';

            // Create card
            const card = document.createElement('div');
            card.className = 'api-key-card';
            card.style.borderLeft = `4px solid ${color}`;

            // Create header
            const header = document.createElement('div');
            header.className = 'api-key-header';

            // Create info section
            const info = document.createElement('div');
            info.className = 'api-key-info';

            const nameDiv = document.createElement('div');
            nameDiv.className = 'api-key-name';
            nameDiv.textContent = key.name || 'API Key';

            const valueDiv = document.createElement('div');
            valueDiv.className = 'api-key-value';

            const prefixSpan = document.createElement('span');
            prefixSpan.textContent = key.keyPrefix + '••••••••';

            const copyBtn = document.createElement('button');
            copyBtn.className = 'btn btn-text btn-sm';
            copyBtn.textContent = 'Copy';
            copyBtn.title = 'Copy Prefix';
            copyBtn.onclick = () => copyToClipboard(key.keyPrefix);

            valueDiv.appendChild(prefixSpan);
            valueDiv.appendChild(copyBtn);
            info.appendChild(nameDiv);
            info.appendChild(valueDiv);

            // Create actions section
            const actions = document.createElement('div');
            actions.className = 'api-key-actions';

            const deleteBtn = document.createElement('button');
            deleteBtn.className = 'btn btn-danger btn-sm';
            deleteBtn.textContent = 'Delete';
            deleteBtn.onclick = () => openDeleteKeyModal(key.id);

            actions.appendChild(deleteBtn);
            header.appendChild(info);
            header.appendChild(actions);

            // Create stats section
            const stats = document.createElement('div');
            stats.className = 'api-key-stats';

            const createStatItem = (label, value, style = '') => {
                const statDiv = document.createElement('div');
                statDiv.className = 'api-key-stat';

                const labelDiv = document.createElement('div');
                labelDiv.className = 'api-key-stat-label';
                labelDiv.textContent = label;

                const valueDiv = document.createElement('div');
                valueDiv.className = 'api-key-stat-value';
                valueDiv.textContent = value;
                if (style) valueDiv.style.cssText = style;

                statDiv.appendChild(labelDiv);
                statDiv.appendChild(valueDiv);
                return statDiv;
            };

            stats.appendChild(createStatItem('Total Requests', usage.totalRequests.toLocaleString()));
            stats.appendChild(createStatItem('Requests Today', usage.requestsToday.toLocaleString()));
            stats.appendChild(createStatItem('Created', created, 'font-size: 13px'));
            stats.appendChild(createStatItem('Last Used', lastUsed, 'font-size: 13px'));

            card.appendChild(header);
            card.appendChild(stats);
            listEl.appendChild(card);
        });

    } catch (error) {
        console.error('Failed to load keys:', error);
        listEl.innerHTML = '';
        const errorDiv = document.createElement('div');
        errorDiv.className = 'alert alert-error';
        errorDiv.textContent = 'Failed to load API keys.';
        listEl.appendChild(errorDiv);
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

function openCreateKeyModal() {
    openModal('create-key-modal');
}

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
            alert(err.detail || err.error || 'Failed to create key');
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
