// API Configuration
const API_BASE_URL = window.location.origin;

// Browser Capability Detection and Performance Profiling
const AnimationProfile = {
    capabilities: null,

    init() {
        this.capabilities = this.detectCapabilities();
        console.log('Animation capabilities detected:', this.capabilities);
    },

    detectCapabilities() {
        const features = {
            // Core animation support
            hasRequestAnimationFrame: 'requestAnimationFrame' in window,
            hasCSSTransforms: 'transform' in document.documentElement.style,
            hasCSSTransforms3d: 'webkitPerspective' in document.documentElement.style || 'perspective' in document.documentElement.style,
            hasWillChange: 'willChange' in document.documentElement.style,

            // Browser detection
            isChrome: /Chrome/.test(navigator.userAgent) && /Google Inc/.test(navigator.vendor),
            isFirefox: /Firefox/.test(navigator.userAgent),
            isSafari: /Safari/.test(navigator.userAgent) && /Apple Computer/.test(navigator.vendor),
            isEdge: /Edg/.test(navigator.userAgent),
            isIE: /MSIE|Trident/.test(navigator.userAgent),

            // Safari-specific detection
            safariVersion: this.detectSafariVersion(),

            // Device detection
            isMobile: /Android|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent),
            isTablet: /iPad|Android(?!.*Mobile)/i.test(navigator.userAgent),
            isIOS: /iPad|iPhone|iPod/.test(navigator.userAgent),

            // Performance indicators
            isLowEnd: this.detectLowEnd(),
            deviceMemory: navigator.deviceMemory || 4,
            hardwareConcurrency: navigator.hardwareConcurrency || 4,

            // Accessibility
            prefersReducedMotion: this.detectReducedMotion(),

            // Network awareness (if available)
            connectionType: this.detectConnectionType(),
            isSlowConnection: this.detectSlowConnection()
        };

        // Determine optimal frame rate and animation quality
        features.targetFrameRate = this.calculateTargetFrameRate(features);
        features.animationQuality = this.calculateAnimationQuality(features);
        features.animationMethod = this.selectAnimationMethod(features);

        return features;
    },

    detectLowEnd() {
        // Heuristics for low-end device detection
        const indicators = [
            navigator.hardwareConcurrency <= 2,
            (navigator.deviceMemory || 4) <= 2,
            /Android [1-4]/.test(navigator.userAgent),
            this.isOldDevice(),
            this.detectSlowConnection()
        ];

        return indicators.filter(Boolean).length >= 2;
    },

    isOldDevice() {
        // Check for older devices based on user agent and performance
        const oldAndroid = /Android [1-4]/.test(navigator.userAgent);
        const oldIOS = /OS [7-9]_/.test(navigator.userAgent);
        const oldChrome = /Chrome\/[0-5][0-9]\./.test(navigator.userAgent);

        return oldAndroid || oldIOS || oldChrome;
    },

    detectReducedMotion() {
        // Check for reduced motion preference
        if (window.matchMedia) {
            return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        }
        return false;
    },

    detectConnectionType() {
        if ('connection' in navigator) {
            return navigator.connection.effectiveType || 'unknown';
        }
        return 'unknown';
    },

    detectSlowConnection() {
        if ('connection' in navigator) {
            const connection = navigator.connection;
            return connection.effectiveType === 'slow-2g' ||
                   connection.effectiveType === '2g' ||
                   connection.saveData === true;
        }
        return false;
    },

    detectSafariVersion() {
        const userAgent = navigator.userAgent;
        const safariMatch = userAgent.match(/Version\/(\d+\.\d+).*Safari/);
        if (safariMatch) {
            return parseFloat(safariMatch[1]);
        }
        return null;
    },

    calculateTargetFrameRate(features) {
        if (features.prefersReducedMotion) return 15;
        if (features.isLowEnd || features.isSlowConnection) return 30;
        if (features.isMobile && !features.isTablet) return 45;
        return 60;
    },

    calculateAnimationQuality(features) {
        if (features.prefersReducedMotion) return 'minimal';
        if (features.isLowEnd || features.isSlowConnection) return 'basic';
        if (features.isMobile) return 'standard';
        // Safari on older versions may struggle with complex animations
        if (features.isSafari && features.safariVersion && features.safariVersion < 14) return 'standard';
        return 'enhanced';
    },

    selectAnimationMethod(features) {
        if (features.isIE || !features.hasRequestAnimationFrame) return 'fallback';
        if (features.isLowEnd || features.prefersReducedMotion) return 'simple';
        if (features.hasCSSTransforms3d && !features.isMobile) return 'hardware';
        return 'standard';
    },

    // Performance monitoring
    measurePerformance(callback, iterations = 10) {
        const times = [];

        const measure = () => {
            const start = performance.now();
            callback();
            const end = performance.now();
            times.push(end - start);

            if (times.length < iterations) {
                requestAnimationFrame(measure);
            } else {
                const avg = times.reduce((a, b) => a + b, 0) / times.length;
                console.log(`Average animation time: ${avg.toFixed(2)}ms`);
                return avg;
            }
        };

        measure();
    }
};

// Adaptive Performance Management System
const AdaptivePerformance = {
    activeAnimations: new Map(),
    frameInterval: null,
    isPaused: false,
    batteryLevel: null,
    visibilityState: 'visible',

    init() {
        this.setupEventListeners();
        this.startPerformanceMonitoring();
        this.setupCleanupHandlers();
    },

    setupCleanupHandlers() {
        // Clean up animations when page is unloaded
        const cleanupAll = () => {
            this.cleanupAllAnimations();
        };

        // Handle page unload
        window.addEventListener('beforeunload', cleanupAll);
        window.addEventListener('pagehide', cleanupAll);

        // Handle content visibility changes (for single page apps)
        const observer = new MutationObserver((mutations) => {
            mutations.forEach((mutation) => {
                mutation.removedNodes.forEach((node) => {
                    if (node.nodeType === Node.ELEMENT_NODE) {
                        // Clean up animations for removed elements
                        this.cleanupElementAnimations(node);
                    }
                });
            });
        });

        observer.observe(document.body, {
            childList: true,
            subtree: true
        });
    },

    cleanupElementAnimations(element) {
        // Check if element has an animation
        if (element.animationId) {
            this.unregisterAnimation(element.animationId);
        }

        // Check for child elements with animations
        const childrenWithAnimations = element.querySelectorAll('[animation-id]');
        childrenWithAnimations.forEach(child => {
            if (child.animationId) {
                this.unregisterAnimation(child.animationId);
            }
        });
    },

    setupEventListeners() {
        // Handle visibility changes (tab switching, app backgrounding)
        if ('visibilityState' in document) {
            document.addEventListener('visibilitychange', () => {
                this.visibilityState = document.visibilityState;
                this.adjustAnimationBasedOnVisibility();
            });
        }

        // Handle battery level (if available)
        if ('getBattery' in navigator) {
            navigator.getBattery().then(battery => {
                this.batteryLevel = battery.level;
                battery.addEventListener('levelchange', () => {
                    this.batteryLevel = battery.level;
                    this.adjustAnimationBasedOnBattery();
                });
            });
        }

        // Handle page unload to clean up animations
        window.addEventListener('beforeunload', () => {
            this.cleanupAllAnimations();
        });

        // Handle mobile-specific events
        if (AnimationProfile.capabilities.isMobile) {
            this.setupMobileEventListeners();
        }
    },

    setupMobileEventListeners() {
        // Pause animations during touch interactions
        let touchTimeout;

        document.addEventListener('touchstart', () => {
            this.pauseAnimations();
            clearTimeout(touchTimeout);
        });

        document.addEventListener('touchend', () => {
            touchTimeout = setTimeout(() => {
                this.resumeAnimations();
            }, 1000); // Resume after 1 second of no touch
        });

        // Handle orientation changes
        window.addEventListener('orientationchange', () => {
            this.handleOrientationChange();
        });

        // Optimize for scrolling performance
        let scrollTimeout;
        window.addEventListener('scroll', () => {
            this.pauseAnimations();
            clearTimeout(scrollTimeout);
            scrollTimeout = setTimeout(() => {
                this.resumeAnimations();
            }, 150);
        });
    },

    startPerformanceMonitoring() {
        // Monitor and adjust performance dynamically
        setInterval(() => {
            this.checkPerformanceAndAdjust();
        }, 5000); // Check every 5 seconds
    },

    checkPerformanceAndAdjust() {
        if (this.activeAnimations.size === 0) return;

        const currentTime = performance.now();
        const frameTime = 1000 / AnimationProfile.capabilities.targetFrameRate;

        // Check if animations are running smoothly
        this.activeAnimations.forEach((animation, id) => {
            if (animation.lastFrameTime) {
                const actualFrameTime = currentTime - animation.lastFrameTime;

                // If frames are taking too long, reduce quality
                if (actualFrameTime > frameTime * 1.5) {
                    this.reduceAnimationQuality(id);
                }
            }
            animation.lastFrameTime = currentTime;
        });
    },

    registerAnimation(id, animation) {
        this.activeAnimations.set(id, {
            ...animation,
            startTime: performance.now(),
            lastFrameTime: null,
            quality: AnimationProfile.capabilities.animationQuality,
            frameRate: AnimationProfile.capabilities.targetFrameRate
        });
    },

    unregisterAnimation(id) {
        this.activeAnimations.delete(id);
    },

    adjustAnimationBasedOnVisibility() {
        if (this.visibilityState === 'hidden') {
            this.pauseAnimations();
        } else {
            this.resumeAnimations();
        }
    },

    adjustAnimationBasedOnBattery() {
        if (this.batteryLevel !== null && this.batteryLevel < 0.2) {
            // Low battery - reduce animation intensity
            this.activeAnimations.forEach(animation => {
                this.reduceAnimationQuality(animation.id);
            });
        }
    },

    pauseAnimations() {
        this.isPaused = true;
        this.activeAnimations.forEach(animation => {
            if (animation.pause) {
                animation.pause();
            }
        });
    },

    resumeAnimations() {
        if (this.visibilityState === 'visible') {
            this.isPaused = false;
            this.activeAnimations.forEach(animation => {
                if (animation.resume) {
                    animation.resume();
                }
            });
        }
    },

    cleanupAllAnimations() {
        this.activeAnimations.forEach(animation => {
            if (animation.cleanup) {
                animation.cleanup();
            }
        });
        this.activeAnimations.clear();
    },

    handleOrientationChange() {
        // Briefly pause animations during orientation change
        this.pauseAnimations();
        setTimeout(() => {
            this.resumeAnimations();
        }, 500);
    },

    reduceAnimationQuality(animationId) {
        const animation = this.activeAnimations.get(animationId);
        if (animation) {
            switch (animation.quality) {
                case 'enhanced':
                    animation.quality = 'standard';
                    break;
                case 'standard':
                    animation.quality = 'basic';
                    break;
                case 'basic':
                    animation.quality = 'minimal';
                    break;
            }

            if (animation.adjustQuality) {
                animation.adjustQuality(animation.quality);
            }
        }
    },

    getOptimalFrameRate() {
        if (this.isPaused || this.visibilityState === 'hidden') {
            return 15; // Minimal frame rate when not visible
        }

        if (this.batteryLevel !== null && this.batteryLevel < 0.2) {
            return 30; // Reduce frame rate on low battery
        }

        return AnimationProfile.capabilities.targetFrameRate;
    }
};

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
    currentStats: { totalRequests: 0, totalUsers: 0, requestsToday: 0 },

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
            'requests-today',
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
        const requestsTodayEl = document.getElementById('requests-today');

        // Update total requests with animation
        if (requestsEl) {
            const oldRequestsText = this.formatNumber(this.currentStats.totalRequests);
            const newRequestsText = this.formatNumber(data.totalRequests);
            if (newRequestsText !== oldRequestsText && oldRequestsText !== '0') {
                this.animateNumberChange(requestsEl, this.currentStats.totalRequests, data.totalRequests);
            } else {
                requestsEl.textContent = newRequestsText;
            }
            this.currentStats.totalRequests = data.totalRequests;
        }

        // Update requests today with animation and timezone logic
        if (requestsTodayEl) {
            // Check if we need to reset based on user's timezone
            const shouldReset = this.shouldResetDailyCounter(data.currentUTCDate);
            const currentValue = shouldReset ? 0 : this.currentStats.requestsToday;
            const targetValue = shouldReset ? data.requestsToday : data.requestsToday;

            // Use full number format (no abbreviations) for daily requests
            const oldTodayText = this.formatFullNumber(currentValue);
            const newTodayText = this.formatFullNumber(targetValue);

            if (newTodayText !== oldTodayText && oldTodayText !== '0') {
                this.animateNumberChange(requestsTodayEl, currentValue, targetValue, true);
            } else {
                requestsTodayEl.textContent = newTodayText;
            }
            this.currentStats.requestsToday = targetValue;

            // Store the current UTC date to track resets
            localStorage.setItem('synaxic_last_utc_date', data.currentUTCDate);
        }

        // Update users with animation
        if (usersEl) {
            const oldUsersText = this.formatNumber(this.currentStats.totalUsers);
            const newUsersText = this.formatNumber(data.totalUsers);
            if (newUsersText !== oldUsersText && oldUsersText !== '0') {
                this.animateNumberChange(usersEl, this.currentStats.totalUsers, data.totalUsers);
            } else {
                usersEl.textContent = newUsersText;
            }
            this.currentStats.totalUsers = data.totalUsers;
        }
    },

    animateNumberChange(element, oldValue, newValue, useFullFormat = false) {
        // Validate input values
        if (!this.isValidNumber(oldValue) || !this.isValidNumber(newValue)) {
            console.warn('Invalid animation values:', { oldValue, newValue });
            const formatFunc = useFullFormat ? this.formatFullNumber : this.formatNumber;
            element.textContent = formatFunc.call(this, newValue || 0);
            return;
        }

        // Check for extreme values that could cause performance issues
        const valueDiff = Math.abs(newValue - oldValue);
        const maxValue = useFullFormat ? 1e12 : 1e9; // Higher limit for full format

        if (valueDiff > maxValue) {
            console.warn('Animation value difference too large, using instant update:', { oldValue, newValue, diff: valueDiff });
            const formatFunc = useFullFormat ? this.formatFullNumber : this.formatNumber;
            element.textContent = formatFunc.call(this, newValue);
            return;
        }

        // Check if we should use fallback animation
        if (AnimationProfile.capabilities.animationMethod === 'fallback' ||
            AnimationProfile.capabilities.prefersReducedMotion ||
            Math.abs(newValue - oldValue) === 0) {
            this.animateNumberChangeFallback(element, oldValue, newValue, useFullFormat);
            return;
        }

        // Generate unique animation ID
        const animationId = `stat-animation-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

        // Cancel any existing animation for this element
        if (element.animationId) {
            AdaptivePerformance.unregisterAnimation(element.animationId);
        }
        element.animationId = animationId;

        // Register with adaptive performance system
        AdaptivePerformance.registerAnimation(animationId, {
            element: element,
            startValue: oldValue,
            endValue: newValue,
            useFullFormat: useFullFormat,
            startTime: performance.now(),
            duration: 5000, // 5 seconds
            pause: () => this.pauseContinuousAnimation(animationId),
            resume: () => this.resumeContinuousAnimation(animationId),
            cleanup: () => this.cleanupContinuousAnimation(animationId),
            adjustQuality: (quality) => this.adjustAnimationQuality(animationId, quality)
        });

        // Start the continuous rolling animation
        this.startContinuousRollingAnimation(animationId, element, oldValue, newValue, useFullFormat);
    },

    isValidNumber(value) {
        return typeof value === 'number' &&
               !isNaN(value) &&
               isFinite(value) &&
               value >= 0; // Stats counters should be non-negative
    },

    startContinuousRollingAnimation(animationId, element, startValue, endValue, useFullFormat) {
        const formatFunc = useFullFormat ? this.formatFullNumber : this.formatNumber;
        const animation = AdaptivePerformance.activeAnimations.get(animationId);

        if (!animation) return;

        const duration = animation.duration;
        const capabilities = AnimationProfile.capabilities;

        // Calculate intermediate values for rolling animation
        const valueDiff = endValue - startValue;
        const absDiff = Math.abs(valueDiff);

        // Generate sequential steps for every number to prevent skipping
        let steps = [];

        if (absDiff === 0) {
            // No change needed
            steps = [startValue];
        } else if (absDiff <= 100) {
            // For small differences, animate every number sequentially
            const increment = valueDiff > 0 ? 1 : -1;
            for (let i = 0; i <= absDiff; i++) {
                steps.push(startValue + (i * increment));
            }
        } else {
            // For larger differences, we need to balance completeness with performance
            const maxSteps = Math.min(absDiff, 100); // Cap at 100 steps for performance
            const stepSize = Math.max(1, Math.floor(absDiff / maxSteps));

            if (stepSize === 1) {
                // If step size is 1, we can animate every number
                const increment = valueDiff > 0 ? 1 : -1;
                for (let i = 0; i <= absDiff; i++) {
                    steps.push(startValue + (i * increment));
                }
            } else {
                // For larger steps, ensure we include key points with easing
                const increment = valueDiff > 0 ? stepSize : -stepSize;
                let current = startValue;

                // Add start value
                steps.push(current);

                // Add intermediate values with easing
                for (let i = 1; i < maxSteps; i++) {
                    const progress = i / maxSteps;
                    const easedProgress = this.easeInOutCubic(progress);
                    const easedValue = startValue + (valueDiff * easedProgress);
                    steps.push(Math.round(easedValue));
                }

                // Ensure we have the final value
                if (steps[steps.length - 1] !== endValue) {
                    steps.push(endValue);
                }
            }
        }

        // Apply performance-based optimization by limiting steps if needed
        if (capabilities.animationQuality === 'basic' && steps.length > 50) {
            // For basic quality, reduce steps while maintaining progression
            const reducedSteps = [steps[0]];
            const stepInterval = Math.ceil(steps.length / 50);
            for (let i = stepInterval; i < steps.length; i += stepInterval) {
                reducedSteps.push(steps[i]);
            }
            if (reducedSteps[reducedSteps.length - 1] !== endValue) {
                reducedSteps.push(endValue);
            }
            steps = reducedSteps;
        } else if (capabilities.animationQuality === 'minimal' && steps.length > 30) {
            // For minimal quality, further reduce steps
            const reducedSteps = [steps[0], steps[Math.floor(steps.length / 2)], endValue];
            steps = reducedSteps;
        }

        // Store animation state
        animation.state = {
            steps: steps,
            currentStepIndex: 0,
            isPaused: false,
            stepStartTime: performance.now(),
            stepDuration: duration / steps.length
        };

        // Start the rolling animation sequence
        this.executeRollingStep(animationId, element, steps, 0, formatFunc);
    },

    executeRollingStep(animationId, element, steps, stepIndex, formatFunc) {
        const animation = AdaptivePerformance.activeAnimations.get(animationId);
        if (!animation || animation.state.isPaused || stepIndex >= steps.length) {
            // Animation complete
            if (animation && stepIndex >= steps.length) {
                element.textContent = formatFunc.call(this, steps[steps.length - 1]);
                AdaptivePerformance.unregisterAnimation(animationId);
                if (element.animationId === animationId) {
                    element.animationId = null;
                }
            }
            return;
        }

        // Update current step index
        animation.state.currentStepIndex = stepIndex;

        const currentValue = steps[stepIndex];
        const previousValue = stepIndex > 0 ? steps[stepIndex - 1] : currentValue;

        // Create rolling animation for this step
        this.createRollingDigitAnimation(element, previousValue, currentValue, formatFunc, () => {
            // Schedule next step only if not paused
            if (!animation.state.isPaused) {
                animation.state.stepTimeoutId = setTimeout(() => {
                    this.executeRollingStep(animationId, element, steps, stepIndex + 1, formatFunc);
                }, animation.state.stepDuration);
            }
        });
    },

    createRollingDigitAnimation(element, oldValue, newValue, formatFunc, onComplete) {
        const oldText = formatFunc.call(this, oldValue);
        const newText = formatFunc.call(this, newValue);
        const direction = newValue > oldValue ? 'up' : 'down';

        // Handle negative numbers by preserving the minus sign
        const oldIsNegative = oldText.startsWith('-');
        const newIsNegative = newText.startsWith('-');

        // Extract the numeric parts for animation
        const oldNumText = oldIsNegative ? oldText.slice(1) : oldText;
        const newNumText = newIsNegative ? newText.slice(1) : newText;

        // Pad shorter numeric string with spaces to align digits
        const maxLen = Math.max(oldNumText.length, newNumText.length);
        const oldPadded = oldNumText.padStart(maxLen, ' ');
        const newPadded = newNumText.padStart(maxLen, ' ');

        // Create container for all digits
        const container = document.createElement('span');
        container.className = 'odometer-container';
        container.style.display = 'inline-flex';
        container.style.alignItems = 'center';
        container.style.verticalAlign = 'baseline';
        container.style.lineHeight = '1.2em';

        // Track all animated elements for cleanup
        const animatedElements = [];

        // Handle minus sign separately if it exists
        if (oldIsNegative || newIsNegative) {
            const signWrapper = document.createElement('span');
            signWrapper.className = 'odometer-sign-wrapper';
            signWrapper.style.display = 'inline-block';
            signWrapper.style.marginRight = '0.1em';
            signWrapper.style.fontWeight = 'inherit';

            // Determine which sign to show and whether to animate it
            if (oldIsNegative === newIsNegative) {
                // Sign doesn't change, just display it
                signWrapper.textContent = '-';
            } else {
                // Sign changes, animate it
                const oldSign = oldIsNegative ? '-' : '+';
                const newSign = newIsNegative ? '-' : '+';

                signWrapper.textContent = newSign;
                // Optionally animate sign change here if needed
            }

            container.appendChild(signWrapper);
        }

        // Process each character position of the numeric part
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
            digitWrapper.style.textAlign = 'center';
            digitWrapper.style.margin = '0';
            digitWrapper.style.padding = '0';
            digitWrapper.style.fontWeight = 'inherit'; // Inherit from parent

            // Use monospace-like width consistency
            const charWidth = this.getCharacterWidth(oldChar, newChar);
            digitWrapper.style.width = charWidth;
            digitWrapper.style.minWidth = charWidth;
            digitWrapper.style.maxWidth = charWidth;

            if (oldChar === newChar) {
                // No change - display static character
                const staticChar = document.createElement('span');
                staticChar.textContent = oldChar;
                staticChar.style.display = 'inline-block';
                staticChar.style.height = '1.2em';
                staticChar.style.lineHeight = '1.2em';
                staticChar.style.verticalAlign = 'baseline';
                staticChar.style.textAlign = 'center';
                staticChar.style.width = '100%';
                staticChar.style.fontWeight = 'inherit'; // Inherit from parent
                digitWrapper.appendChild(staticChar);
            } else {
                // Character changed - animate it with rolling effect
                const roller = document.createElement('span');
                roller.className = 'odometer-roller';
                roller.style.display = 'block';
                roller.style.position = 'relative';

                // Apply hardware acceleration for better performance with Safari fallbacks
                if (AnimationProfile.capabilities.hasCSSTransforms3d) {
                    if (AnimationProfile.capabilities.isSafari && AnimationProfile.capabilities.safariVersion < 14) {
                        // Older Safari needs webkit prefix and different approach
                        roller.style.webkitTransform = 'translateZ(0)';
                        roller.style.transform = 'translateZ(0)';
                        roller.style.webkitBackfaceVisibility = 'hidden';
                        roller.style.backfaceVisibility = 'hidden';
                    } else {
                        roller.style.transform = 'translateZ(0)';
                        roller.style.willChange = 'transform';
                    }
                }

                // Adjust animation duration based on performance
                const animationDuration = AnimationProfile.capabilities.animationQuality === 'enhanced' ? '0.4s' :
                                         AnimationProfile.capabilities.animationQuality === 'standard' ? '0.3s' :
                                         AnimationProfile.capabilities.animationQuality === 'basic' ? '0.2s' : '0.15s';

                roller.style.transition = `transform ${animationDuration} cubic-bezier(0.34, 1.56, 0.64, 1)`;

                // Old character
                const oldCharSpan = document.createElement('span');
                oldCharSpan.textContent = oldChar;
                oldCharSpan.style.display = 'block';
                oldCharSpan.style.height = '1.2em';
                oldCharSpan.style.lineHeight = '1.2em';
                oldCharSpan.style.textAlign = 'center';
                oldCharSpan.style.width = '100%';
                oldCharSpan.style.verticalAlign = 'baseline';
                oldCharSpan.style.fontWeight = 'inherit'; // Inherit from parent

                // New character
                const newCharSpan = document.createElement('span');
                newCharSpan.textContent = newChar;
                newCharSpan.style.display = 'block';
                newCharSpan.style.height = '1.2em';
                newCharSpan.style.lineHeight = '1.2em';
                newCharSpan.style.textAlign = 'center';
                newCharSpan.style.width = '100%';
                newCharSpan.style.verticalAlign = 'baseline';
                newCharSpan.style.fontWeight = 'inherit'; // Inherit from parent

                // Append in correct order based on direction
                // When increasing (up): new digit comes from bottom, old goes up
                // When decreasing (down): new digit comes from top, old goes down
                if (direction === 'up') {
                    roller.appendChild(newCharSpan); // New digit (starts below)
                    roller.appendChild(oldCharSpan); // Old digit (starts above)
                } else {
                    roller.appendChild(oldCharSpan); // Old digit (starts below)
                    roller.appendChild(newCharSpan); // New digit (starts above)
                }

                digitWrapper.appendChild(roller);
                animatedElements.push(roller);

                // Set initial transform before animation with Safari compatibility
                const initialTransform = direction === 'up'
                    ? 'translateY(-1.2em)' // Start with old digit visible
                    : 'translateY(0)';      // Start with old digit visible

                const finalTransform = direction === 'up'
                    ? 'translateY(0)'      // Move new digit up into view
                    : 'translateY(-1.2em)'; // Move new digit down into view

                // Apply transforms with Safari fallbacks
                if (AnimationProfile.capabilities.isSafari && AnimationProfile.capabilities.safariVersion < 14) {
                    roller.style.webkitTransform = initialTransform;
                    roller.style.transform = initialTransform;
                } else {
                    roller.style.transform = initialTransform;
                }

                // Trigger animation on next frame
                requestAnimationFrame(() => {
                    requestAnimationFrame(() => {
                        if (AnimationProfile.capabilities.isSafari && AnimationProfile.capabilities.safariVersion < 14) {
                            roller.style.webkitTransform = finalTransform;
                            roller.style.transform = finalTransform;
                        } else {
                            roller.style.transform = finalTransform;
                        }
                    });
                });
            }

            container.appendChild(digitWrapper);
        }

        // Replace element content
        element.textContent = '';
        element.appendChild(container);

        // Clean up after animation
        const cleanupDelay = AnimationProfile.capabilities.animationQuality === 'enhanced' ? 450 :
                            AnimationProfile.capabilities.animationQuality === 'standard' ? 350 :
                            AnimationProfile.capabilities.animationQuality === 'basic' ? 250 : 200;

        const cleanupId = setTimeout(() => {
            // Clean up event listeners and computed styles with Safari compatibility
            animatedElements.forEach(roller => {
                if (roller) {
                    roller.style.willChange = 'auto';
                    roller.style.transform = '';
                    // Clean up Safari-specific properties
                    if (AnimationProfile.capabilities.isSafari && AnimationProfile.capabilities.safariVersion < 14) {
                        roller.style.webkitTransform = '';
                        roller.style.webkitBackfaceVisibility = '';
                        roller.style.backfaceVisibility = '';
                    }
                }
            });

            // Clear element content and set final text
            element.textContent = newText;

            // Call completion callback
            if (onComplete) onComplete();
        }, cleanupDelay);

        // Store cleanup ID for potential early cleanup
        element._animationCleanupId = cleanupId;
    },

    pauseContinuousAnimation(animationId) {
        const animation = AdaptivePerformance.activeAnimations.get(animationId);
        if (animation && animation.state) {
            animation.state.isPaused = true;
            animation.state.pauseStartTime = performance.now();
            // Clear any pending step timeouts
            if (animation.state.stepTimeoutId) {
                clearTimeout(animation.state.stepTimeoutId);
                animation.state.stepTimeoutId = null;
            }
        }
    },

    resumeContinuousAnimation(animationId) {
        const animation = AdaptivePerformance.activeAnimations.get(animationId);
        if (animation && animation.state) {
            animation.state.isPaused = false;

            // Adjust timing to account for pause duration
            if (animation.state.pauseStartTime) {
                const pauseDuration = performance.now() - animation.state.pauseStartTime;
                animation.state.stepStartTime += pauseDuration;
                animation.state.pauseStartTime = null;
            }

            // Resume from current step
            const formatFunc = animation.useFullFormat ? this.formatFullNumber : this.formatNumber;
            this.executeRollingStep(animationId, animation.element, animation.state.steps, animation.state.currentStepIndex + 1, formatFunc);
        }
    },

    cleanupContinuousAnimation(animationId) {
        const animation = AdaptivePerformance.activeAnimations.get(animationId);
        if (animation) {
            if (animation.frameId) {
                cancelAnimationFrame(animation.frameId);
                animation.frameId = null;
            }
            if (animation.state && animation.state.stepTimeoutId) {
                clearTimeout(animation.state.stepTimeoutId);
                animation.state.stepTimeoutId = null;
            }
            if (animation.element) {
                // Clear any pending animation cleanup
                if (animation.element._animationCleanupId) {
                    clearTimeout(animation.element._animationCleanupId);
                    animation.element._animationCleanupId = null;
                }
                if (animation.element.animationId === animationId) {
                    animation.element.animationId = null;
                }
            }
        }
    },

    adjustAnimationQuality(animationId, quality) {
        const animation = AdaptivePerformance.activeAnimations.get(animationId);
        if (animation && animation.state) {
            // Adjust step size based on quality
            const baseStepSize = Math.abs(animation.endValue - animation.startValue) / 100;

            switch (quality) {
                case 'minimal':
                    animation.state.stepSize = baseStepSize * 5;
                    break;
                case 'basic':
                    animation.state.stepSize = baseStepSize * 2;
                    break;
                case 'standard':
                    animation.state.stepSize = baseStepSize;
                    break;
                case 'enhanced':
                    animation.state.stepSize = Math.max(1, baseStepSize / 2);
                    break;
            }
        }
    },

    // Fallback animation for older browsers or reduced motion
    animateNumberChangeFallback(element, oldValue, newValue, useFullFormat = false) {
        const formatFunc = useFullFormat ? this.formatFullNumber : this.formatNumber;

        // Simple fade transition
        element.style.opacity = '0';
        element.style.transition = 'opacity 0.3s ease-in-out';

        setTimeout(() => {
            element.textContent = formatFunc.call(this, newValue);
            element.style.opacity = '1';
        }, 150);
    },

    // Easing function for smooth animation
    easeInOutCubic(t) {
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    },

    // Helper function to get consistent character width
    getCharacterWidth(oldChar, newChar) {
        // Create a cache key for character pairs
        const cacheKey = `${oldChar}-${newChar}`;

        // Initialize cache if needed
        if (!this._charWidthCache) {
            this._charWidthCache = new Map();
        }

        // Return cached value if available
        if (this._charWidthCache.has(cacheKey)) {
            return this._charWidthCache.get(cacheKey);
        }
        // Use consistent widths for different character types
        if (oldChar === ' ' && newChar === ' ') {
            width = '0'; // No space needed
        } else if (oldChar === ' ' || newChar === ' ') {
            width = '0.6em'; // Space characters
        } else if (/\d/.test(oldChar) || /\d/.test(newChar)) {
            width = '0.6em'; // Digits
        } else if (/[\.,]/.test(oldChar) || /[\.,]/.test(newChar)) {
            width = '0.4em'; // Punctuation
        } else if (/[KM]/.test(oldChar) || /[KM]/.test(newChar)) {
            width = '0.8em'; // Suffixes
        } else {
            width = '0.6em'; // Default
        }

        // Cache the result
        this._charWidthCache.set(cacheKey, width);
        return width;
    },

    showPlaceholder() {
        const requestsEl = document.getElementById('total-requests');
        const usersEl = document.getElementById('total-users');
        const requestsTodayEl = document.getElementById('requests-today');

        // Only show placeholders if elements exist
        if (!requestsEl || !usersEl) return;

        requestsEl.textContent = '---';
        if (requestsTodayEl) requestsTodayEl.textContent = '---';
        usersEl.textContent = '---';
    },

    formatNumber(num) {
        if (num >= 1000000) {
            return (num / 1000000).toFixed(1) + 'M';
        } else if (num >= 1000) {
            return (num / 1000).toFixed(1) + 'K';
        }
        return num.toString();
    },

    formatFullNumber(num) {
        // Format number with commas but no abbreviations
        return num.toLocaleString();
    },

    shouldResetDailyCounter(currentUTCDate) {
        // Get the last stored UTC date
        const lastStoredDate = localStorage.getItem('synaxic_last_utc_date');

        // If we don't have a stored date, this is the first load
        if (!lastStoredDate) {
            return false;
        }

        // Get user's current date in their timezone
        const userToday = new Date().toLocaleDateString('en-CA'); // en-CA format is YYYY-MM-DD

        // Convert UTC dates to user's timezone dates
        const lastUTCDateObj = new Date(lastStoredDate + 'T00:00:00Z');
        const currentUTCDateObj = new Date(currentUTCDate + 'T00:00:00Z');

        const lastUserDate = lastUTCDateObj.toLocaleDateString('en-CA');
        const currentUserDate = currentUTCDateObj.toLocaleDateString('en-CA');

        // If the date has changed for the user, they've passed midnight
        const userMidnightPassed = userToday !== lastUserDate;

        // If the server date is different from the last stored date,
        // it means server has passed midnight UTC
        const serverMidnightPassed = currentUTCDate !== lastStoredDate;

        return userMidnightPassed || serverMidnightPassed;
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

            // Handle 204 No Content (not authenticated)
            if (response.status === 204) {
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
    // Initialize animation systems first
    AnimationProfile.init();
    AdaptivePerformance.init();

    // Initialize other components
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

    // Show hero section once everything is initialized
    setTimeout(() => {
        document.body.classList.add('hero-ready');

        // Handle different page types
        if (document.body.classList.contains('analytics')) {
            document.body.classList.add('analytics-ready');
        } else if (document.body.classList.contains('health')) {
            document.body.classList.add('health-ready');
        }
    }, 50); // Small delay to ensure CSS is applied
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

// Shared Animation Utilities Module
const AnimationUtils = {
    // Cache for character width calculations
    _charWidthCache: new Map(),

    // Apply Safari-compatible transforms
    applyTransform(element, transformValue, cleanup = false) {
        if (!element) return;

        const isSafari = AnimationProfile.capabilities.isSafari;
        const safariVersion = AnimationProfile.capabilities.safariVersion;

        if (isSafari && safariVersion < 14) {
            element.style.webkitTransform = cleanup ? '' : transformValue;
            element.style.transform = cleanup ? '' : transformValue;
        } else {
            element.style.transform = cleanup ? '' : transformValue;
            if (!cleanup) {
                element.style.willChange = 'transform';
            } else {
                element.style.willChange = 'auto';
            }
        }
    },

    // Apply hardware acceleration
    applyHardwareAcceleration(element) {
        if (!element) return;

        if (AnimationProfile.capabilities.hasCSSTransforms3d) {
            const isSafari = AnimationProfile.capabilities.isSafari;
            const safariVersion = AnimationProfile.capabilities.safariVersion;

            if (isSafari && safariVersion < 14) {
                element.style.webkitTransform = 'translateZ(0)';
                element.style.transform = 'translateZ(0)';
                element.style.webkitBackfaceVisibility = 'hidden';
                element.style.backfaceVisibility = 'hidden';
            } else {
                element.style.transform = 'translateZ(0)';
                element.style.willChange = 'transform';
            }
        }
    },

    // Clean up animation properties
    cleanupAnimation(element) {
        if (!element) return;

        const isSafari = AnimationProfile.capabilities.isSafari;
        const safariVersion = AnimationProfile.capabilities.safariVersion;

        element.style.willChange = 'auto';
        element.style.transform = '';

        if (isSafari && safariVersion < 14) {
            element.style.webkitTransform = '';
            element.style.webkitBackfaceVisibility = '';
            element.style.backfaceVisibility = '';
        }
    },

    // Get cached character width with fallback
    getCharacterWidth(oldChar, newChar) {
        const cacheKey = `${oldChar}-${newChar}`;

        // Return cached value if available
        if (this._charWidthCache.has(cacheKey)) {
            return this._charWidthCache.get(cacheKey);
        }

        // Calculate width
        let width;
        if (oldChar === ' ' && newChar === ' ') {
            width = '0';
        } else if (oldChar === ' ' || newChar === ' ') {
            width = '0.6em';
        } else if (/\d/.test(oldChar) || /\d/.test(newChar)) {
            width = '0.6em';
        } else if (/[\.,]/.test(oldChar) || /[\.,]/.test(newChar)) {
            width = '0.4em';
        } else if (/[KM]/.test(oldChar) || /[KM]/.test(newChar)) {
            width = '0.8em';
        } else {
            width = '0.6em';
        }

        // Cache the result
        this._charWidthCache.set(cacheKey, width);
        return width;
    },

    // Create animation timeout with automatic cleanup
    createAnimationTimeout(callback, delay, cleanupCallback = null) {
        const timeoutId = setTimeout(() => {
            if (callback) callback();
            if (cleanupCallback) cleanupCallback();
        }, delay);

        return timeoutId;
    },

    // Safe requestAnimationFrame with cleanup
    requestAnimationFrame(callback) {
        if (AnimationProfile.capabilities.hasRequestAnimationFrame) {
            return requestAnimationFrame(callback);
        } else {
            // Fallback for older browsers
            return setTimeout(callback, 16); // ~60fps
        }
    },

    // Cancel animation frame or timeout
    cancelAnimation(id) {
        if (typeof id === 'number') {
            cancelAnimationFrame(id);
        } else {
            clearTimeout(id);
        }
    }
};
