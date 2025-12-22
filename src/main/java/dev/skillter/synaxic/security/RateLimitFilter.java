package dev.skillter.synaxic.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.service.AccountUsageService;
import dev.skillter.synaxic.service.DailyRequestTrackerService;
import dev.skillter.synaxic.service.RateLimitService;
import dev.skillter.synaxic.util.IpExtractor;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final AccountUsageService accountUsageService;
    private final DailyRequestTrackerService dailyRequestTrackerService;
    private final IpExtractor ipExtractor;
    private final ObjectMapper objectMapper;

    private static final Set<String> STATIC_EXTENSIONS = Set.of(
            ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".webp",
            ".woff", ".woff2", ".ttf", ".eot", ".html", ".map", ".json", ".xml", ".txt",
            ".pdf", ".csv"
    );

    private static final Set<String> UI_PAGES = Set.of(
            "/",
            "/dashboard",
            "/analytics",
            "/privacy-policy",
            "/terms-of-service",
            "/fair-use-policy",
            "/v1/auth/login-success",
            "/login",
            "/health",
            "/favicon.ico"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (shouldSkipRateLimit(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key;
        RateLimitService.RateLimitTier tier;
        String apiKeyPrefix = null;
        Long apiKeyId = null;
        boolean isApiKeyAuth = false;

        // 1. Static Resources & UI Pages (DDoS Protection Tier - 5M/hr)
        if (isStaticResource(path) || isUiPage(path)) {
            tier = RateLimitService.RateLimitTier.STATIC;
            key = "static:" + ipExtractor.extractClientIp(request);
        } else {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()) {
                if (authentication instanceof ApiKeyAuthentication apiKeyAuth) {
                    // 2. API Key Authentication (Programmatic Access - 10k/hr)
                    // Uses 'account:{id}' bucket - counts towards dashboard quota
                    User user = apiKeyAuth.getApiKey().getUser();
                    key = "account:" + user.getId();
                    tier = RateLimitService.RateLimitTier.ACCOUNT;
                    apiKeyPrefix = apiKeyAuth.getApiKey().getPrefix();
                    apiKeyId = apiKeyAuth.getApiKey().getId();
                    isApiKeyAuth = true;
                } else if (authentication instanceof OAuth2AuthenticationToken) {
                    // 3. OAuth2 Session Authentication (Frontend Website Access - 50k/hr)
                    // Uses 'frontend:{id}' bucket - separate high quota, DOES NOT affect dashboard quota
                    Long userId = getUserIdFromSession(request);
                    if (userId != null) {
                        key = "frontend:" + userId;
                        tier = RateLimitService.RateLimitTier.FRONTEND;
                    } else {
                        // Fallback if session is missing user_id
                        key = ipExtractor.extractClientIp(request);
                        tier = RateLimitService.RateLimitTier.ANONYMOUS;
                    }
                } else {
                    // Fallback for other auth types
                    key = ipExtractor.extractClientIp(request);
                    tier = RateLimitService.RateLimitTier.ANONYMOUS;
                }
            } else {
                // 4. Anonymous (IP Based - 1k/hr)
                key = ipExtractor.extractClientIp(request);
                tier = RateLimitService.RateLimitTier.ANONYMOUS;
            }
        }

        Bucket bucket = rateLimitService.resolveBucket(key, tier);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        long limit = rateLimitService.getLimit(tier);
        response.addHeader("X-RateLimit-Limit", String.valueOf(limit));

        if (probe.isConsumed()) {
            response.addHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));

            // Only count non-static traffic towards global stats
            if (tier != RateLimitService.RateLimitTier.STATIC) {
                // Increment global daily counter (counts everything except static assets)
                try {
                    dailyRequestTrackerService.incrementDailyRequests();
                } catch (Exception e) {
                    // Ignore errors
                }

                // Record detailed usage ONLY for actual API Keys
                // This ensures frontend browsing does NOT increase the "Hourly Quota" bar on the dashboard
                if (isApiKeyAuth && apiKeyId != null && apiKeyPrefix != null) {
                    accountUsageService.recordApiKeyUsage(apiKeyId, apiKeyPrefix);
                }
            }

            filterChain.doFilter(request, response);
        } else {
            handleRateLimitExceeded(request, response, probe);
        }
    }

    private Long getUserIdFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object userIdObj = session.getAttribute("user_id");
            if (userIdObj instanceof Long) {
                return (Long) userIdObj;
            }
        }
        return null;
    }

    private boolean shouldSkipRateLimit(String path) {
        return path.startsWith("/actuator/") || 
               path.startsWith("/error") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs");
    }

    private boolean isUiPage(String path) {
        if (UI_PAGES.contains(path)) return true;
        return false; 
    }

    private boolean isStaticResource(String path) {
        if (path.startsWith("/css/") || 
            path.startsWith("/js/") || 
            path.startsWith("/assets/") || 
            path.startsWith("/images/") ||
            path.startsWith("/webjars/")) {
            return true;
        }
        
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex != -1) {
            String ext = path.substring(dotIndex).toLowerCase();
            return STATIC_EXTENSIONS.contains(ext);
        }
        return false;
    }

    private void handleRateLimitExceeded(HttpServletRequest request, HttpServletResponse response, ConsumptionProbe probe) throws IOException {
        long waitForRefillNanos = probe.getNanosToWaitForRefill();
        long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(waitForRefillNanos) + 1;

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.addHeader("X-RateLimit-Remaining", "0");
        response.addHeader("Retry-After", String.valueOf(retryAfterSeconds));

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "You have exhausted your request quota. Please try again later."
        );
        problemDetail.setTitle("Too Many Requests");
        problemDetail.setType(URI.create("https://synaxic.skillter.dev/errors/too-many-requests"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("path", request.getRequestURI());
        problemDetail.setProperty("retryAfterSeconds", retryAfterSeconds);

        response.getWriter().write(objectMapper.writeValueAsString(problemDetail));
    }
}

