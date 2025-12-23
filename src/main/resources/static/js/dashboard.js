const API_BASE = window.location.origin;
let currentUser = null;
let keyToDelete = null;
let apiKeyColors = new Map(); // Store consistent colors for API keys

// Create authenticated fetch headers
function getAuthHeaders() {
    const headers = {};

    // Check if we're using API key authentication (look for API key in URL or localStorage)
    const urlParams = new URLSearchParams(window.location.search);
    const apiKey = urlParams.get('api_key') || localStorage.getItem('apiKey');

    if (apiKey) {
        headers['Authorization'] = `ApiKey ${apiKey}`;
    }

    return headers;
}

// Create authenticated fetch function
async function authenticatedFetch(url, options = {}) {
    const authHeaders = getAuthHeaders();
    const headers = {
        ...authHeaders,
        ...(options.headers || {})
    };

    const apiKey = authHeaders.Authorization;
    const response = await fetch(url, {
        ...options,
        headers,
        credentials: apiKey ? 'omit' : 'include'
    });

    // Extract rate limit headers for real-time prediction
    if (response.ok) {
        const rateLimit = response.headers.get('X-RateLimit-Limit');
        const remaining = response.headers.get('X-RateLimit-Remaining');

        if (rateLimit && remaining) {
            updateRealTimeRateLimit(parseInt(rateLimit), parseInt(remaining));
        }
    }

    return response;
}

// Color palette for API keys
const colorPalette = [
    '#6366f1', // Primary blue
    '#8b5cf6', // Purple
    '#06b6d4', // Cyan
    '#10b981', // Emerald
    '#f59e0b', // Amber
    '#ef4444', // Red
    '#ec4899', // Pink
    '#84cc16', // Lime
    '#f97316', // Orange
    '#14b8a6'  // Teal
];

// Generate consistent color for API key based on its ID
function getKeyColor(keyId) {
    if (!keyId || typeof keyId !== 'string') {
        keyId = String(keyId || 'unknown');
    }
    if (!apiKeyColors.has(keyId)) {
        const hash = keyId.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
        const colorIndex = hash % colorPalette.length;
        apiKeyColors.set(keyId, colorPalette[colorIndex]);
    }
    return apiKeyColors.get(keyId);
}

// Real-time usage prediction system
class RealTimeUsagePredictor {
    constructor() {
        this.rateLimit = 10000; // Default rate limit
        this.lastServerUpdate = Date.now();
        this.serverRemainingRequests = 10000; // Actual value from server
        this.serverUsedRequests = 0; // Actual value from server
        this.currentRemainingRequests = 10000; // Client-side predicted value
        this.currentUsedRequests = 0; // Client-side predicted value

        // Countdown calculation variables
        this.countdownInterval = null;
        this.serverSyncInterval = null;
        this.countdownIntervalMs = 1000; // Update every second for smooth display
        this.serverSyncIntervalMs = 10000; // Sync with server every 10 seconds

        this.isVisible = !document.hidden;
        this.invalidDataCount = 0;
        this.maxInvalidData = 5;

        // Track visibility changes
        document.addEventListener('visibilitychange', () => {
            this.isVisible = !document.hidden;
            if (this.isVisible) {
                this.startCountdown();
                this.startServerSync();
            } else {
                this.stopCountdown();
                this.stopServerSync();
            }
        });
    }

    updateServerData(used, limit, remaining = null) {
        // Validate server data
        if (typeof used !== 'number' || used < 0 || used > limit) {
            console.warn('Invalid used value from server:', { used, limit });
            this.invalidDataCount++;
            if (this.invalidDataCount >= this.maxInvalidData) {
                this.resetToSafeDefaults();
            }
            return;
        }

        if (typeof limit !== 'number' || limit <= 0) {
            console.warn('Invalid limit value from server:', limit);
            this.invalidDataCount++;
            if (this.invalidDataCount >= this.maxInvalidData) {
                this.resetToSafeDefaults();
            }
            return;
        }

        // Valid data received, reset invalid counter
        this.invalidDataCount = 0;

        this.usedRequests = used;
        this.rateLimit = limit;
        this.lastUpdate = Date.now();

        if (remaining !== null && typeof remaining === 'number' && remaining >= 0) {
            this.remainingRequests = Math.min(remaining, limit - used);
        } else {
            this.remainingRequests = Math.max(0, limit - used);
        }

        // Calculate request rate based on change since last update
        if (this.previousUsed !== undefined && typeof this.previousUsed === 'number') {
            const timeDelta = (this.lastUpdate - this.previousTimestamp) / 1000; // seconds
            const usageDelta = used - this.previousUsed;
            if (timeDelta > 0 && usageDelta >= 0) {
                // Smooth the request rate with exponential moving average
                const alpha = 0.3; // Smoothing factor
                const instantRate = usageDelta / timeDelta;
                this.requestRate = Math.max(0, this.requestRate * (1 - alpha) + instantRate * alpha);
            }
        }

        this.previousUsed = used;
        this.previousTimestamp = this.lastUpdate;
        console.log('Updated server data:', { used, limit, remaining: this.remainingRequests, requestRate: this.requestRate });
    }

    resetToSafeDefaults() {
        console.warn('Resetting predictor to safe defaults due to invalid data');
        this.usedRequests = 0;
        this.remainingRequests = this.rateLimit;
        this.requestRate = 0;
        this.lastUpdate = Date.now();
        this.previousUsed = 0;
        this.previousTimestamp = Date.now();
        this.invalidDataCount = 0;

        // Update UI with safe values
        this.updateUI({
            used: 0,
            remaining: this.rateLimit,
            limit: this.rateLimit,
            percentage: 0,
            requestRate: 0,
            timeSinceUpdate: 0
        });
    }

    updateFromRateLimitHeaders(limit, remaining) {
        // Validate and sanitize header data
        if (!limit || limit <= 0 || !Number.isInteger(limit)) {
            console.warn('Invalid rate limit from headers:', limit);
            return; // Ignore invalid data
        }

        if (!remaining || remaining < 0 || remaining > limit) {
            console.warn('Invalid remaining requests from headers:', { limit, remaining });
            return; // Ignore invalid data
        }

        // Only update if this is for the account-level tier (10000 requests)
        // Skip lower-tier limits like static resources (1000 requests)
        if (limit < 5000) {
            console.log('Ignoring lower-tier rate limit:', limit);
            return;
        }

        const calculatedUsed = limit - remaining;

        // Validate calculated used value
        if (calculatedUsed < 0 || calculatedUsed > limit) {
            console.warn('Invalid calculated usage from headers:', { limit, remaining, calculatedUsed });
            return;
        }

        // Update server data and reset client prediction to match
        // Ensure all values are integers
        this.rateLimit = Math.floor(limit);
        this.serverRemainingRequests = Math.floor(remaining);
        this.serverUsedRequests = Math.floor(calculatedUsed);
        this.currentRemainingRequests = Math.floor(remaining);
        this.currentUsedRequests = Math.floor(calculatedUsed);
        this.lastServerUpdate = Date.now();

        console.log('Updated from headers:', {
            limit,
            remaining,
            used: this.serverUsedRequests,
            countdownRate: this.getCountdownRatePerSecond()
        });

        // Restart countdown with new data
        this.restartCountdown();
    }

    // Calculate how many requests are recovered per second
    getCountdownRatePerSecond() {
        // Rate limit refreshes per hour, so calculate recovery rate
        return this.rateLimit / 3600; // requests per second
    }

    // Calculate interval in milliseconds for countdown to decrease by 1
    getCountdownIntervalMs() {
        const ratePerSecond = this.getCountdownRatePerSecond();
        if (ratePerSecond <= 0) return 1000; // Default to 1 second if rate is 0

        // Calculate how often to decrement by 1
        // If rate is 2.77 req/sec, then 1 / 2.77 = 0.36 seconds = 360ms
        return Math.max(100, Math.min(10000, 1000 / ratePerSecond)); // Clamp between 100ms and 10s
    }

    getCurrentState() {
        // Ensure all request counts are integers
        const used = Math.max(0, Math.min(this.rateLimit, Math.floor(this.currentUsedRequests)));
        const remaining = Math.max(0, Math.min(this.rateLimit, Math.floor(this.currentRemainingRequests)));
        const limit = Math.floor(this.rateLimit);

        return {
            used: used,
            remaining: remaining,
            limit: limit,
            percentage: Math.min(100, Math.max(0, (used / limit) * 100)),
            countdownRate: this.getCountdownRatePerSecond(),
            countdownInterval: this.getCountdownIntervalMs(),
            timeSinceServerUpdate: (Date.now() - this.lastServerUpdate) / 1000
        };
    }

    // Countdown system methods
    startCountdown() {
        if (this.countdownInterval) return;

        const intervalMs = this.getCountdownIntervalMs();
        console.log(`Starting countdown: decreasing by 1 every ${intervalMs}ms`);

        this.countdownInterval = setInterval(() => {
            if (this.isVisible && this.currentRemainingRequests > 0) {
                // Decrease used requests by 1 (simulating quota recovery)
                // Always use integers for request counts
                this.currentUsedRequests = Math.max(0, Math.floor(this.currentUsedRequests) - 1);
                this.currentRemainingRequests = Math.min(this.rateLimit, Math.floor(this.rateLimit - this.currentUsedRequests));

                // Update UI
                this.updateUI(this.getCurrentState());
            }
        }, intervalMs);
    }

    stopCountdown() {
        if (this.countdownInterval) {
            clearInterval(this.countdownInterval);
            this.countdownInterval = null;
            console.log('Stopped countdown');
        }
    }

    restartCountdown() {
        this.stopCountdown();
        if (this.isVisible) {
            this.startCountdown();
        }
    }

    // Server sync methods
    startServerSync() {
        if (this.serverSyncInterval) return;

        console.log(`Starting server sync: every ${this.serverSyncIntervalMs}ms`);
        this.serverSyncInterval = setInterval(() => {
            if (this.isVisible) {
                this.syncWithServer();
            }
        }, this.serverSyncIntervalMs);
    }

    stopServerSync() {
        if (this.serverSyncInterval) {
            clearInterval(this.serverSyncInterval);
            this.serverSyncInterval = null;
            console.log('Stopped server sync');
        }
    }

    async syncWithServer() {
        try {
            console.log('Syncing usage data with server...');
            // Try to get current hour usage from account usage endpoint
            const response = await authenticatedFetch(`${API_BASE}/v1/account/usage`);

            if (response.ok) {
                const usageData = await response.json();
                console.log('Account usage sync response:', usageData);

                // Update server data with current hour usage
                if (usageData.accountRequestsUsed !== undefined && usageData.accountRateLimit !== undefined) {
                    const serverUsed = Math.floor(usageData.accountRequestsUsed);
                    const serverLimit = Math.floor(usageData.accountRateLimit);
                    const serverRemaining = Math.max(0, serverLimit - serverUsed);

                    this.serverUsedRequests = serverUsed;
                    this.serverRemainingRequests = serverRemaining;
                    this.rateLimit = serverLimit;
                    this.lastServerUpdate = Date.now();

                    console.log(`Updated from account usage: ${serverUsed}/${serverLimit} used, ${serverRemaining} remaining`);

                    // Smoothly adjust client prediction to match server
                    this.adjustClientToServerValues();
                } else {
                    console.log('Account usage endpoint missing expected fields, relying on rate limit headers');
                }
            } else {
                console.log('Account usage endpoint failed, relying on rate limit headers for accuracy');
            }
        } catch (error) {
            console.error('Server sync failed:', error);
            console.log('Relying on rate limit headers for accuracy');
        }
    }

    adjustClientToServerValues() {
        // Calculate the difference between client prediction and server reality
        const usedDiff = this.serverUsedRequests - this.currentUsedRequests;
        const remainingDiff = this.serverRemainingRequests - this.currentRemainingRequests;

        // If difference is small, snap to server values immediately
        if (Math.abs(usedDiff) <= 5) {
            this.currentUsedRequests = Math.floor(this.serverUsedRequests);
            this.currentRemainingRequests = Math.floor(this.serverRemainingRequests);
            console.log('Client prediction synced to server values (small difference)');
        }
        // If difference is larger, gradually adjust to avoid jarring changes
        else if (Math.abs(usedDiff) > 0) {
            const adjustmentRate = 0.3; // Adjust 30% of the difference each sync
            this.currentUsedRequests = Math.floor(this.currentUsedRequests + (usedDiff * adjustmentRate));
            this.currentRemainingRequests = Math.floor(this.rateLimit - this.currentUsedRequests);
            console.log(`Client prediction adjusting to server: ${Math.floor(usedDiff)} difference, ${adjustmentRate * 100}% adjustment`);
        }
    }

    // Legacy method names for compatibility
    startPrediction() {
        this.startCountdown();
        this.startServerSync();
    }

    stopPrediction() {
        this.stopCountdown();
        this.stopServerSync();
    }

    // Initialize the countdown system
    initialize() {
        console.log('Initializing real-time countdown system...');
        if (this.isVisible) {
            this.startCountdown();
            this.startServerSync();
        }
    }

    updateUI(state) {
        // Update the usage counter
        const usedElement = document.getElementById('account-used');
        const limitElement = document.getElementById('account-limit');
        const percentageElement = document.getElementById('donut-percentage');

        if (usedElement) usedElement.textContent = formatNumber(state.used);
        if (limitElement) limitElement.textContent = formatNumber(state.limit);
        if (percentageElement) percentageElement.textContent = `${state.percentage.toFixed(1)}%`;

        // Update the donut chart
        this.updateDonutChart(state.used, state.limit);

        // Log countdown info for debugging
        if (state.timeSinceServerUpdate > 10) {
            console.log('Countdown active:', {
                used: state.used,
                remaining: state.remaining,
                rate: state.countdownRate.toFixed(2) + ' req/sec',
                interval: state.countdownInterval + 'ms',
                timeSinceUpdate: state.timeSinceServerUpdate.toFixed(1) + 's'
            });
        }
    }

    updateDonutChart(used, limit) {
        const svg = document.getElementById('donut-chart');
        if (!svg) return;

        const centerX = 150;
        const centerY = 150;
        const outerRadius = 130;
        const innerRadius = 90;

        const percentage = Math.min((used / limit), 1);
        const dashArray = 2 * Math.PI * outerRadius;
        const dashOffset = dashArray * (1 - percentage);

        const svgContent = `
            <circle cx="${centerX}" cy="${centerY}" r="${outerRadius}"
                    fill="none" stroke="rgba(156, 163, 175, 0.2)"
                    stroke-width="${outerRadius - innerRadius}"/>
            <circle cx="${centerX}" cy="${centerY}" r="${outerRadius}"
                    fill="none" stroke="url(#gradient1)"
                    stroke-width="${outerRadius - innerRadius}"
                    stroke-dasharray="${dashArray}"
                    stroke-dashoffset="${dashOffset}"
                    stroke-linecap="round"
                    style="transition: stroke-dashoffset 0.3s ease;"/>
            <defs>
                <linearGradient id="gradient1" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" style="stop-color:#6366f1;stop-opacity:1" />
                    <stop offset="100%" style="stop-color:#8b5cf6;stop-opacity:1" />
                </linearGradient>
            </defs>
        `;

        svg.innerHTML = svgContent;
    }

    startPeriodicServerUpdates() {
        if (this.serverUpdateInterval) return;

        this.serverUpdateInterval = setInterval(async () => {
            if (this.isVisible && currentUser) {
                try {
                    const usageResponse = await authenticatedFetch(`${API_BASE}/v1/auth/account-usage`);
                    if (usageResponse.ok) {
                        const accountUsage = await usageResponse.json();
                        this.updateServerData(
                            accountUsage.accountRequestsUsed,
                            accountUsage.accountRateLimit,
                            accountUsage.remainingRequests
                        );
                    }
                } catch (error) {
                    console.warn('Failed to update usage from server:', error);
                }
            }
        }, 30000); // Update from server every 30 seconds
    }

    stopPeriodicServerUpdates() {
        if (this.serverUpdateInterval) {
            clearInterval(this.serverUpdateInterval);
            this.serverUpdateInterval = null;
        }
    }

    destroy() {
        this.stopPrediction();
        this.stopPeriodicServerUpdates();
    }
}

// Global predictor instance
let usagePredictor = null;

// Debug mode for troubleshooting (set to true to enable detailed logging)
const DEBUG_PREDICTOR = false;

// Update function for rate limit headers
function updateRealTimeRateLimit(limit, remaining) {
    if (usagePredictor) {
        usagePredictor.updateFromRateLimitHeaders(limit, remaining);
    }
}

// Debug function to manually trigger predictor reset
function resetUsagePredictor() {
    if (usagePredictor) {
        usagePredictor.resetToSafeDefaults();
        console.log('Usage predictor manually reset');
    }
}

// Toast notification system
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
        <button class="toast-close" onclick="closeToast(this)">&times;</button>
    `;

    document.body.appendChild(toast);

    setTimeout(() => {
        closeToast(toast.querySelector('.toast-close'));
    }, 5000);
}

function closeToast(button) {
    const toast = button.closest('.toast');
    toast.classList.add('hiding');
    setTimeout(() => {
        toast.remove();
    }, 300);
}

// Check authentication on load
async function checkAuth() {
    try {
        console.log('Starting authentication check...');

        // Add timeout to prevent infinite loading
        const timeoutPromise = new Promise((_, reject) =>
            setTimeout(() => reject(new Error('Authentication check timeout')), 5000)
        );

        // For session check, always use credentials-based auth (not API key)
        const responsePromise = fetch(`${API_BASE}/v1/auth/session`, {
            credentials: 'include',
            redirect: 'manual' // Prevent CORS errors from OAuth redirects
        });

        const response = await Promise.race([responsePromise, timeoutPromise]);
        console.log('Session check response status:', response.status);

        // Handle redirect (not authenticated)
        if (response.type === 'opaqueredirect' || response.status === 0) {
            console.log('Redirect detected - not authenticated');
            window.location.href = '/';
            return;
        }

        if (!response.ok) {
            console.error('Session check failed with status:', response.status);
            if (response.status === 401) {
                // Not authenticated - redirect to login
                window.location.href = '/';
                return;
            }
            throw new Error(`Session check failed: ${response.status}`);
        }

        currentUser = await response.json();
        console.log('User data received:', currentUser);

        if (currentUser) {
            console.log('Calling displayUserProfile with:', currentUser);
            displayUserProfile(currentUser);
            console.log('Calling loadApiKeys...');
            loadApiKeys();
            console.log('Calling loadStats...');
            loadStats(); // Load initial stats
            // Note: Real-time updates are now handled by the predictor
        } else {
            console.error('No user data received');
            window.location.href = '/';
        }
    } catch (error) {
        console.error('Auth check failed:', error);

        // Show error state instead of infinite loading
        let errorMessage = 'Authentication failed. Please <a href="/" style="color: var(--primary);">login again</a>.';

        if (error.message === 'Authentication check timeout') {
            errorMessage = 'Authentication check timed out. Please check your connection and <a href="/" style="color: var(--primary);">try again</a>.';
        }

        document.getElementById('user-profile').innerHTML = `
            <div class="alert alert-error">${errorMessage}</div>
        `;
        document.getElementById('api-keys-list').innerHTML = `
            <div class="alert alert-error">Failed to load API keys. Please refresh the page or <a href="/" style="color: var(--primary);">login again</a>.</div>
        `;
    }
}

// Display user profile
function displayUserProfile(user) {
    const initials = user.name ? user.name.split(' ').map(n => n[0]).join('').toUpperCase().substring(0, 2) : user.email[0].toUpperCase();

    document.getElementById('user-profile').innerHTML = `
        <div class="user-avatar">${initials}</div>
        <div class="user-details">
            <div class="user-name">${user.name || 'User'}</div>
            <div class="user-email">${user.email}</div>
        </div>
        <button class="btn btn-danger" onclick="logout()">Logout</button>
    `;
}

// Load API keys
async function loadApiKeys() {
    const container = document.getElementById('api-keys-list');
    container.innerHTML = '<div class="loading-container"><div class="loading-spinner"></div><div class="loading-text">Loading API keys...</div></div>';

    try {
        // Add timeout to prevent infinite loading
        const timeoutPromise = new Promise((_, reject) =>
            setTimeout(() => reject(new Error('Request timeout')), 10000)
        );

        const response = await Promise.race([
            authenticatedFetch(`${API_BASE}/v1/auth/api-keys`),
            timeoutPromise
        ]);

        if (!response.ok) {
            console.error('API keys response status:', response.status);
            throw new Error(`Failed to load API keys: ${response.status}`);
        }

        const keys = await response.json();

        // Update the "Create New Key" button based on key count
        const createKeyBtn = document.querySelector('.section-header button.btn-primary');
        if (createKeyBtn) {
            if (keys.length >= 2) {
                createKeyBtn.disabled = true;
                createKeyBtn.title = 'Maximum 2 keys per account. Delete a key to create a new one.';
                createKeyBtn.innerHTML = '<span>Maximum Keys Reached (2/2)</span>';
            } else {
                createKeyBtn.disabled = false;
                createKeyBtn.title = '';
                createKeyBtn.innerHTML = '<span>Create New Key</span>';
            }
        }

        if (keys.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <div class="empty-state-icon">🔑</div>
                    <div class="empty-state-title">No API Keys Yet</div>
                    <div class="empty-state-text">Create your first API key to start using the Synaxic API (Maximum 2 keys per account)</div>
                    <button class="btn btn-primary" onclick="openCreateKeyModal()">Create Your First Key</button>
                </div>
            `;
            return;
        }

        container.innerHTML = keys.map(key => renderApiKeyCard(key)).join('');
        document.getElementById('total-keys').textContent = keys.length + '/2';
    } catch (error) {
        console.error('Error loading API keys:', error);
        let errorMessage = 'Failed to load API keys. Please refresh the page.';

        if (error.message === 'Request timeout') {
            errorMessage = 'Request timed out. Please check your connection and refresh the page.';
        } else if (error.message.includes('401')) {
            errorMessage = 'Authentication expired. Please <a href="/" style="color: var(--primary);">login again</a>.';
        } else if (error.message.includes('403')) {
            errorMessage = 'Access denied. Please <a href="/" style="color: var(--primary);">login again</a>.';
        }

        container.innerHTML = `<div class="alert alert-error">${errorMessage}</div>`;
        showToast('error', 'Error', errorMessage);
    }
}

// Render API key card
function renderApiKeyCard(key) {
    // Fix date parsing - handle both timestamp (seconds) and ISO string
    let createdAt;
    if (typeof key.createdAt === 'number') {
        // If it's a large number, it's likely milliseconds already
        // If it's a smaller number (like 17xxxxxxx), it's seconds and needs conversion
        createdAt = key.createdAt > 1000000000000 ? key.createdAt : key.createdAt * 1000;
    } else {
        createdAt = new Date(key.createdAt).getTime();
    }
    const createdDate = new Date(createdAt).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
    });

    let lastUsedAt;
    if (key.lastUsedAt) {
        if (typeof key.lastUsedAt === 'number') {
            lastUsedAt = key.lastUsedAt > 1000000000000 ? key.lastUsedAt : key.lastUsedAt * 1000;
        } else {
            lastUsedAt = new Date(key.lastUsedAt).getTime();
        }
    }
    const lastUsedDate = key.lastUsedAt
        ? new Date(lastUsedAt).toLocaleString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        })
        : 'Never';

    const maskedKey = key.keyPrefix + '***' + key.keySuffix;
    const usage = key.usageStats || {};
    const keyColor = getKeyColor(key.id);

    return `
        <div class="api-key-card">
            <div class="api-key-color-indicator" style="background-color: ${keyColor}"></div>
            <div class="api-key-header">
                <div class="api-key-info">
                    <div class="api-key-name">${key.name || 'API Key'}</div>
                    <div class="api-key-value">
                        ${maskedKey}
                    </div>
                </div>
                <div class="api-key-actions">
                    <button class="btn btn-icon btn-danger" onclick="openDeleteKeyModal('${key.id}')" title="Delete">
                        🗑️
                    </button>
                </div>
            </div>
            <div class="api-key-stats">
                <div class="api-key-stat">
                    <div class="api-key-stat-label">Total Requests</div>
                    <div class="api-key-stat-value">${(usage.totalRequests || 0).toLocaleString()}</div>
                </div>
                <div class="api-key-stat">
                    <div class="api-key-stat-label">Today</div>
                    <div class="api-key-stat-value">${(usage.requestsToday || 0).toLocaleString()}</div>
                </div>
                <div class="api-key-stat">
                    <div class="api-key-stat-label">Last Used</div>
                    <div class="api-key-stat-value" style="font-size: 12px;">${lastUsedDate}</div>
                </div>
                <div class="api-key-stat">
                    <div class="api-key-stat-label">Created</div>
                    <div class="api-key-stat-value" style="font-size: 12px;">${createdDate}</div>
                </div>
            </div>
        </div>
    `;
}

// Load account usage and stats
async function loadStats() {
    try {
        // Load account usage (now with real-time data)
        const usageResponse = await authenticatedFetch(`${API_BASE}/v1/auth/account-usage`);

        if (usageResponse.ok) {
            const accountUsage = await usageResponse.json();
            displayAccountUsage(accountUsage);
        } else {
            console.error('Failed to load account usage:', usageResponse.status);
        }

        // Load legacy stats
        const statsResponse = await authenticatedFetch(`${API_BASE}/v1/auth/stats`);

        if (statsResponse.ok) {
            const stats = await statsResponse.json();
            document.getElementById('total-requests').textContent = (stats.totalRequests || 0).toLocaleString();
            document.getElementById('requests-today').textContent = (stats.requestsToday || 0).toLocaleString();
        }
    } catch (error) {
        console.error('Error loading stats:', error);
    }
}

// Refresh account usage more frequently to show real-time updates
let statsRefreshInterval;

function startStatsRefresh() {
    // Load immediately
    loadStats();

    // Then refresh every 30 seconds to show real-time quota changes
    statsRefreshInterval = setInterval(() => {
        loadStats();
    }, 30000); // 30 seconds
}

function stopStatsRefresh() {
    if (statsRefreshInterval) {
        clearInterval(statsRefreshInterval);
        statsRefreshInterval = null;
    }
}

// Display account usage visualization with donut chart
function displayAccountUsage(usage) {
    // Update account usage header
    const used = usage.accountRequestsUsed || 0;
    const limit = usage.accountRateLimit || 10000;
    const percentage = usage.usagePercentage || 0;

    // Update the real-time predictor with server data
    if (usagePredictor) {
        usagePredictor.updateServerData(used, limit, usage.remainingRequests);
    }

    document.getElementById('account-used').textContent = formatNumber(used);
    document.getElementById('account-limit').textContent = formatNumber(limit);
    document.getElementById('donut-percentage').textContent = `${percentage.toFixed(1)}%`;

    // Create donut chart
    createDonutChart(used, limit, usage.keyUsageBreakdown || []);
}

// Create donut chart with SVG
function createDonutChart(used, limit, keyBreakdown) {
    const svg = document.getElementById('donut-chart');
    const legend = document.getElementById('donut-legend');

    const centerX = 160;
    const centerY = 160;
    const outerRadius = 140;
    const innerRadius = 100;

    let svgContent = '';
    let legendContent = '';

    if (keyBreakdown.length === 0) {
        // No API keys exist - show empty state
        svg.style.display = 'none';
        const donutContainer = document.querySelector('.donut-chart-container');
        if (donutContainer) {
            donutContainer.style.display = 'none';
        }

        legendContent = `
            <div class="legend-item">
                <div class="legend-color" style="background: rgba(156, 163, 175, 0.4)"></div>
                <div class="legend-info">
                    <div class="legend-name">No Usage</div>
                    <div class="legend-requests">0 requests</div>
                </div>
            </div>
        `;
    } else {
        // API keys exist - show donut chart (even with 0 usage)
        svg.style.display = 'block';
        const donutContainer = document.querySelector('.donut-chart-container');
        if (donutContainer) {
            donutContainer.style.display = 'block';
        }

        // Calculate angles for each segment
        let currentAngle = 0;
        const totalRequests = keyBreakdown.reduce((sum, key) => sum + key.requestCount, 0);

        // If there are no requests made, show all API keys as available
        if (totalRequests === 0) {
            // Show each API key with 0 usage
            keyBreakdown.forEach((key, index) => {
                const color = getKeyColor(key.keyId || key.keyPrefix);

                // Create a small segment for each key to show they exist
                const smallAngle = 5; // Small visible segment
                const startAngle = currentAngle;
                const endAngle = currentAngle + smallAngle;

                const x1 = centerX + outerRadius * Math.cos((startAngle - 90) * Math.PI / 180);
                const y1 = centerY + outerRadius * Math.sin((startAngle - 90) * Math.PI / 180);
                const x2 = centerX + outerRadius * Math.cos((endAngle - 90) * Math.PI / 180);
                const y2 = centerY + outerRadius * Math.sin((endAngle - 90) * Math.PI / 180);

                const x3 = centerX + innerRadius * Math.cos((endAngle - 90) * Math.PI / 180);
                const y3 = centerY + innerRadius * Math.sin((endAngle - 90) * Math.PI / 180);
                const x4 = centerX + innerRadius * Math.cos((startAngle - 90) * Math.PI / 180);
                const y4 = centerY + innerRadius * Math.sin((startAngle - 90) * Math.PI / 180);

                svgContent += `
                    <path d="M ${x1} ${y1} A ${outerRadius} ${outerRadius} 0 0 1 ${x2} ${y2}
                             L ${x3} ${y3} A ${innerRadius} ${innerRadius} 0 0 0 ${x4} ${y4} Z"
                          fill="${color}"
                          stroke="var(--bg-secondary)"
                          stroke-width="3"
                          class="donut-segment"
                          style="cursor: pointer; transform-origin: ${centerX}px ${centerY}px;"
                          onmouseenter="showDonutTooltip(event, '${key.keyName || key.keyPrefix}', 0, '${color}', '${key.keyPrefix}', '${key.keyId}')"
                          onmouseleave="hideDonutTooltip()"/>
                `;

                legendContent += `
                    <div class="legend-item"
                         onmouseenter="showDonutTooltip(event, '${key.keyName || key.keyPrefix}', 0, '${color}', '${key.keyPrefix}', '${key.keyId}')"
                         onmouseleave="hideDonutTooltip()">
                        <div class="legend-color" style="background-color: ${color}"></div>
                        <div class="legend-info">
                            <div class="legend-name">${key.keyName || key.keyPrefix}</div>
                            <div class="legend-requests">0 requests</div>
                        </div>
                    </div>
                `;

                currentAngle = endAngle + 10; // Add gap between keys
            });

            // Fill the rest with available quota
            const remainingAngle = 360 - currentAngle;
            if (remainingAngle > 0) {
                const x1 = centerX + outerRadius * Math.cos((currentAngle - 90) * Math.PI / 180);
                const y1 = centerY + outerRadius * Math.sin((currentAngle - 90) * Math.PI / 180);
                const x2 = centerX + outerRadius * Math.cos((360 - 90) * Math.PI / 180);
                const y2 = centerY + outerRadius * Math.sin((360 - 90) * Math.PI / 180);

                const x3 = centerX + innerRadius * Math.cos((360 - 90) * Math.PI / 180);
                const y3 = centerY + innerRadius * Math.sin((360 - 90) * Math.PI / 180);
                const x4 = centerX + innerRadius * Math.cos((currentAngle - 90) * Math.PI / 180);
                const y4 = centerY + innerRadius * Math.sin((currentAngle - 90) * Math.PI / 180);

                svgContent += `
                    <path d="M ${x1} ${y1} A ${outerRadius} ${outerRadius} 0 1 1 ${x2} ${y2}
                             L ${x3} ${y3} A ${innerRadius} ${innerRadius} 0 1 0 ${x4} ${y4} Z"
                          fill="rgba(156, 163, 175, 0.2)"
                          stroke="var(--bg-secondary)"
                          stroke-width="2"/>
                `;

                legendContent += `
                    <div class="legend-item">
                        <div class="legend-color" style="background: rgba(156, 163, 175, 0.4)"></div>
                        <div class="legend-info">
                            <div class="legend-name">Available</div>
                            <div class="legend-requests">${limit.toLocaleString()} requests</div>
                        </div>
                    </div>
                `;
            }
        } else {
            // API keys have actual usage - simplified donut chart
            const percentage = Math.min((used / limit), 1);
            const dashArray = 2 * Math.PI * outerRadius;
            const dashOffset = dashArray * (1 - percentage);

            svgContent = `
                <circle cx="${centerX}" cy="${centerY}" r="${outerRadius}"
                        fill="none" stroke="rgba(156, 163, 175, 0.2)"
                        stroke-width="${outerRadius - innerRadius}"/>
                <circle cx="${centerX}" cy="${centerY}" r="${outerRadius}"
                        fill="none" stroke="url(#gradient1)"
                        stroke-width="${outerRadius - innerRadius}"
                        stroke-dasharray="${dashArray}"
                        stroke-dashoffset="${dashOffset}"
                        stroke-linecap="round"/>
                <defs>
                    <linearGradient id="gradient1" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" style="stop-color:#6366f1;stop-opacity:1" />
                        <stop offset="100%" style="stop-color:#8b5cf6;stop-opacity:1" />
                    </linearGradient>
                </defs>
            `;

            // Show API keys with usage
            keyBreakdown.forEach((key) => {
                const color = getKeyColor(key.keyId || key.keyPrefix);

                legendContent += `
                    <div class="legend-item">
                        <div class="legend-color" style="background-color: ${color}"></div>
                        <div class="legend-info">
                            <div class="legend-name">${key.keyName || key.keyPrefix}</div>
                            <div class="legend-requests">${key.requestCount.toLocaleString()} requests</div>
                        </div>
                    </div>
                `;
            });

    svg.innerHTML = svgContent;
    legend.innerHTML = legendContent;
}

// Format numbers for display
function formatNumber(num) {
    if (num >= 1000000) {
        return (num / 1000000).toFixed(1) + 'M';
    } else if (num >= 1000) {
        return (num / 1000).toFixed(1) + 'K';
    }
    return num.toString();
}

// Enhanced Donut chart tooltip functionality
let activeTooltip = null;

function showDonutTooltip(event, keyName, requestCount, color, keyPrefix, keyId) {
    hideDonutTooltip();

    const tooltip = document.createElement('div');
    tooltip.className = 'tooltip';

    // Create enhanced tooltip content
    const keyDisplay = keyName || keyPrefix || 'Unknown Key';
    const prefixDisplay = keyPrefix ? keyPrefix.substring(0, 12) + '...' : '';
    const idDisplay = keyId ? '#' + keyId.toString().substring(-6) : '';

    tooltip.innerHTML = `
        <div class="tooltip-header">
            <div class="tooltip-key-icon" style="background-color: ${color}"></div>
            <div class="tooltip-key-name">${keyDisplay}</div>
            ${prefixDisplay ? `<div class="tooltip-key-id">${prefixDisplay}</div>` : ''}
        </div>
        <div class="tooltip-stats">
            <div class="tooltip-stat">
                <div class="tooltip-stat-value">${requestCount.toLocaleString()}</div>
                <div class="tooltip-stat-label">Requests</div>
            </div>
            <div class="tooltip-stat">
                <div class="tooltip-stat-value">${requestCount > 0 ? ((requestCount / 10000) * 100).toFixed(1) : '0'}%</div>
                <div class="tooltip-stat-label">Usage</div>
            </div>
        </div>
    `;

    document.body.appendChild(tooltip);

    // Position tooltip with smart positioning
    const rect = event.target.getBoundingClientRect();
    const tooltipRect = tooltip.getBoundingClientRect();
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;

    let left = rect.left + (rect.width / 2) - (tooltip.offsetWidth / 2);
    let top = rect.top - tooltip.offsetHeight - 15;

    // Adjust horizontal position if tooltip goes off screen
    if (left < 10) {
        left = 10;
    } else if (left + tooltip.offsetWidth > viewportWidth - 10) {
        left = viewportWidth - tooltip.offsetWidth - 10;
    }

    // Adjust vertical position if tooltip goes off screen (show below instead)
    if (top < 10) {
        top = rect.bottom + 15;
        tooltip.style.setProperty('--tooltip-arrow-direction', 'up');
    }

    tooltip.style.left = left + 'px';
    tooltip.style.top = top + 'px';

    // Show tooltip with smooth animation
    requestAnimationFrame(() => {
        tooltip.classList.add('show');
    });

    activeTooltip = tooltip;
}

function hideDonutTooltip() {
    if (activeTooltip) {
        activeTooltip.classList.remove('show');
        setTimeout(() => {
            if (activeTooltip && activeTooltip.parentNode) {
                activeTooltip.parentNode.removeChild(activeTooltip);
            }
            activeTooltip = null;
        }, 300);
    }
}

// Create API key
async function createApiKey() {
    const name = document.getElementById('key-name').value.trim();
    const btn = event.target;
    const originalText = btn.innerHTML;

    btn.disabled = true;
    btn.innerHTML = '<div class="loading-spinner" style="width: 16px; height: 16px;"></div>';

    try {
        const response = await authenticatedFetch(`${API_BASE}/v1/auth/api-key/create`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ name: name || null })
        });

        const result = await response.json();

        if (!response.ok) {
            // Check if it's the maximum key limit error
            if (response.status === 400 && result.error) {
                showToast('error', 'Maximum Keys Reached', result.error);
            } else {
                throw new Error('Failed to create API key');
            }
            btn.disabled = false;
            btn.innerHTML = originalText;
            return;
        }

        closeModal('create-key-modal');
        document.getElementById('key-name').value = '';

        // Show the new key
        document.getElementById('new-key-value').value = result.key;
        openModal('show-key-modal');

        showToast('success', 'Success!', 'API key created successfully');

        // Reload keys to update the list
        await loadApiKeys();
    } catch (error) {
        console.error('Error creating API key:', error);
        showToast('error', 'Error', 'Failed to create API key. Please try again.');
    } finally {
        btn.disabled = false;
        btn.innerHTML = originalText;
    }
}

// Delete API key
function openDeleteKeyModal(keyId) {
    keyToDelete = keyId;
    openModal('delete-key-modal');
}

async function confirmDeleteKey() {
    if (!keyToDelete) return;

    const btn = event.target;
    const originalText = btn.innerHTML;

    btn.disabled = true;
    btn.innerHTML = '<div class="loading-spinner" style="width: 16px; height: 16px;"></div>';

    try {
        const response = await authenticatedFetch(`${API_BASE}/v1/auth/api-key/${keyToDelete}`, {
            method: 'DELETE'
        });

        if (!response.ok) throw new Error('Failed to delete API key');

        closeModal('delete-key-modal');
        showToast('success', 'Deleted', 'API key deleted successfully');
        await loadApiKeys();
        await loadStats();
    } catch (error) {
        console.error('Error deleting API key:', error);
        showToast('error', 'Error', 'Failed to delete API key. Please try again.');
    } finally {
        keyToDelete = null;
        btn.disabled = false;
        btn.innerHTML = originalText;
    }
}

// Request data export
async function requestDataExport() {
    const btn = event.target;
    const originalText = btn.innerHTML;

    btn.disabled = true;
    btn.innerHTML = '<div class="loading-spinner" style="width: 16px; height: 16px;"></div> Exporting...';

    try {
        const response = await authenticatedFetch(`${API_BASE}/v1/auth/export-data`);

        if (!response.ok) throw new Error('Failed to export data');

        const data = await response.json();
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `synaxic-data-export-${new Date().toISOString().split('T')[0]}.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);

        showToast('success', 'Export Complete', 'Your data has been downloaded');
    } catch (error) {
        console.error('Error exporting data:', error);
        showToast('error', 'Export Failed', 'Failed to export data. Please try again.');
    } finally {
        btn.disabled = false;
        btn.innerHTML = originalText;
    }
}

// Delete account
function openDeleteAccountModal() {
    document.getElementById('delete-confirm').value = '';
    openModal('delete-account-modal');
}

async function confirmDeleteAccount() {
    const confirmation = document.getElementById('delete-confirm').value;

    if (confirmation !== 'DELETE') {
        showToast('warning', 'Invalid Confirmation', 'Please type "DELETE" to confirm account deletion.');
        return;
    }

    const btn = event.target;
    const originalText = btn.innerHTML;

    btn.disabled = true;
    btn.innerHTML = '<div class="loading-spinner" style="width: 16px; height: 16px;"></div> Deleting...';

    try {
        const response = await authenticatedFetch(`${API_BASE}/v1/auth/delete-account`, {
            method: 'DELETE'
        });

        if (!response.ok) throw new Error('Failed to delete account');

        showToast('success', 'Account Deleted', 'Your account has been deleted successfully.');
        setTimeout(() => {
            window.location.href = '/';
        }, 2000);
    } catch (error) {
        console.error('Error deleting account:', error);
        showToast('error', 'Error', 'Failed to delete account. Please try again or contact support.');
        btn.disabled = false;
        btn.innerHTML = originalText;
    }
}

// Logout function
async function logout() {
    try {
        // Call logout endpoint to invalidate session
        const response = await authenticatedFetch(`${API_BASE}/v1/auth/logout`, {
            method: 'POST'
        });

        // Even if logout endpoint fails, redirect to home
        showToast('success', 'Logged Out', 'You have been successfully logged out.');
        setTimeout(() => {
            window.location.href = '/';
        }, 1000);
    } catch (error) {
        console.error('Logout error:', error);
        // Still redirect even if API call fails
        showToast('success', 'Logged Out', 'You have been successfully logged out.');
        setTimeout(() => {
            window.location.href = '/';
        }, 1000);
    }
}

// Reset cookie consent
function resetCookieConsent() {
    const consent = localStorage.getItem('synaxic_cookie_consent');
    if (consent) {
        localStorage.removeItem('synaxic_cookie_consent');
        showToast('success', 'Consent Reset', 'Cookie preferences have been cleared. Reloading page to show consent banner...');
        setTimeout(() => {
            window.location.reload();
        }, 1500);
    } else {
        showToast('info', 'No Preferences Found', 'No cookie consent preferences to reset.');
    }
}

// Modal utilities
function openModal(modalId) {
    document.getElementById(modalId).classList.add('active');
}

function closeModal(modalId) {
    document.getElementById(modalId).classList.remove('active');
}

function openCreateKeyModal() {
    openModal('create-key-modal');
}

function copyNewKey() {
    const input = document.getElementById('new-key-value');
    input.select();
    navigator.clipboard.writeText(input.value).then(() => {
        showToast('success', 'Copied!', 'API key copied to clipboard');
    }).catch(() => {
        document.execCommand('copy');
        showToast('success', 'Copied!', 'API key copied to clipboard');
    });
}

function copyToClipboard(text, button) {
    navigator.clipboard.writeText(text).then(() => {
        const originalText = button.textContent;
        button.textContent = '✓ Copied!';
        showToast('success', 'Copied!', 'API key copied to clipboard');
        setTimeout(() => {
            button.textContent = originalText;
        }, 2000);
    }).catch(err => {
        console.error('Failed to copy:', err);
        showToast('error', 'Copy Failed', 'Failed to copy to clipboard');
    });
}

// Close modals when clicking outside
document.querySelectorAll('.modal').forEach(modal => {
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            modal.classList.remove('active');
        }
    });
});

// Initialize
window.addEventListener('DOMContentLoaded', () => {
    console.log('Dashboard page loaded - starting initialization...');

    // Add a safety timeout to prevent infinite loading
    setTimeout(() => {
        const profileLoading = document.querySelector('#user-profile .loading-container');
        const keysLoading = document.querySelector('#api-keys-list .loading-container');

        if (profileLoading || keysLoading) {
            console.log('Safety timeout triggered - showing error state');
            document.getElementById('user-profile').innerHTML = `
                <div class="alert alert-error">
                    Loading timeout. Please <a href="/" style="color: var(--primary);">login again</a>.
                </div>
            `;
            document.getElementById('api-keys-list').innerHTML = `
                <div class="alert alert-error">
                    Loading timeout. Please refresh the page or <a href="/" style="color: var(--primary);">login again</a>.
                </div>
            `;
        }
    }, 15000); // 15 second safety timeout

    checkAuth();

    // Initialize the real-time usage predictor
    usagePredictor = new RealTimeUsagePredictor();
    usagePredictor.initialize();

    // Initialize mobile menu and other common features
    if (typeof MobileMenu !== 'undefined') {
        MobileMenu.init();
    }

    // Add keyboard event support for modals and inputs
    initializeKeyboardSupport();
});

// Cleanup when page is unloaded
window.addEventListener('beforeunload', () => {
    stopStatsRefresh();
    if (usagePredictor) {
        usagePredictor.destroy();
        usagePredictor = null;
    }
});

// Also cleanup when page becomes hidden (tab switch)
document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
        stopStatsRefresh();
    } else {
        // Page became visible again, restart refresh if user is logged in
        if (currentUser) {
            startStatsRefresh();
        }
    }
});

// Keyboard event support for modals and forms
function initializeKeyboardSupport() {
    // Escape key to close modals
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            const activeModal = document.querySelector('.modal.active');
            if (activeModal) {
                closeModal(activeModal.id);
            }
        }
    });

    // Enter key support for input fields and modals
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            const activeElement = document.activeElement;
            const activeModal = document.querySelector('.modal.active');

            // Handle Enter key in modals
            if (activeModal) {
                // For delete confirmation modal
                if (activeModal.id === 'delete-account-modal') {
                    const confirmInput = document.getElementById('delete-confirm');
                    if (activeElement === confirmInput) {
                        const confirmation = confirmInput.value.trim();
                        if (confirmation === 'DELETE') {
                            confirmDeleteAccount();
                        }
                        return;
                    }
                }

                // For create API key modal - submit on Enter when in the name input
                if (activeModal.id === 'create-key-modal') {
                    const nameInput = document.getElementById('key-name');
                    if (activeElement === nameInput) {
                        createApiKey();
                        return;
                    }
                }

                // For delete key modal - confirm deletion on Enter
                if (activeModal.id === 'delete-key-modal') {
                    confirmDeleteKey();
                    return;
                }
            }

            // Handle Enter key in regular inputs outside modals
            if (activeElement && activeElement.classList.contains('form-input')) {
                const form = activeElement.closest('form');
                if (form) {
                    const submitBtn = form.querySelector('button[type="submit"], .btn-primary');
                    if (submitBtn && !submitBtn.disabled) {
                        submitBtn.click();
                        return;
                    }
                }
            }
        }
    });

    // Focus management for modals
    document.querySelectorAll('.modal').forEach(modal => {
        modal.addEventListener('shown', () => {
            // Focus the first input or button in the modal
            const firstInput = modal.querySelector('input:not([readonly]), button:not([disabled])');
            if (firstInput) {
                firstInput.focus();
            }
        });
    });
}}}