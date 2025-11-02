// API Configuration
const API_BASE_URL = window.location.origin;

// Cookie Consent Management (GDPR Compliant)
const CookieConsent = {
    STORAGE_KEY: 'synaxic_cookie_consent',

    init() {
        const consent = this.getConsent();
        if (!consent) {
            this.showBanner();
        } else {
            this.applyConsent(consent);
        }

        // Event listeners - check if elements exist first
        const acceptAllBtn = document.getElementById('cookie-accept-all');
        const essentialBtn = document.getElementById('cookie-essential');
        const declineBtn = document.getElementById('cookie-decline');

        if (acceptAllBtn) acceptAllBtn.addEventListener('click', () => this.acceptAll());
        if (essentialBtn) essentialBtn.addEventListener('click', () => this.acceptEssential());
        if (declineBtn) declineBtn.addEventListener('click', () => this.declineAll());
    },

    showBanner() {
        const banner = document.getElementById('cookie-consent');
        if (banner) banner.classList.remove('hidden');
    },

    hideBanner() {
        const banner = document.getElementById('cookie-consent');
        if (banner) banner.classList.add('hidden');
    },

    getConsent() {
        const stored = localStorage.getItem(this.STORAGE_KEY);
        return stored ? JSON.parse(stored) : null;
    },

    saveConsent(consent) {
        localStorage.setItem(this.STORAGE_KEY, JSON.stringify(consent));
    },

    acceptAll() {
        const consent = { essential: true, analytics: true, timestamp: Date.now() };
        this.saveConsent(consent);
        this.applyConsent(consent);
        this.hideBanner();
    },

    acceptEssential() {
        const consent = { essential: true, analytics: false, timestamp: Date.now() };
        this.saveConsent(consent);
        this.applyConsent(consent);
        this.hideBanner();
    },

    declineAll() {
        const consent = { essential: false, analytics: false, timestamp: Date.now() };
        this.saveConsent(consent);
        this.applyConsent(consent);
        this.hideBanner();
    },

    applyConsent(consent) {
        // Essential cookies are always enabled for session management
        // Analytics can be conditionally enabled based on consent
        if (consent.analytics) {
            console.log('Analytics cookies enabled');
            // Initialize analytics here if needed
        }
    }
};

// Mobile Menu Toggle
const MobileMenu = {
    init() {
        const menuBtn = document.getElementById('mobile-menu-btn');
        const navLinks = document.querySelector('.nav-links');

        // Only initialize if menu elements exist
        if (!menuBtn || !navLinks) return;

        menuBtn.addEventListener('click', () => {
            navLinks.classList.toggle('active');
        });

        // Close menu when clicking outside
        document.addEventListener('click', (e) => {
            if (!menuBtn.contains(e.target) && !navLinks.contains(e.target)) {
                navLinks.classList.remove('active');
            }
        });

        // Close menu when clicking on a link
        navLinks.querySelectorAll('a').forEach(link => {
            link.addEventListener('click', () => {
                navLinks.classList.remove('active');
            });
        });
    }
};

// Stats Fetching
const Stats = {
    currentStats: { totalRequests: 0, totalUsers: 0 },

    async init() {
        // Only fetch stats if the page has stats elements
        if (this.hasStatsElements()) {
            await this.fetchStats();
            // Refresh stats every 5 seconds for live updates
            setInterval(() => this.fetchStats(), 5000);
        }
    },

    hasStatsElements() {
        // Check if the page has any stats-related elements
        const statsElements = [
            'total-requests',
            'total-users',
            'total-requests-stat',
            'total-users-stat',
            'total-requests-metric'
        ];

        return statsElements.some(id => document.getElementById(id));
    },

    async fetchStats() {
        try {
            const response = await fetch(`${API_BASE_URL}/api/stats`);
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

        // Only update if elements exist on this page
        if (!requestsEl || !usersEl) return;

        // Update requests with animation
        const oldRequestsText = this.formatNumber(this.currentStats.totalRequests);
        const newRequestsText = this.formatNumber(data.totalRequests);
        if (newRequestsText !== oldRequestsText && oldRequestsText !== '0') {
            this.animateNumberChange(requestsEl, this.currentStats.totalRequests, data.totalRequests);
        } else {
            requestsEl.textContent = newRequestsText;
        }
        this.currentStats.totalRequests = data.totalRequests;

        // Update users with animation
        const oldUsersText = this.formatNumber(this.currentStats.totalUsers);
        const newUsersText = this.formatNumber(data.totalUsers);
        if (newUsersText !== oldUsersText && oldUsersText !== '0') {
            this.animateNumberChange(usersEl, this.currentStats.totalUsers, data.totalUsers);
        } else {
            usersEl.textContent = newUsersText;
        }
        this.currentStats.totalUsers = data.totalUsers;
    },

    animateNumberChange(element, oldValue, newValue) {
        const oldText = this.formatNumber(oldValue);
        const newText = this.formatNumber(newValue);
        const direction = newValue > oldValue ? 'up' : 'down';

        // Pad shorter string with spaces to align digits
        const maxLen = Math.max(oldText.length, newText.length);
        const oldPadded = oldText.padStart(maxLen, ' ');
        const newPadded = newText.padStart(maxLen, ' ');

        // Create container for all digits
        const container = document.createElement('span');
        container.className = 'odometer-container';
        container.style.display = 'inline-flex';
        container.style.alignItems = 'center';

        // Process each character position
        for (let i = 0; i < maxLen; i++) {
            const oldChar = oldPadded[i];
            const newChar = newPadded[i];

            const digitWrapper = document.createElement('span');
            digitWrapper.className = 'odometer-digit-wrapper';
            digitWrapper.style.display = 'inline-block';
            digitWrapper.style.position = 'relative';
            digitWrapper.style.overflow = 'hidden';
            digitWrapper.style.height = '1.2em';
            digitWrapper.style.lineHeight = '1.2em';
            digitWrapper.style.verticalAlign = 'baseline';

            if (oldChar === ' ' && newChar === ' ') {
                digitWrapper.style.minWidth = '0';
            } else if (oldChar === ' ' || newChar === ' ') {
                digitWrapper.style.minWidth = '0.3em';
            } else {
                digitWrapper.style.minWidth = '0.6em';
            }

            if (oldChar === newChar) {
                // No change - display static character
                const staticChar = document.createElement('span');
                staticChar.textContent = oldChar;
                staticChar.style.display = 'inline-block';
                staticChar.style.height = '100%';
                staticChar.style.lineHeight = '1.2em';
                digitWrapper.appendChild(staticChar);
            } else {
                // Character changed - animate it
                const roller = document.createElement('span');
                roller.className = 'odometer-roller';
                roller.style.display = 'block';
                roller.style.position = 'relative';
                roller.style.transition = 'transform 0.5s cubic-bezier(0.34, 1.56, 0.64, 1)';

                // Old character
                const oldCharSpan = document.createElement('span');
                oldCharSpan.textContent = oldChar;
                oldCharSpan.style.display = 'block';
                oldCharSpan.style.height = '1.2em';
                oldCharSpan.style.lineHeight = '1.2em';
                oldCharSpan.style.textAlign = 'center';

                // New character
                const newCharSpan = document.createElement('span');
                newCharSpan.textContent = newChar;
                newCharSpan.style.display = 'block';
                newCharSpan.style.height = '1.2em';
                newCharSpan.style.lineHeight = '1.2em';
                newCharSpan.style.textAlign = 'center';

                // Append in correct order based on direction
                if (direction === 'up') {
                    roller.appendChild(oldCharSpan);
                    roller.appendChild(newCharSpan);
                } else {
                    roller.appendChild(newCharSpan);
                    roller.appendChild(oldCharSpan);
                }

                digitWrapper.appendChild(roller);

                // Set initial transform before animation
                roller.style.transform = direction === 'up'
                    ? 'translateY(0)'
                    : 'translateY(-1.2em)';

                // Trigger animation on next frame
                requestAnimationFrame(() => {
                    requestAnimationFrame(() => {
                        roller.style.transform = direction === 'up'
                            ? 'translateY(-1.2em)'
                            : 'translateY(0)';
                    });
                });
            }

            container.appendChild(digitWrapper);
        }

        element.textContent = '';
        element.appendChild(container);

        // Clean up after animation
        setTimeout(() => {
            element.textContent = newText;
        }, 550);
    },

    showPlaceholder() {
        const requestsEl = document.getElementById('total-requests');
        const usersEl = document.getElementById('total-users');

        // Only show placeholders if elements exist
        if (!requestsEl || !usersEl) return;

        requestsEl.textContent = '---';
        usersEl.textContent = '---';
    },

    formatNumber(num) {
        if (num >= 1000000) {
            return (num / 1000000).toFixed(1) + 'M';
        } else if (num >= 1000) {
            return (num / 1000).toFixed(1) + 'K';
        }
        return num.toString();
    }
};

// OAuth Login
const Auth = {
    isLoggedIn: false,

    async init() {
        const loginBtn = document.getElementById('login-btn');
        if (!loginBtn) return; // Exit if button doesn't exist on this page

        // Check auth status first (async)
        await this.checkAuthStatus();

        // Add event listener after auth check
        loginBtn.addEventListener('click', (e) => this.handleClick(e));
    },

    async checkAuthStatus() {
        try {
            const response = await fetch(`${API_BASE_URL}/v1/auth/session`, {
                credentials: 'include',
                redirect: 'manual' // Don't follow redirects - prevents CORS errors
            });

            // redirect: 'manual' makes fetch return opaque response for redirects
            // Check if we got a successful response (not a redirect)
            if (response.type === 'opaqueredirect' || response.status === 0) {
                // Got redirected (user not authenticated)
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
            // Only update if it doesn't already say "Dashboard"
            if (!loginBtn.textContent.includes('Dashboard')) {
                loginBtn.innerHTML = '<span>Dashboard</span>';
            }
            loginBtn.classList.add('logged-in');
        }
    },

    updateButtonForLoggedOut() {
        const loginBtn = document.getElementById('login-btn');
        if (loginBtn) {
            // Only update if it doesn't already say "Login with Google"
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

        // Only initialize if demo elements exist on this page
        if (!endpointSelect || !executeBtn || !copyBtn) return;

        endpointSelect.addEventListener('change', (e) => {
            this.currentEndpoint = e.target.value;
            this.renderInputs();
            this.updateCodeExample();
        });

        executeBtn.addEventListener('click', () => this.executeRequest());

        copyBtn.addEventListener('click', () => this.copyCode());

        // Code language tabs
        document.querySelectorAll('.code-tab').forEach(tab => {
            tab.addEventListener('click', (e) => {
                document.querySelectorAll('.code-tab').forEach(t => t.classList.remove('active'));
                e.target.classList.add('active');
                this.currentLang = e.target.dataset.lang;
                this.updateCodeExample();
            });
        });

        // Initialize with default endpoint
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
        }).catch(err => {
            console.error('Failed to copy:', err);
        });
    }
};

// Smooth Scroll for Navigation Links
const SmoothScroll = {
    init() {
        document.querySelectorAll('a[href^="#"]').forEach(anchor => {
            anchor.addEventListener('click', function (e) {
                const href = this.getAttribute('href');
                if (href === '#') return;

                e.preventDefault();
                const target = document.querySelector(href);
                if (target) {
                    const offset = 80; // Navbar height
                    const targetPosition = target.offsetTop - offset;
                    window.scrollTo({
                        top: targetPosition,
                        behavior: 'smooth'
                    });
                }
            });
        });
    }
};

// Navbar Scroll Effect
const Navbar = {
    init() {
        const navbar = document.querySelector('.navbar');
        if (!navbar) return;

        let lastScroll = 0;

        window.addEventListener('scroll', () => {
            const currentScroll = window.pageYOffset;

            if (currentScroll <= 0) {
                navbar.style.boxShadow = 'none';
            } else {
                navbar.style.boxShadow = '0 2px 12px rgba(0, 0, 0, 0.3)';
            }

            lastScroll = currentScroll;
        });
    }
};

// Scroll Progress Indicator
const ScrollProgress = {
    init() {
        // Create progress bar
        const progressBar = document.createElement('div');
        progressBar.className = 'scroll-progress';
        document.body.appendChild(progressBar);

        window.addEventListener('scroll', () => {
            const scrollTop = window.pageYOffset;
            const docHeight = document.documentElement.scrollHeight - window.innerHeight;
            const scrollPercent = (scrollTop / docHeight) * 100;
            progressBar.style.width = scrollPercent + '%';
        });
    }
};

// Parallax Effect
const Parallax = {
    init() {
        const elements = document.querySelectorAll('.hero-background');

        window.addEventListener('scroll', () => {
            const scrolled = window.pageYOffset;
            elements.forEach(el => {
                const speed = 0.5;
                el.style.transform = `translateY(${scrolled * speed}px)`;
            });
        });
    }
};

// Number Counter Animation
const CounterAnimation = {
    animateValue(element, start, end, duration) {
        const range = end - start;
        const increment = end > start ? 1 : -1;
        const stepTime = Math.abs(Math.floor(duration / range));
        let current = start;

        const timer = setInterval(() => {
            current += increment;
            if (element.textContent) {
                element.textContent = this.formatNumber(current);
            }
            if (current === end) {
                clearInterval(timer);
            }
        }, stepTime);
    },

    formatNumber(num) {
        if (num >= 1000000) {
            return (num / 1000000).toFixed(1) + 'M';
        } else if (num >= 1000) {
            return (num / 1000).toFixed(1) + 'K';
        }
        return num.toString();
    }
};

// Intersection Observer for Animation on Scroll
const AnimateOnScroll = {
    init() {
        const observer = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    entry.target.style.opacity = '1';
                    entry.target.style.transform = 'translateY(0)';

                    // Remove inline styles after animation completes to allow CSS hover effects
                    setTimeout(() => {
                        entry.target.style.opacity = '';
                        entry.target.style.transform = '';
                        entry.target.style.transition = '';
                    }, 600); // Match animation duration
                }
            });
        }, { threshold: 0.1 });

        document.querySelectorAll('.feature-card, .stack-card, .contact-card').forEach(el => {
            el.style.opacity = '0';
            el.style.transform = 'translateY(30px)';
            el.style.transition = 'opacity 0.6s ease, transform 0.6s ease';
            observer.observe(el);
        });
    }
};

// Initialize Everything
document.addEventListener('DOMContentLoaded', () => {
    CookieConsent.init();
    MobileMenu.init();
    Stats.init();
    Auth.init();
    APIDemo.init();
    SmoothScroll.init();
    Navbar.init();
    AnimateOnScroll.init();
    ScrollProgress.init();
    Parallax.init();

    // Initialize keyboard support
    initKeyboardSupport();
});

// Keyboard support for forms and inputs
function initKeyboardSupport() {
    // Enter key support for API demo inputs
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            const activeElement = document.activeElement;

            // Handle Enter key in API demo inputs
            if (activeElement && activeElement.classList.contains('form-input')) {
                const demoInputs = document.getElementById('demo-inputs');
                if (demoInputs && demoInputs.contains(activeElement)) {
                    // Find and click the execute button
                    const executeBtn = document.getElementById('demo-execute');
                    if (executeBtn && !executeBtn.disabled) {
                        executeBtn.click();
                        return;
                    }
                }
            }

            // Handle Enter key in cookie consent inputs
            if (activeElement && activeElement.type === 'text' || activeElement.type === 'email') {
                const form = activeElement.closest('form');
                if (form) {
                    const submitBtn = form.querySelector('button[type="submit"], .btn-primary');
                    if (submitBtn && !submitBtn.disabled) {
                        e.preventDefault();
                        submitBtn.click();
                        return;
                    }
                }
            }
        }
    });
}
