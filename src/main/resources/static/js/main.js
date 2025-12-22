// API Configuration
const API_BASE_URL = window.location.origin;

// Stats Fetching
const Stats = {
    currentStats: { totalRequests: 0, totalUsers: 0, requestsToday: 0 },

    async init() {
        if (this.hasStatsElements()) {
            await this.fetchStats();
            setInterval(() => this.fetchStats(), 5000);
        }
    },

    hasStatsElements() {
        const statsElements = [
            'total-requests',
            'total-users',
            'requests-today',
            'total-requests-stat',
            'total-users-stat',
            'total-requests-metric'
        ];
        return statsElements.some(id => document.getElementById(id));
    },

    async fetchStats() {
        try {
            // Add timeout to fetch
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 5000);

            const response = await fetch(`${API_BASE_URL}/api/stats`, {
                signal: controller.signal
            });
            
            clearTimeout(timeoutId);

            if (response.ok) {
                const data = await response.json();
                this.updateDisplay(data);
            } else {
                this.showPlaceholder();
            }
        } catch (error) {
            console.error('Failed to fetch stats:', error);
            this.showPlaceholder();
        }
    },

    updateDisplay(data) {
        const requestsEl = document.getElementById('total-requests');
        const usersEl = document.getElementById('total-users');
        const requestsTodayEl = document.getElementById('requests-today');

        if (requestsEl) requestsEl.textContent = data.totalRequests.toLocaleString();
        if (requestsTodayEl) requestsTodayEl.textContent = data.requestsToday.toLocaleString();
        if (usersEl) usersEl.textContent = data.totalUsers.toLocaleString();
    },

    showPlaceholder() {
        const requestsEl = document.getElementById('total-requests');
        const usersEl = document.getElementById('total-users');
        const requestsTodayEl = document.getElementById('requests-today');

        if (requestsEl && requestsEl.textContent === 'Loading...') requestsEl.textContent = '---';
        if (requestsTodayEl && requestsTodayEl.textContent === 'Loading...') requestsTodayEl.textContent = '---';
        if (usersEl && usersEl.textContent === 'Loading...') usersEl.textContent = '---';
    }
};

// OAuth Login
const Auth = {
    isLoggedIn: false,

    async init() {
        const loginBtn = document.getElementById('login-btn');
        if (!loginBtn) return;

        await this.checkAuthStatus();
        loginBtn.addEventListener('click', (e) => this.handleClick(e));
    },

    async checkAuthStatus() {
        try {
            const response = await fetch(`${API_BASE_URL}/v1/auth/session`, {
                credentials: 'include',
                redirect: 'manual'
            });

            if (response.status === 204) {
                this.isLoggedIn = false;
                this.updateButtonForLoggedOut();
                return;
            }

            if (response.type === 'opaqueredirect' || response.status === 0) {
                this.isLoggedIn = false;
                this.updateButtonForLoggedOut();
                return;
            }

            if (response.ok) {
                const user = await response.json();
                this.isLoggedIn = true;
                this.updateButtonForLoggedIn(user);
            } else {
                this.isLoggedIn = false;
                this.updateButtonForLoggedOut();
            }
        } catch (error) {
            this.isLoggedIn = false;
            this.updateButtonForLoggedOut();
        }
    },

    updateButtonForLoggedIn(user) {
        const loginBtn = document.getElementById('login-btn');
        if (loginBtn) {
            if (!loginBtn.textContent.includes('Dashboard')) {
                loginBtn.innerHTML = '<span>Dashboard</span>';
            }
            loginBtn.classList.add('logged-in');
        }
    },

    updateButtonForLoggedOut() {
        const loginBtn = document.getElementById('login-btn');
        if (loginBtn) {
            if (!loginBtn.textContent.includes('Login')) {
                loginBtn.innerHTML = '<span>Login with Google</span>';
            }
            loginBtn.classList.remove('logged-in');
        }
    },

    handleClick(e) {
        e.preventDefault();
        if (this.isLoggedIn) {
            window.location.href = '/dashboard';
        } else {
            this.login();
        }
    },

    login() {
        window.location.href = `${API_BASE_URL}/oauth2/authorization/google`;
    }
};

// API Demo Playground
const APIDemo = {
    currentEndpoint: 'ip',
    currentLang: 'curl',

    init() {
        const endpointSelect = document.getElementById('api-endpoint');
        const executeBtn = document.getElementById('demo-execute');
        const copyBtn = document.getElementById('copy-code');

        if (!endpointSelect || !executeBtn || !copyBtn) return;

        endpointSelect.addEventListener('change', (e) => {
            this.currentEndpoint = e.target.value;
            this.renderInputs();
            this.updateCodeExample();
        });

        executeBtn.addEventListener('click', () => this.executeRequest());
        copyBtn.addEventListener('click', () => this.copyCode());

        document.querySelectorAll('.code-tab').forEach(tab => {
            tab.addEventListener('click', (e) => {
                document.querySelectorAll('.code-tab').forEach(t => t.classList.remove('active'));
                e.target.classList.add('active');
                this.currentLang = e.target.dataset.lang;
                this.updateCodeExample();
            });
        });

        this.renderInputs();
        this.updateCodeExample();
    },

    renderInputs() {
        const container = document.getElementById('demo-inputs');
        const configs = {
            ip: [],
            whoami: [],
            email: [
                { name: 'email', label: 'Email Address', type: 'text', placeholder: 'user@example.com', required: true }
            ],
            unit: [
                { name: 'from', label: 'From Unit', type: 'select', options: ['mi', 'km', 'ft', 'm', 'yd', 'in', 'cm'], required: true },
                { name: 'to', label: 'To Unit', type: 'select', options: ['km', 'mi', 'm', 'ft', 'cm', 'in', 'yd'], required: true },
                { name: 'value', label: 'Value', type: 'number', placeholder: '100', required: true }
            ],
            byte: [
                { name: 'from', label: 'From Unit', type: 'select', options: ['B', 'KB', 'MB', 'GB', 'TB', 'KiB', 'MiB', 'GiB', 'TiB'], required: true },
                { name: 'to', label: 'To Unit', type: 'select', options: ['KB', 'MB', 'GB', 'TB', 'B', 'KiB', 'MiB', 'GiB', 'TiB'], required: true },
                { name: 'value', label: 'Value', type: 'number', placeholder: '1024', required: true }
            ],
            color: [
                { name: 'from', label: 'From Format', type: 'select', options: ['hex', 'rgb', 'hsl'], required: true },
                { name: 'to', label: 'To Format', type: 'select', options: ['rgb', 'hsl', 'hex'], required: true },
                { name: 'value', label: 'Color Value', type: 'text', placeholder: '#ffcc00 or rgb(255,204,0)', required: true }
            ],
            contrast: [
                { name: 'fg', label: 'Foreground Color (HEX)', type: 'text', placeholder: '#000000', required: true },
                { name: 'bg', label: 'Background Color (HEX)', type: 'text', placeholder: '#ffffff', required: true }
            ]
        };

        const inputs = configs[this.currentEndpoint] || [];

        if (inputs.length === 0) {
            container.innerHTML = '<p style="color: var(--text-muted); text-align: center;">No parameters required for this endpoint.</p>';
            return;
        }

        container.innerHTML = inputs.map(input => {
            if (input.type === 'select') {
                return `
                    <div class="input-group">
                        <label for="input-${input.name}">${input.label}${input.required ? ' *' : ''}</label>
                        <select id="input-${input.name}" ${input.required ? 'required' : ''}>
                            ${input.options.map(opt => `<option value="${opt}">${opt}</option>`).join('')}
                        </select>
                    </div>
                `;
            } else {
                return `
                    <div class="input-group">
                        <label for="input-${input.name}">${input.label}${input.required ? ' *' : ''}</label>
                        <input
                            type="${input.type}"
                            id="input-${input.name}"
                            placeholder="${input.placeholder || ''}"
                            ${input.required ? 'required' : ''}
                        >
                    </div>
                `;
            }
        }).join('');
    },

    getInputValues() {
        const inputs = document.querySelectorAll('#demo-inputs input, #demo-inputs select');
        const values = {};
        inputs.forEach(input => {
            const name = input.id.replace('input-', '');
            values[name] = input.value;
        });
        return values;
    },

    buildURL() {
        const endpoints = {
            ip: '/v1/ip',
            whoami: '/v1/whoami',
            email: '/v1/email/validate',
            unit: '/v1/convert/units',
            byte: '/v1/convert/bytes',
            color: '/v1/color/convert',
            contrast: '/v1/color/contrast'
        };

        const base = endpoints[this.currentEndpoint];
        const params = this.getInputValues();

        if (Object.keys(params).length === 0) {
            return `${API_BASE_URL}${base}`;
        }

        const query = new URLSearchParams(params).toString();
        return `${API_BASE_URL}${base}?${query}`;
    },

    async executeRequest() {
        const outputEl = document.getElementById('demo-output');
        const statusEl = document.getElementById('response-status');
        const timeEl = document.getElementById('response-time');

        outputEl.textContent = 'Loading...';
        statusEl.textContent = '';
        timeEl.textContent = '';

        const startTime = performance.now();

        try {
            const url = this.buildURL();
            const response = await fetch(url);
            const endTime = performance.now();
            const duration = Math.round(endTime - startTime);

            const data = await response.json();

            outputEl.textContent = JSON.stringify(data, null, 2);

            statusEl.textContent = `${response.status} ${response.statusText}`;
            statusEl.className = `response-status ${response.ok ? 'success' : 'error'}`;

            timeEl.textContent = `${duration}ms`;
        } catch (error) {
            const endTime = performance.now();
            const duration = Math.round(endTime - startTime);

            outputEl.textContent = `Error: ${error.message}`;
            statusEl.textContent = 'Error';
            statusEl.className = 'response-status error';
            timeEl.textContent = `${duration}ms`;
        }
    },

    updateCodeExample() {
        const codeEl = document.getElementById('code-example');
        const url = this.buildURL();

        const examples = {
            curl: `curl -X GET "${url}" \\
  -H "Accept: application/json"`,

            javascript: `fetch('${url}', {
  method: 'GET',
  headers: {
    'Accept': 'application/json'
  }
})
  .then(response => response.json())
  .then(data => console.log(data))
  .catch(error => console.error('Error:', error));`,

            java: `import java.net.http.*;
import java.net.URI;

HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("${url}"))
    .header("Accept", "application/json")
    .GET()
    .build();

HttpResponse<String> response = client.send(
    request,
    HttpResponse.BodyHandlers.ofString()
);

System.out.println(response.body());`
        };

        codeEl.textContent = examples[this.currentLang] || examples.curl;
    },

    copyCode() {
        const codeEl = document.getElementById('code-example');
        const text = codeEl.textContent;

        navigator.clipboard.writeText(text).then(() => {
            const btn = document.getElementById('copy-code');
            const originalText = btn.textContent;
            btn.textContent = 'Copied!';
            setTimeout(() => {
                btn.textContent = originalText;
            }, 2000);
        });
    }
};

// Smooth Scroll
const SmoothScroll = {
    init() {
        document.querySelectorAll('a[href^="#"]').forEach(anchor => {
            anchor.addEventListener('click', function (e) {
                const href = this.getAttribute('href');
                if (href === '#') return;
                e.preventDefault();
                const target = document.querySelector(href);
                if (target) {
                    const offset = 80;
                    const targetPosition = target.offsetTop - offset;
                    window.scrollTo({ top: targetPosition, behavior: 'smooth' });
                }
            });
        });
    }
};

// Navbar Scroll
const Navbar = {
    init() {
        const navbar = document.querySelector('.navbar');
        if (!navbar) return;
        window.addEventListener('scroll', () => {
            const currentScroll = window.pageYOffset;
            if (currentScroll <= 0) {
                navbar.style.boxShadow = 'none';
            } else {
                navbar.style.boxShadow = '0 2px 12px rgba(0, 0, 0, 0.3)';
            }
        });
    }
};

// Mobile Menu
const MobileMenu = {
    init() {
        const menuBtn = document.getElementById('mobile-menu-btn');
        const navLinks = document.querySelector('.nav-links');

        if (!menuBtn || !navLinks) return;

        menuBtn.addEventListener('click', () => {
            navLinks.classList.toggle('active');
        });

        document.addEventListener('click', (e) => {
            if (!menuBtn.contains(e.target) && !navLinks.contains(e.target)) {
                navLinks.classList.remove('active');
            }
        });
    }
};

// Cookie Consent
const CookieConsent = {
    init() {
        if (!localStorage.getItem('synaxic_cookie_consent')) {
            document.getElementById('cookie-consent').classList.remove('hidden');
        }
        
        const acceptBtn = document.getElementById('cookie-accept-all');
        if (acceptBtn) {
            acceptBtn.addEventListener('click', () => {
                localStorage.setItem('synaxic_cookie_consent', 'true');
                document.getElementById('cookie-consent').classList.add('hidden');
            });
        }
    }
};

// Initialize Everything
document.addEventListener('DOMContentLoaded', () => {
    // Immediately show hero
    document.body.classList.add('hero-ready');

    // Init components
    Stats.init();
    Auth.init();
    APIDemo.init();
    SmoothScroll.init();
    Navbar.init();
    MobileMenu.init();
    CookieConsent.init();
});

