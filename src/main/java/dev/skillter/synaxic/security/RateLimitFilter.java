package dev.skillter.synaxic.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.service.AccountUsageService;
import dev.skillter.synaxic.service.RateLimitService;
import dev.skillter.synaxic.util.IpExtractor;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final AccountUsageService accountUsageService;
    private final IpExtractor ipExtractor;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip rate limiting for pages and documentation
        // Only rate limit actual API endpoints
        if (shouldSkipRateLimit(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key;
        RateLimitService.RateLimitTier tier;
        String apiKeyPrefix = null;
        Long apiKeyId = null;

        // Determine rate limit tier for API endpoints
        if (isStaticResource(path)) {
            tier = RateLimitService.RateLimitTier.STATIC;
            key = ipExtractor.extractClientIp(request);
        } else {
            // Only API endpoints reach here (/v1/*, /api/*)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()) {
                if (authentication instanceof ApiKeyAuthentication apiKeyAuth) {
                    // API key authentication - use account-level rate limiting
                    User user = apiKeyAuth.getApiKey().getUser();
                    key = "account:" + user.getId();
                    tier = RateLimitService.RateLimitTier.ACCOUNT;
                    apiKeyPrefix = apiKeyAuth.getApiKey().getPrefix();
                    apiKeyId = apiKeyAuth.getApiKey().getId();
                } else if (authentication.getPrincipal() instanceof User user) {
                    // OAuth2 authentication - use account-level rate limiting
                    key = "account:" + user.getId();
                    tier = RateLimitService.RateLimitTier.ACCOUNT;
                } else {
                    key = ipExtractor.extractClientIp(request);
                    tier = RateLimitService.RateLimitTier.ANONYMOUS;
                }
            } else {
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

            // Record API key usage for account-level tracking
            if (apiKeyId != null && apiKeyPrefix != null) {
                try {
                    accountUsageService.recordApiKeyUsage(apiKeyId, apiKeyPrefix);
                } catch (Exception e) {
                    log.warn("Failed to record API key usage for key {}: {}", apiKeyPrefix, e.getMessage());
                }
            }

            filterChain.doFilter(request, response);
        } else {
            handleRateLimitExceeded(request, response, probe);
        }
    }

    private boolean shouldSkipRateLimit(String path) {
        // Skip rate limiting for pages, documentation, and non-API endpoints
        return path.equals("/") ||
               path.equals("/index.html") ||
               path.equals("/analytics") ||
               path.equals("/analytics.html") ||
               path.equals("/dashboard") ||
               path.equals("/dashboard.html") ||
               path.equals("/health") ||
               path.equals("/health.html") ||
               path.equals("/login-success.html") ||
               path.equals("/login-success") ||
               path.equals("/privacy-policy") ||
               path.equals("/privacy-policy.html") ||
               path.equals("/terms-of-service") ||
               path.equals("/terms-of-service.html") ||
               path.equals("/fair-use-policy") ||
               path.equals("/fair-use-policy.html") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/actuator") ||
               path.startsWith("/oauth2") ||
               path.startsWith("/api/debug") ||
               path.equals("/login") ||
               path.equals("/error");
    }

    private boolean isStaticResource(String path) {
        // Only truly static files: actual asset files with extensions
        // Do NOT include dynamic pages like /, /dashboard, /health, /swagger-ui, /actuator, /v3/api-docs
        // Those should use ANONYMOUS or API_KEY tiers instead
        return path.startsWith("/css/") || path.startsWith("/js/") ||
               path.startsWith("/images/") || path.startsWith("/assets/") ||
               path.endsWith(".css") || path.endsWith(".js") ||
               path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
               path.endsWith(".gif") || path.endsWith(".svg") || path.endsWith(".ico") ||
               path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") ||
               path.endsWith(".eot");
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
                "You have exhausted your API request quota. Please try again later."
        );
        problemDetail.setTitle("Too Many Requests");
        problemDetail.setType(URI.create("https://synaxic.skillter.dev/errors/too-many-requests"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("path", request.getRequestURI());
        problemDetail.setProperty("retryAfterSeconds", retryAfterSeconds);

        response.getWriter().write(objectMapper.writeValueAsString(problemDetail));
        log.warn("Rate limit exceeded for key associated with path: {}", request.getRequestURI());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/error");
    }
}