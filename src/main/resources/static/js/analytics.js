// Helper to safely update text content
function safeSetText(id, text) {
    const el = document.getElementById(id);
    if (el) {
        el.textContent = text;
    }
}

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
        if (!response.ok) return;
        
        const data = await response.json();

        safeSetText('total-requests-stat', data.totalRequests.toLocaleString());
        safeSetText('total-users-stat', data.totalUsers.toLocaleString());
        
        // Health tab metrics
        safeSetText('total-requests-metric', data.totalRequests.toLocaleString());
        safeSetText('users-metric', data.totalUsers.toLocaleString());
    } catch (error) {
        console.warn('Error fetching public stats:', error);
    }
}

// Fetch detailed analytics
async function fetchDetailedAnalytics() {
    try {
        const response = await fetch('/v1/admin/stats', {
            credentials: 'include',
            redirect: 'manual'
        });

        if (!response.ok) return;

        const data = await response.json();

        // Update key metrics (top cards)
        if (data.responseTime) {
            safeSetText('avg-response-time', data.responseTime.avgMs ? `${data.responseTime.avgMs.toFixed(1)} ms` : 'N/A');
        }

        if (data.rates) {
            safeSetText('success-rate', data.rates.successRatePercent ? `${data.rates.successRatePercent.toFixed(1)}%` : 'N/A');
            safeSetText('requests-per-min', data.rates.requestsPerMinute ? data.rates.requestsPerMinute.toFixed(1) : 'N/A');
            safeSetText('error-rate', data.rates.errorRatePercent ? `${data.rates.errorRatePercent.toFixed(2)}%` : '0%');
        }

        // API Key stats
        if (data.apiKeys) {
            safeSetText('active-api-keys', data.apiKeys.activeKeysLast24h || '0');
            safeSetText('total-api-keys', data.apiKeys.totalKeys || '0');
        }

        // Cache stats
        if (data.cache) {
            safeSetText('cache-hit-rate', data.cache.hitRatePercent ? `${data.cache.hitRatePercent.toFixed(1)}%` : 'N/A');
        }

        // Service breakdown
        if (data.serviceBreakdown) {
            safeSetText('ip-service-requests', data.serviceBreakdown.ipInspectorRequests?.toLocaleString() || '0');
            safeSetText('email-service-requests', data.serviceBreakdown.emailValidatorRequests?.toLocaleString() || '0');
            safeSetText('converter-service-requests', data.serviceBreakdown.unitConverterRequests?.toLocaleString() || '0');
            safeSetText('other-service-requests', data.serviceBreakdown.otherRequests?.toLocaleString() || '0');
        }

        // Response time statistics
        if (data.responseTime) {
            safeSetText('response-min', data.responseTime.minMs ? `${data.responseTime.minMs.toFixed(1)} ms` : 'N/A');
            safeSetText('response-avg', data.responseTime.avgMs ? `${data.responseTime.avgMs.toFixed(1)} ms` : 'N/A');
            safeSetText('response-max', data.responseTime.maxMs ? `${data.responseTime.maxMs.toFixed(1)} ms` : 'N/A');
        }

        // Latency percentiles
        if (data.latency) {
            safeSetText('latency-p50', data.latency.p50_ms ? `${data.latency.p50_ms.toFixed(0)} ms` : 'N/A');
            safeSetText('latency-p95', data.latency.p95_ms ? `${data.latency.p95_ms.toFixed(0)} ms` : 'N/A');
            safeSetText('latency-p99', data.latency.p99_ms ? `${data.latency.p99_ms.toFixed(0)} ms` : 'N/A');

            // For health tab
            safeSetText('latency-p50-metric', data.latency.p50_ms ? data.latency.p50_ms.toFixed(0) : '--');
            safeSetText('latency-p95-metric', data.latency.p95_ms ? data.latency.p95_ms.toFixed(0) : '--');
            safeSetText('latency-p99-metric', data.latency.p99_ms ? data.latency.p99_ms.toFixed(0) : '--');
        }

        // System health
        safeSetText('uptime', data.uptime || 'N/A');
        safeSetText('uptime-metric', data.uptime || 'N/A');

        // For health tab metrics
        if (data.rates) {
            safeSetText('avg-response-metric', data.responseTime?.avgMs ? `${data.responseTime.avgMs.toFixed(1)} ms` : 'N/A');
            safeSetText('success-rate-metric', data.rates.successRatePercent ? `${data.rates.successRatePercent.toFixed(1)}%` : 'N/A');
        }

        // Endpoint breakdown
        if (data.breakdowns?.topEndpoints && document.getElementById('endpoint-breakdown')) {
            renderBreakdown('endpoint-breakdown', data.breakdowns.topEndpoints);
        }

        // Error breakdown
        const errorBreakdown = [];
        if (data.requests?.clientErrorCount > 0) errorBreakdown.push({ item: '4xx Client Errors', count: data.requests.clientErrorCount });
        if (data.requests?.serverErrorCount > 0) errorBreakdown.push({ item: '5xx Server Errors', count: data.requests.serverErrorCount });
        
        if (document.getElementById('error-breakdown')) {
            if (errorBreakdown.length > 0) {
                renderBreakdown('error-breakdown', errorBreakdown);
            } else {
                document.getElementById('error-breakdown').innerHTML = '<p style="color: var(--success); text-align: center; padding: 20px;">✅ No errors detected</p>';
            }
        }

        // Top API Keys
        if (data.breakdowns?.topApiKeys && document.getElementById('top-api-keys')) {
            const keysData = data.breakdowns.topApiKeys.map(k => ({
                item: k.item.substring(0, 8) + '***' + k.item.substring(k.item.length - 4),
                count: k.count
            }));
            renderBreakdown('top-api-keys', keysData);
        }

        // Top Countries
        if (data.breakdowns?.topCountries && document.getElementById('top-countries')) {
            renderBreakdown('top-countries', data.breakdowns.topCountries);
        }

    } catch (error) {
        console.warn('Error fetching detailed analytics:', error);
    }
}

// Fetch system health
async function fetchSystemHealth() {
    try {
        const response = await fetch('/actuator/health');
        if (!response.ok) return;

        const data = await response.json();
        const isUp = data.status === 'UP';
        
        const overallStatus = document.getElementById('overall-status');
        if (overallStatus) {
            overallStatus.className = `status-badge ${isUp ? 'up' : 'down'}`;
            overallStatus.innerHTML = `
                <span class="status-indicator"></span>
                <span>System Status: ${data.status}</span>
            `;
        }

        // Build components for health tab
        if (data.components && document.getElementById('health-components')) {
            const componentsHtml = Object.entries(data.components)
                .map(([name, component]) => renderComponent(name, component))
                .join('');
            document.getElementById('health-components').innerHTML = componentsHtml;
        }
    } catch (error) {
        console.warn('Error fetching system health:', error);
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
    const names = { 'db': 'Database', 'diskSpace': 'Disk Space', 'ping': 'Ping', 'redis': 'Redis Cache', 'ssl': 'SSL Certificates' };
    return names[name] || name.charAt(0).toUpperCase() + name.slice(1);
}

function formatKey(key) {
    return key.replace(/([A-Z])/g, ' $1').trim().split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
}

function formatValue(value) {
    if (typeof value === 'number') {
        if (value > 1000000000) return `${(value / 1000000000).toFixed(2)} GB`;
        if (value > 1000000) return `${(value / 1000000).toFixed(2)} MB`;
        if (value > 1000) return `${(value / 1000).toFixed(2)} KB`;
        return value.toString();
    }
    if (Array.isArray(value)) return value.length === 0 ? 'None' : value.join(', ');
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
    if (!container) return;
    
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
            safeSetText('memory-usage', formatBytes(memoryUsed));
        } else {
            safeSetText('memory-usage', 'N/A');
        }
    } catch (error) {
        // Silent fail for metrics
    }
}

// Fetch cache metrics
async function fetchCacheMetrics() {
    try {
        const response = await fetch('/actuator/metrics/cache.gets');
        if (response.ok) {
            const data = await response.json();
            const hits = data.measurements?.find(m => m.statistic === 'COUNT')?.value || 0;
            safeSetText('cache-hit-rate', hits > 0 ? hits.toFixed(0) + ' hits' : 'No data');
        } else {
            // 404 is expected if no cache hits have happened yet
            safeSetText('cache-hit-rate', 'N/A');
        }
    } catch (error) {
        // Silent fail
    }
}

// Load data on page load
document.addEventListener('DOMContentLoaded', () => {
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

