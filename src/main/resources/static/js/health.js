async function loadHealth() {
    try {
        const response = await fetch('/actuator/health');

        if (!response.ok) {
            console.error('Failed to fetch health data:', response.status);
            document.getElementById('overall-status').className = 'status-badge down';
            document.getElementById('overall-status').innerHTML = '<span class="status-indicator"></span><span>System Status: Error</span>';
            return;
        }

        const data = await response.json();

        const overallStatus = document.getElementById('overall-status');
        const contentDiv = document.getElementById('content');

        // Update overall status
        const isUp = data.status === 'UP';
        overallStatus.className = `status-badge ${isUp ? 'up' : 'down'}`;
        overallStatus.innerHTML = `
            <span class="status-indicator ${isUp ? 'up' : 'down'}"></span>
            <span>System Status: ${data.status}</span>
        `;

        // Build components grid
        if (data.components) {
            const componentsHtml = Object.entries(data.components)
                .map(([name, component]) => renderComponent(name, component))
                .join('');
            contentDiv.innerHTML = `<div class="components">${componentsHtml}</div>`;
        } else {
            contentDiv.innerHTML = '<div class="components"></div>';
        }

        updateTimestamp();
        // Refresh every 30 seconds
        setTimeout(loadHealth, 30000);
    } catch (error) {
        document.getElementById('content').innerHTML = `
            <div class="error">
                <strong>Failed to load health status</strong>
                <p>${error.message}</p>
            </div>
        `;
    }
}

function renderComponent(name, component) {
    const isUp = component.status === 'UP';
    const displayName = formatComponentName(name);

    let detailsHtml = '';
    if (component.details) {
        detailsHtml = Object.entries(component.details)
            .filter(([key]) => key !== 'path') // Skip path
            .map(([key, value]) => {
                const displayValue = formatValue(value);
                return `
                    <div class="detail-item">
                        <span class="detail-label">${formatKey(key)}</span>
                        <span class="detail-value ${isBytes(key) ? 'bytes' : ''}">${displayValue}</span>
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

function isBytes(key) {
    return key.includes('total') || key.includes('free') || key.includes('threshold');
}

function updateTimestamp() {
    const now = new Date();
    document.getElementById('last-updated').textContent = now.toLocaleTimeString();
}

async function loadMetrics() {
    try {
        // Load public stats
        const statsResponse = await fetch('/api/stats');
        const statsData = await statsResponse.json();
        document.getElementById('total-requests-metric').textContent = statsData.totalRequests.toLocaleString();
        document.getElementById('users-metric').textContent = statsData.totalUsers.toLocaleString();
    } catch (error) {
        console.error('Error fetching stats:', error);
    }

    try {
        // Load detailed admin stats
        const adminResponse = await fetch('/v1/admin/stats');
        if (!adminResponse.ok) return;

        const adminData = await adminResponse.json();

        // Update request metrics
        if (adminData.latency?.p50_ms) {
            document.getElementById('avg-response-metric').textContent = adminData.latency.p50_ms.toFixed(0) + ' ms';
        }

        if (adminData.requests?.total > 0) {
            const totalErrors = (adminData.requests?.clientErrorCount || 0) + (adminData.requests?.serverErrorCount || 0);
            const successRate = (((adminData.requests.total - totalErrors) / adminData.requests.total) * 100).toFixed(1);
            document.getElementById('success-rate-metric').textContent = successRate + '%';
        }

        // Update uptime
        if (adminData.uptime) {
            document.getElementById('uptime-metric').textContent = adminData.uptime;
        }

        // Update latency percentiles
        if (adminData.latency?.p50_ms) {
            document.getElementById('latency-p50-metric').textContent = adminData.latency.p50_ms.toFixed(0);
        }
        if (adminData.latency?.p95_ms) {
            document.getElementById('latency-p95-metric').textContent = adminData.latency.p95_ms.toFixed(0);
        }
        if (adminData.latency?.p99_ms) {
            document.getElementById('latency-p99-metric').textContent = adminData.latency.p99_ms.toFixed(0);
        }

        // Active sessions placeholder (would need backend support)
        document.getElementById('sessions-metric').textContent = 'N/A';
    } catch (error) {
        console.error('Error fetching admin stats:', error);
    }

    // Load memory usage from actuator/metrics
    try {
        const metricsResponse = await fetch('/actuator/metrics/jvm.memory.used');
        if (metricsResponse.ok) {
            const metricsData = await metricsResponse.json();
            const memoryUsed = metricsData.measurements?.[0]?.value || 0;
            document.getElementById('memory-usage').textContent = formatBytes(memoryUsed);
        } else {
            document.getElementById('memory-usage').textContent = 'N/A';
        }
    } catch (error) {
        console.error('Error fetching memory metrics:', error);
        document.getElementById('memory-usage').textContent = 'N/A';
    }

    // Load cache metrics if available
    try {
        const cacheResponse = await fetch('/actuator/metrics/cache.gets');
        if (cacheResponse.ok) {
            const cacheData = await cacheResponse.json();
            const hits = cacheData.measurements?.find(m => m.statistic === 'COUNT')?.value || 0;
            document.getElementById('cache-hit-rate').textContent = hits > 0 ? hits.toFixed(0) + ' hits' : 'No data';
        } else {
            document.getElementById('cache-hit-rate').textContent = 'N/A';
        }
    } catch (error) {
        console.error('Error fetching cache metrics:', error);
        document.getElementById('cache-hit-rate').textContent = 'N/A';
    }
}

function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// Load on page load
document.addEventListener('DOMContentLoaded', () => {
    loadHealth();
    loadMetrics();
    // Refresh both every 30 seconds
    setInterval(loadHealth, 30000);
    setInterval(loadMetrics, 30000);
});