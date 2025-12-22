// ... (Keep existing imports/variables) ...
const API_BASE = window.location.origin;
let currentUser = null;
let keyToDelete = null;

// ... (Keep color palette logic) ...
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

// ... (Keep sessionFetch and RealTimeUsagePredictor) ...
async function sessionFetch(url, options = {}) {
    const defaultOptions = {
        credentials: 'include',
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        }
    };

    const finalOptions = { ...defaultOptions, ...options };
    if (finalOptions.headers['Authorization']) {
        delete finalOptions.headers['Authorization'];
    }

    try {
        const response = await fetch(url, finalOptions);

        if (response.status === 401 || response.status === 403) {
            console.log('Session expired, redirecting...');
            window.location.href = '/'; 
            return new Promise(() => {});
        }

        const limit = response.headers.get('X-RateLimit-Limit');
        const remaining = response.headers.get('X-RateLimit-Remaining');
        if (limit && remaining && usagePredictor) {
            usagePredictor.updateFromRateLimitHeaders(parseInt(limit), parseInt(remaining));
        }

        return response;
    } catch (error) {
        console.error("Fetch error:", error);
        throw error;
    }
}

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
        if (limit > 20000) return; 
        const used = Math.max(0, limit - remaining);
        if (used >= this.used) {
            this.limit = limit;
            this.remaining = remaining;
            this.used = used;
            this.lastUpdate = Date.now();
            this.updateUI();
        }
    }

    updateUI() {
        const usedEl = document.getElementById('account-used');
        const limitEl = document.getElementById('account-limit');
        const percentEl = document.getElementById('donut-percentage');

        if (usedEl) usedEl.textContent = this.used.toLocaleString();
        if (limitEl) limitEl.textContent = this.limit.toLocaleString();
        
        const percent = this.limit > 0 ? (this.used / this.limit) * 100 : 0;
        if (percentEl) percentEl.textContent = `${percent.toFixed(1)}%`;

        this.renderDonut(percent);
    }

    renderDonut(percentage) {
        const svg = document.getElementById('donut-chart');
        if (!svg) return;

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

        const existingPath = svg.querySelector('.donut-progress');
        if (existingPath) existingPath.remove();
        const existingDefs = svg.querySelector('defs');
        if (existingDefs) existingDefs.remove();

        const radius = 120;
        const center = 150;
        const circumference = 2 * Math.PI * radius;
        const validPercentage = Math.min(Math.max(percentage, 0), 100);
        const offset = circumference - (validPercentage / 100) * circumference;

        const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');
        defs.innerHTML = `
            <linearGradient id="donutGradient" x1="0%" y1="0%" x2="100%" y2="0%">
                <stop offset="0%" stop-color="#6366f1" />
                <stop offset="100%" stop-color="#ec4899" />
            </linearGradient>
        `;
        svg.appendChild(defs);

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
        
        circle.style.transition = 'stroke-dashoffset 0.5s ease-out';

        svg.appendChild(circle);
    }
}

const usagePredictor = new RealTimeUsagePredictor();

// ... (checkAuth, displayUserProfile, loadApiKeys remain same) ...
async function checkAuth() {
    const timeout = new Promise((_, reject) => 
        setTimeout(() => reject(new Error('Request timed out')), 8000)
    );

    try {
        const response = await Promise.race([
            sessionFetch(`${API_BASE}/v1/auth/session`),
            timeout
        ]);
        
        if (response.status === 204) {
            window.location.href = '/';
            return;
        }

        if (response.ok) {
            currentUser = await response.json();
            displayUserProfile(currentUser);
            loadApiKeys();
            loadStats();
            setInterval(loadStats, 10000); 
        } else {
            window.location.href = '/';
        }
    } catch (error) {
        console.error('Auth check failed:', error);
        document.getElementById('user-profile').innerHTML = `
            <div class="alert alert-error">
                Failed to load profile (${error.message}). <br>
                <a href="/" style="color: white; text-decoration: underline; margin-top: 8px; display: inline-block;">Return Home</a>
            </div>
        `;
    }
}

function displayUserProfile(user) {
    const profileEl = document.getElementById('user-profile');
    const initials = user.email ? user.email.substring(0, 2).toUpperCase() : 'U';
    
    let memberDate = 'Unknown';
    if (user.memberSince) {
        if (Array.isArray(user.memberSince)) {
             memberDate = new Date(user.memberSince[0], user.memberSince[1]-1, user.memberSince[2]).toLocaleDateString();
        } else {
             memberDate = new Date(user.memberSince).toLocaleDateString();
        }
    }

    profileEl.innerHTML = `
        <div class="user-avatar">${initials}</div>
        <div class="user-details">
            <h3>${user.name || user.email}</h3>
            <p>${user.email}</p>
            <p style="font-size: 12px; margin-top: 4px; color: var(--text-muted);">Member since: ${memberDate}</p>
        </div>
    `;
}

async function loadApiKeys() {
    const listEl = document.getElementById('api-keys-list');
    
    try {
        const response = await sessionFetch(`${API_BASE}/v1/auth/api-keys`);
        const keys = await response.json();

        const createBtn = document.getElementById('create-key-btn');
        if (createBtn) {
            if (keys.length >= 2) {
                createBtn.disabled = true;
                createBtn.innerHTML = '<span>Limit Reached (2/2)</span>';
            } else {
                createBtn.disabled = false;
                createBtn.innerHTML = '<span>+ Create New Key</span>';
            }
        }

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
                                <button class="btn btn-text btn-sm" onclick="copyToClipboard('${key.keyPrefix}')">Copy</button>
                            </div>
                        </div>
                        <button class="btn btn-danger btn-sm" onclick="openDeleteKeyModal('${key.id}')">Delete</button>
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

// === UPDATED LOAD STATS ===
async function loadStats() {
    try {
        const usageRes = await sessionFetch(`${API_BASE}/v1/auth/account-usage`);
        if (usageRes.ok) {
            const usageData = await usageRes.json();
            usagePredictor.updateServerData(
                usageData.accountRequestsUsed,
                usageData.accountRateLimit,
                usageData.remainingRequests
            );
        }

        const statsRes = await sessionFetch(`${API_BASE}/v1/auth/stats`);
        if (statsRes.ok) {
            const stats = await statsRes.json();
            // Updated IDs to match dashboard.html and avoid collision with main.js
            document.getElementById('user-total-requests').textContent = (stats.totalRequests || 0).toLocaleString();
            document.getElementById('user-requests-today').textContent = (stats.requestsToday || 0).toLocaleString();
        }
    } catch (error) {
        console.error('Stats update failed:', error);
    }
}

// ... (Rest of modal/action functions remain same) ...
function openModal(id) {
    const modal = document.getElementById(id);
    if (modal) {
        modal.style.display = 'flex';
        setTimeout(() => modal.classList.add('active'), 10);
    }
}

function closeModal(id) {
    const modal = document.getElementById(id);
    if (modal) {
        modal.classList.remove('active');
        setTimeout(() => modal.style.display = 'none', 300);
    }
}

function openCreateKeyModal() {
    openModal('create-key-modal');
}

async function createApiKey() {
    const nameInput = document.getElementById('key-name');
    const btn = document.querySelector('#create-key-modal .btn-primary');
    
    if (!btn) return;
    
    const originalText = btn.textContent;
    btn.disabled = true;
    btn.textContent = 'Creating...';

    try {
        const response = await sessionFetch(`${API_BASE}/v1/auth/api-key/create`, {
            method: 'POST',
            body: JSON.stringify({ name: nameInput.value.trim() })
        });

        if (response.ok) {
            const data = await response.json();
            closeModal('create-key-modal');
            nameInput.value = '';
            document.getElementById('new-key-value').value = data.key;
            openModal('show-key-modal');
            showToast('success', 'Success', 'API key created successfully');
            loadApiKeys();
        } else {
            const err = await response.json();
            showToast('error', 'Error', err.error || err.detail || 'Failed to create key');
        }
    } catch (error) {
        showToast('error', 'Error', 'Connection failed');
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
    if (!btn) return;

    const originalText = btn.textContent;
    btn.disabled = true;
    btn.textContent = 'Deleting...';

    try {
        const response = await sessionFetch(`${API_BASE}/v1/auth/api-key/${keyToDelete}`, {
            method: 'DELETE'
        });

        if (response.ok) {
            closeModal('delete-key-modal');
            showToast('success', 'Deleted', 'API key deleted successfully');
            loadApiKeys();
            loadStats();
        } else {
            let errMsg = 'Failed to delete key';
            try {
                const errData = await response.json();
                if (errData.detail) errMsg = errData.detail;
            } catch (e) {}
            
            showToast('error', 'Error', errMsg);
        }
    } catch (error) {
        console.error("Delete error:", error);
        showToast('error', 'Error', 'Failed to delete key. Check connection.');
    } finally {
        btn.disabled = false;
        btn.textContent = originalText;
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
            showToast('success', 'Export', 'Data export downloaded');
        } else {
            showToast('error', 'Error', 'Export failed');
        }
    } catch (e) {
        showToast('error', 'Error', 'Export connection failed');
    }
}

function openDeleteAccountModal() {
    document.getElementById('delete-confirm').value = '';
    openModal('delete-account-modal');
}

async function confirmDeleteAccount() {
    const confirmInput = document.getElementById('delete-confirm');
    if (confirmInput.value !== 'DELETE') {
        showToast('warning', 'Confirm', 'Please type DELETE to confirm.');
        return;
    }

    try {
        const response = await sessionFetch(`${API_BASE}/v1/auth/delete-account`, {
            method: 'DELETE'
        });
        
        if (response.ok) {
            window.location.href = '/';
        } else {
            showToast('error', 'Error', 'Failed to delete account');
        }
    } catch (e) {
        showToast('error', 'Error', 'Delete connection failed');
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

function copyNewKey() {
    const input = document.getElementById('new-key-value');
    input.select();
    navigator.clipboard.writeText(input.value);
    const btn = event.target;
    btn.textContent = 'Copied!';
    setTimeout(() => btn.textContent = 'Copy', 1500);
}

function copyToClipboard(text) {
    navigator.clipboard.writeText(text);
    showToast('success', 'Copied', 'Copied to clipboard');
}

function showToast(type, title, message) {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    
    const icons = {
        success: '✅',
        error: '❌',
        warning: '⚠️',
        info: 'ℹ️'
    };

    toast.innerHTML = `
        <div class="toast-icon">${icons[type] || icons.info}</div>
        <div class="toast-content">
            <div class="toast-title">${title}</div>
            <div class="toast-message">${message}</div>
        </div>
    `;

    document.body.appendChild(toast);

    requestAnimationFrame(() => {
        toast.classList.add('show');
    });

    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => {
            if (toast.parentNode) toast.parentNode.removeChild(toast);
        }, 300);
    }, 3000);
}

document.addEventListener('DOMContentLoaded', () => {
    checkAuth();
    
    document.querySelectorAll('.modal').forEach(modal => {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) closeModal(modal.id);
        });
    });
});

