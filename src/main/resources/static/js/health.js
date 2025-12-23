async function loadHealth() {
    try {
        const response = await fetch('/actuator/health');

        if (!response.ok) {
            console.error('Failed to fetch health data:', response.status);
            const overallStatus = document.getElementById('overall-status');
            overallStatus.className = 'status-badge down';
            overallStatus.innerHTML = '';
            const indicator = document.createElement('span');
            indicator.className = 'status-indicator';
            const statusText = document.createElement('span');
            statusText.textContent = 'System Status: Error';
            overallStatus.appendChild(indicator);
            overallStatus.appendChild(statusText);
            return;
        }

        const data = await response.json();

        const overallStatus = document.getElementById('overall-status');
        const contentDiv = document.getElementById('content');

        // Update overall status
        const isUp = data.status === 'UP';
        overallStatus.className = `status-badge ${isUp ? 'up' : 'down'}`;
        overallStatus.innerHTML = '';
        const indicator = document.createElement('span');
        indicator.className = 'status-indicator ' + (isUp ? 'up' : 'down');
        const statusText = document.createElement('span');
        statusText.textContent = 'System Status: ' + data.status;
        overallStatus.appendChild(indicator);
        overallStatus.appendChild(statusText);

        // Build components grid
        contentDiv.innerHTML = '';
        const componentsDiv = document.createElement('div');
        componentsDiv.className = 'components';

        if (data.components) {
            Object.entries(data.components).forEach(([name, component]) => {
                const componentElement = renderComponent(name, component);
                componentsDiv.appendChild(componentElement);
            });
        }
        contentDiv.appendChild(componentsDiv);

        updateTimestamp();
        // Refresh every 30 seconds
        setTimeout(loadHealth, 30000);
    } catch (error) {
        const contentDiv = document.getElementById('content');
        contentDiv.innerHTML = '';
        const errorDiv = document.createElement('div');
        errorDiv.className = 'error';

        const strong = document.createElement('strong');
        strong.textContent = 'Failed to load health status';

        const p = document.createElement('p');
        p.textContent = error.message;

        errorDiv.appendChild(strong);
        errorDiv.appendChild(p);
        contentDiv.appendChild(errorDiv);
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
                valueSpan.className = 'detail-value' + (isBytes(key) ? ' bytes' : '');
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

    // Cache metrics are provided by /v1/admin/stats if available
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