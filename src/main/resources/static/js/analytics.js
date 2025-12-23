// Tab switching
document.querySelectorAll('.dashboard-tab').forEach(tab => {
    tab.addEventListener('click', (e) => {
        const tabName = e.target.dataset.tab;

        // Update active tab
        document.querySelectorAll('.dashboard-tab').forEach(t => t.classList.remove('active'));
        e.target.classList.add('active');

        // Update active content
        document.querySelectorAll('.dashboard-content').forEach(c => c.classList.remove('active'));
        document.getElementById(tabName).classList.add('active');
    });
});

// Fetch public stats
async function fetchPublicStats() {
    try {
        const response = await fetch('/api/stats');
        const data = await response.json();

        document.getElementById('total-requests-stat').textContent = data.totalRequests.toLocaleString();
        document.getElementById('total-users-stat').textContent = data.totalUsers.toLocaleString();
        document.getElementById('total-requests-metric').textContent = data.totalRequests.toLocaleString();
        document.getElementById('users-metric').textContent = data.totalUsers.toLocaleString();
    } catch (error) {
        console.error('Error fetching public stats:', error);
        // Don't change values, leave them as they are
    }
}

// Fetch detailed analytics
async function fetchDetailedAnalytics() {
    try {
        const response = await fetch('/v1/admin/stats', {
            credentials: 'include',
            redirect: 'manual'
        });

        // Handle redirect (not authenticated)
        if (response.type === 'opaqueredirect' || response.status === 0) {
            console.log('Admin stats requires authentication');
            // Don't change values, just leave them as they are
            return;
        }

        if (!response.ok) {
            console.warn('Failed to fetch admin stats:', response.status);
            // Don't change values, just leave them as they are
            return;
        }

        const data = await response.json();

        // Update key metrics (top cards)
        if (data.responseTime) {
            document.getElementById('avg-response-time').textContent =
                data.responseTime.avgMs ? `${data.responseTime.avgMs.toFixed(1)} ms` : 'N/A';
        }

        if (data.rates) {
            document.getElementById('success-rate').textContent =
                data.rates.successRatePercent ? `${data.rates.successRatePercent.toFixed(1)}%` : 'N/A';
            document.getElementById('requests-per-min').textContent =
                data.rates.requestsPerMinute ? data.rates.requestsPerMinute.toFixed(1) : 'N/A';
            document.getElementById('error-rate').textContent =
                data.rates.errorRatePercent ? `${data.rates.errorRatePercent.toFixed(2)}%` : '0%';
        }

        // API Key stats
        if (data.apiKeys) {
            document.getElementById('active-api-keys').textContent = data.apiKeys.activeKeysLast24h || '0';
            document.getElementById('total-api-keys').textContent = data.apiKeys.totalKeys || '0';
        }

        // Cache stats
        if (data.cache) {
            document.getElementById('cache-hit-rate').textContent =
                data.cache.hitRatePercent ? `${data.cache.hitRatePercent.toFixed(1)}%` : 'N/A';
        }

        // Service breakdown
        if (data.serviceBreakdown) {
            document.getElementById('ip-service-requests').textContent =
                data.serviceBreakdown.ipInspectorRequests?.toLocaleString() || '0';
            document.getElementById('email-service-requests').textContent =
                data.serviceBreakdown.emailValidatorRequests?.toLocaleString() || '0';
            document.getElementById('converter-service-requests').textContent =
                data.serviceBreakdown.unitConverterRequests?.toLocaleString() || '0';
            document.getElementById('other-service-requests').textContent =
                data.serviceBreakdown.otherRequests?.toLocaleString() || '0';
        }

        // Response time statistics
        if (data.responseTime) {
            document.getElementById('response-min').textContent =
                data.responseTime.minMs ? `${data.responseTime.minMs.toFixed(1)} ms` : 'N/A';
            document.getElementById('response-avg').textContent =
                data.responseTime.avgMs ? `${data.responseTime.avgMs.toFixed(1)} ms` : 'N/A';
            document.getElementById('response-max').textContent =
                data.responseTime.maxMs ? `${data.responseTime.maxMs.toFixed(1)} ms` : 'N/A';
        }

        // Latency percentiles
        if (data.latency) {
            document.getElementById('latency-p50').textContent =
                data.latency.p50_ms ? `${data.latency.p50_ms.toFixed(0)} ms` : 'N/A';
            document.getElementById('latency-p95').textContent =
                data.latency.p95_ms ? `${data.latency.p95_ms.toFixed(0)} ms` : 'N/A';
            document.getElementById('latency-p99').textContent =
                data.latency.p99_ms ? `${data.latency.p99_ms.toFixed(0)} ms` : 'N/A';

            // For health tab
            if (document.getElementById('latency-p50-metric')) {
                document.getElementById('latency-p50-metric').textContent =
                    data.latency.p50_ms ? data.latency.p50_ms.toFixed(0) : '--';
            }
            if (document.getElementById('latency-p95-metric')) {
                document.getElementById('latency-p95-metric').textContent =
                    data.latency.p95_ms ? data.latency.p95_ms.toFixed(0) : '--';
            }
            if (document.getElementById('latency-p99-metric')) {
                document.getElementById('latency-p99-metric').textContent =
                    data.latency.p99_ms ? data.latency.p99_ms.toFixed(0) : '--';
            }
        }

        // System health
        document.getElementById('uptime').textContent = data.uptime || 'N/A';
        if (document.getElementById('uptime-metric')) {
            document.getElementById('uptime-metric').textContent = data.uptime || 'N/A';
        }

        // For health tab metrics
        if (data.rates && document.getElementById('avg-response-metric')) {
            document.getElementById('avg-response-metric').textContent =
                data.responseTime?.avgMs ? `${data.responseTime.avgMs.toFixed(1)} ms` : 'N/A';
            document.getElementById('success-rate-metric').textContent =
                data.rates.successRatePercent ? `${data.rates.successRatePercent.toFixed(1)}%` : 'N/A';
        }

        // Endpoint breakdown
        if (data.breakdowns?.topEndpoints) {
            renderBreakdown('endpoint-breakdown', data.breakdowns.topEndpoints);
        } else {
            document.getElementById('endpoint-breakdown').innerHTML =
                '<p style="color: var(--text-muted); text-align: center; padding: 20px;">No endpoint data yet</p>';
        }

        // Error breakdown
        const errorBreakdown = [];
        if (data.requests?.clientErrorCount > 0) {
            errorBreakdown.push({ item: '4xx Client Errors', count: data.requests.clientErrorCount });
        }
        if (data.requests?.serverErrorCount > 0) {
            errorBreakdown.push({ item: '5xx Server Errors', count: data.requests.serverErrorCount });
        }
        if (errorBreakdown.length > 0) {
            renderBreakdown('error-breakdown', errorBreakdown);
        } else {
            document.getElementById('error-breakdown').innerHTML =
                '<p style="color: var(--success); text-align: center; padding: 20px;">✅ No errors detected</p>';
        }

        // Top API Keys
        if (data.breakdowns?.topApiKeys) {
            const keysData = data.breakdowns.topApiKeys.map(k => ({
                item: k.item.substring(0, 8) + '***' + k.item.substring(k.item.length - 4),
                count: k.count
            }));
            renderBreakdown('top-api-keys', keysData);
        } else {
            document.getElementById('top-api-keys').innerHTML =
                '<p style="color: var(--text-muted); text-align: center; padding: 20px;">No API key data yet</p>';
        }

        // Top Countries
        if (data.breakdowns?.topCountries) {
            renderBreakdown('top-countries', data.breakdowns.topCountries);
        } else {
            document.getElementById('top-countries').innerHTML =
                '<p style="color: var(--text-muted); text-align: center; padding: 20px;">No country data yet</p>';
        }

    } catch (error) {
        console.error('Error fetching detailed analytics:', error);
        // Don't change values, just leave them as they are
    }
}

// Fetch system health
async function fetchSystemHealth() {
    try {
        const response = await fetch('/actuator/health');

        if (!response.ok) {
            console.warn('Failed to fetch system health:', response.status);
            // Don't change status, leave it as it is
            return;
        }

        const data = await response.json();

        const isUp = data.status === 'UP';
        const overallStatus = document.getElementById('overall-status');
        overallStatus.className = `status-badge ${isUp ? 'up' : 'down'}`;
        overallStatus.innerHTML = `
            <span class="status-indicator"></span>
            <span>System Status: ${data.status}</span>
        `;

        // Build components for health tab
        if (data.components) {
            const componentsHtml = Object.entries(data.components)
                .map(([name, component]) => renderComponent(name, component))
                .join('');
            document.getElementById('health-components').innerHTML = `<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 24px; margin-bottom: 48px;">${componentsHtml}</div>`;
        }

        // Additional health data processing can be added here if needed
    } catch (error) {
        console.error('Error fetching system health:', error);
        // Don't change status, leave it as it is
    }
}

function renderComponent(name, component) {
    const isUp = component.status === 'UP';
    const displayName = formatComponentName(name);

    let detailsHtml = '';
    if (component.details) {
        detailsHtml = Object.entries(component.details)
            .filter(([key]) => key !== 'path')
            .map(([key, value]) => {
                const displayValue = formatValue(value);
                return `
                    <div class="detail-item">
                        <span class="detail-label">${formatKey(key)}</span>
                        <span class="detail-value">${displayValue}</span>
                    </div>
                `;
            })
            .join('');
    }

    return `
        <div class="component-card">
            <div class="component-header">
                <span class="component-name">${displayName}</span>
                <span class="component-status ${isUp ? 'up' : 'down'}">${component.status}</span>
            </div>
            ${detailsHtml ? `<div class="component-details">${detailsHtml}</div>` : ''}
        </div>
    `;
}

function formatComponentName(name) {
    const names = {
        'db': 'Database',
        'diskSpace': 'Disk Space',
        'ping': 'Ping',
        'redis': 'Redis Cache',
        'ssl': 'SSL Certificates'
    };
    return names[name] || name.charAt(0).toUpperCase() + name.slice(1);
}

function formatKey(key) {
    return key.replace(/([A-Z])/g, ' $1').trim().split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
}

function formatValue(value) {
    if (typeof value === 'number') {
        if (value > 1000000000) {
            return `${(value / 1000000000).toFixed(2)} GB`;
        } else if (value > 1000000) {
            return `${(value / 1000000).toFixed(2)} MB`;
        } else if (value > 1000) {
            return `${(value / 1000).toFixed(2)} KB`;
        }
        return value.toString();
    }
    if (Array.isArray(value)) {
        return value.length === 0 ? 'None' : value.join(', ');
    }
    return value.toString();
}

function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

function renderBreakdown(containerId, data) {
    const container = document.getElementById(containerId);
    if (!data || data.length === 0) {
        container.innerHTML = '<p style="color: var(--text-muted); text-align: center;">No data available</p>';
        return;
    }

    const maxCount = Math.max(...data.map(item => item.count));

    container.innerHTML = data.map(item => `
        <div class="breakdown-item">
            <div style="flex: 1;">
                <div class="breakdown-label">${item.item}</div>
                <div class="breakdown-bar">
                    <div class="breakdown-bar-fill" style="width: ${(item.count / maxCount * 100).toFixed(1)}%"></div>
                </div>
            </div>
            <div class="breakdown-value">${item.count.toLocaleString()}</div>
        </div>
    `).join('');
}

// Fetch memory metrics
async function fetchMemoryMetrics() {
    try {
        const response = await fetch('/actuator/metrics/jvm.memory.used');

        if (response.ok) {
            const data = await response.json();
            const memoryUsed = data.measurements?.[0]?.value || 0;
            document.getElementById('memory-usage').textContent = formatBytes(memoryUsed);
        } else {
            document.getElementById('memory-usage').textContent = 'N/A';
        }
    } catch (error) {
        console.error('Error fetching memory metrics:', error);
        // Don't change memory usage value, leave it as it is
    }
}

// Fetch cache metrics
async function fetchCacheMetrics() {
    try {
        const response = await fetch('/actuator/metrics/cache.gets');

        if (response.ok) {
            const data = await response.json();
            const hits = data.measurements?.find(m => m.statistic === 'COUNT')?.value || 0;
            document.getElementById('cache-hit-rate').textContent = hits > 0 ? hits.toFixed(0) + ' hits' : 'No data';
        } else {
            document.getElementById('cache-hit-rate').textContent = 'N/A';
        }
    } catch (error) {
        console.error('Error fetching cache metrics:', error);
        // Don't change cache hit rate value, leave it as it is
    }
}

// Helper function to set placeholders for all loading elements
function setLoadingPlaceholders(value) {
    const ids = [
        'avg-response-time', 'success-rate', 'requests-per-min', 'error-rate',
        'active-api-keys', 'cache-hit-rate', 'total-api-keys',
        'ip-service-requests', 'email-service-requests',
        'converter-service-requests', 'other-service-requests',
        'response-min', 'response-avg', 'response-max',
        'latency-p50', 'latency-p95', 'latency-p99',
        'uptime', 'memory-usage',
        'latency-p50-metric', 'latency-p95-metric', 'latency-p99-metric',
        'avg-response-metric', 'success-rate-metric', 'uptime-metric', 'sessions-metric'
    ];

    ids.forEach(id => {
        const el = document.getElementById(id);
        if (el && el.textContent === 'Loading...') {
            el.textContent = value;
        }
    });
}

// Load data on page load
window.addEventListener('DOMContentLoaded', () => {
    fetchPublicStats();
    fetchDetailedAnalytics();
    fetchSystemHealth();
    fetchMemoryMetrics();
    fetchCacheMetrics();

    // Refresh every 30 seconds
    setInterval(fetchPublicStats, 30000);
    setInterval(fetchDetailedAnalytics, 30000);
    setInterval(fetchSystemHealth, 30000);
    setInterval(fetchMemoryMetrics, 30000);
    setInterval(fetchCacheMetrics, 30000);
});