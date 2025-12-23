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
            const hasData = data.responseTime.count > 0;

            document.getElementById('response-min').textContent =
                hasData ? `${data.responseTime.minMs.toFixed(1)} ms` : 'N/A';
            document.getElementById('response-avg').textContent =
                hasData ? `${data.responseTime.avgMs.toFixed(1)} ms` : 'N/A';
            document.getElementById('response-max').textContent =
                hasData ? `${data.responseTime.maxMs.toFixed(1)} ms` : 'N/A';
        }

        // Latency percentiles
        if (data.latency) {
            const p50 = data.latency.p50_ms;
            const p95 = data.latency.p95_ms;
            const p99 = data.latency.p99_ms;

            document.getElementById('latency-p50').textContent =
                (p50 !== null && p50 !== undefined && p50 > 0) ? `${p50.toFixed(0)} ms` : 'N/A';
            document.getElementById('latency-p95').textContent =
                (p95 !== null && p95 !== undefined && p95 > 0) ? `${p95.toFixed(0)} ms` : 'N/A';
            document.getElementById('latency-p99').textContent =
                (p99 !== null && p99 !== undefined && p99 > 0) ? `${p99.toFixed(0)} ms` : 'N/A';

            // For health tab
            if (document.getElementById('latency-p50-metric')) {
                document.getElementById('latency-p50-metric').textContent =
                    (p50 !== null && p50 !== undefined && p50 > 0) ? p50.toFixed(0) : '--';
            }
            if (document.getElementById('latency-p95-metric')) {
                document.getElementById('latency-p95-metric').textContent =
                    (p95 !== null && p95 !== undefined && p95 > 0) ? p95.toFixed(0) : '--';
            }
            if (document.getElementById('latency-p99-metric')) {
                document.getElementById('latency-p99-metric').textContent =
                    (p99 !== null && p99 !== undefined && p99 > 0) ? p99.toFixed(0) : '--';
            }
        }

        // System health
        document.getElementById('uptime').textContent = data.uptime || 'N/A';
        if (document.getElementById('uptime-metric')) {
            document.getElementById('uptime-metric').textContent = data.uptime || 'N/A';
        }

        // For health tab metrics
        if (data.rates && document.getElementById('avg-response-metric')) {
            const hasResponseData = data.responseTime?.count > 0;
            document.getElementById('avg-response-metric').textContent =
                hasResponseData ? `${data.responseTime.avgMs.toFixed(1)} ms` : 'N/A';
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
        overallStatus.innerHTML = '';
        const indicator = document.createElement('span');
        indicator.className = 'status-indicator';
        const statusText = document.createElement('span');
        statusText.textContent = 'System Status: ' + data.status;
        overallStatus.appendChild(indicator);
        overallStatus.appendChild(statusText);

        // Build components for health tab
        if (data.components) {
            const healthComponents = document.getElementById('health-components');
            healthComponents.innerHTML = '';

            const gridDiv = document.createElement('div');
            gridDiv.style.display = 'grid';
            gridDiv.style.gridTemplateColumns = 'repeat(auto-fit, minmax(320px, 1fr))';
            gridDiv.style.gap = '24px';
            gridDiv.style.marginBottom = '48px';

            Object.entries(data.components).forEach(([name, component]) => {
                const componentElement = renderComponent(name, component);
                gridDiv.appendChild(componentElement);
            });

            healthComponents.appendChild(gridDiv);
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

    // Create card element
    const cardDiv = document.createElement('div');
    cardDiv.className = 'component-card';

    // Create header
    const headerDiv = document.createElement('div');
    headerDiv.className = 'component-header';

    const nameSpan = document.createElement('span');
    nameSpan.className = 'component-name';
    nameSpan.textContent = displayName;

    const statusSpan = document.createElement('span');
    statusSpan.className = 'component-status ' + (isUp ? 'up' : 'down');
    statusSpan.textContent = component.status;

    headerDiv.appendChild(nameSpan);
    headerDiv.appendChild(statusSpan);
    cardDiv.appendChild(headerDiv);

    // Create details if present
    if (component.details) {
        const detailsDiv = document.createElement('div');
        detailsDiv.className = 'component-details';

        Object.entries(component.details)
            .filter(([key]) => key !== 'path')
            .forEach(([key, value]) => {
                const displayValue = formatValue(value);

                const detailItem = document.createElement('div');
                detailItem.className = 'detail-item';

                const labelSpan = document.createElement('span');
                labelSpan.className = 'detail-label';
                labelSpan.textContent = formatKey(key);

                const valueSpan = document.createElement('span');
                valueSpan.className = 'detail-value';
                valueSpan.textContent = displayValue;

                detailItem.appendChild(labelSpan);
                detailItem.appendChild(valueSpan);
                detailsDiv.appendChild(detailItem);
            });

        if (detailsDiv.children.length > 0) {
            cardDiv.appendChild(detailsDiv);
        }
    }

    return cardDiv;
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
        container.innerHTML = '';
        const emptyP = document.createElement('p');
        emptyP.style.color = 'var(--text-muted)';
        emptyP.style.textAlign = 'center';
        emptyP.textContent = 'No data available';
        container.appendChild(emptyP);
        return;
    }

    const maxCount = Math.max(...data.map(item => item.count));
    container.innerHTML = '';

    data.forEach(item => {
        const breakdownItem = document.createElement('div');
        breakdownItem.className = 'breakdown-item';

        const flexDiv = document.createElement('div');
        flexDiv.style.flex = '1';

        const labelDiv = document.createElement('div');
        labelDiv.className = 'breakdown-label';
        labelDiv.textContent = item.item; // Sanitized

        const barDiv = document.createElement('div');
        barDiv.className = 'breakdown-bar';

        const barFill = document.createElement('div');
        barFill.className = 'breakdown-bar-fill';
        barFill.style.width = ((item.count / maxCount * 100).toFixed(1)) + '%';

        barDiv.appendChild(barFill);
        flexDiv.appendChild(labelDiv);
        flexDiv.appendChild(barDiv);

        const valueDiv = document.createElement('div');
        valueDiv.className = 'breakdown-value';
        valueDiv.textContent = item.count.toLocaleString();

        breakdownItem.appendChild(flexDiv);
        breakdownItem.appendChild(valueDiv);
        container.appendChild(breakdownItem);
    });
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

// Cache metrics are provided by /v1/admin/stats - no separate fetch needed

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

    // Refresh every 30 seconds
    setInterval(fetchPublicStats, 30000);
    setInterval(fetchDetailedAnalytics, 30000);
    setInterval(fetchSystemHealth, 30000);
    setInterval(fetchMemoryMetrics, 30000);
});