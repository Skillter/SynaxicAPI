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
    private final DailyRequestTrackerService dailyRequestTrackerService;
    private final IpExtractor ipExtractor;
    private final ObjectMapper objectMapper;

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

        if (isStaticResource(path)) {
            tier = RateLimitService.RateLimitTier.STATIC;
            key = ipExtractor.extractClientIp(request);
        } else {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()) {
                if (authentication instanceof ApiKeyAuthentication apiKeyAuth) {
                    User user = apiKeyAuth.getApiKey().getUser();
                    key = "account:" + user.getId();
                    tier = RateLimitService.RateLimitTier.ACCOUNT;
                    apiKeyPrefix = apiKeyAuth.getApiKey().getPrefix();
                    apiKeyId = apiKeyAuth.getApiKey().getId();
                } else if (authentication.getPrincipal() instanceof User user) {
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

            if (!isStaticResource(path)) {
                try {
                    dailyRequestTrackerService.incrementDailyRequests();
                } catch (Exception e) {
                    // Ignore errors
                }

                if (apiKeyId != null && apiKeyPrefix != null) {
                    accountUsageService.recordApiKeyUsage(apiKeyId, apiKeyPrefix);
                }
            }

            filterChain.doFilter(request, response);
        } else {
            handleRateLimitExceeded(request, response, probe);
        }
    }

    private boolean shouldSkipRateLimit(String path) {
        return path.equals("/") ||
               path.equals("/health") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/error");
    }

    private boolean isStaticResource(String path) {
        if (path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/assets/")) return true;
        
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex == -1) return false;
        
        String ext = path.substring(dotIndex);
        return ext.equals(".css") || ext.equals(".js") || ext.equals(".png") || 
               ext.equals(".jpg") || ext.equals(".ico") || ext.equals(".svg") || 
               ext.equals(".woff2") || ext.equals(".html");
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
    }
}

