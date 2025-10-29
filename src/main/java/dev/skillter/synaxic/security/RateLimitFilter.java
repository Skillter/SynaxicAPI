package dev.skillter.synaxic.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.skillter.synaxic.model.entity.User;
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
    private final IpExtractor ipExtractor;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String key;
        RateLimitService.RateLimitTier tier;

        // Determine rate limit tier
        String path = request.getRequestURI();
        if (isStaticResource(path)) {
            tier = RateLimitService.RateLimitTier.STATIC;
            key = ipExtractor.extractClientIp(request);
        } else {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof User user) {
                key = user.getId().toString();
                tier = RateLimitService.RateLimitTier.API_KEY;
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
            filterChain.doFilter(request, response);
        } else {
            handleRateLimitExceeded(request, response, probe);
        }
    }

    private boolean isStaticResource(String path) {
        return path.endsWith(".html") || path.endsWith(".css") || path.endsWith(".js") ||
               path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
               path.endsWith(".gif") || path.endsWith(".svg") || path.endsWith(".ico") ||
               path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") ||
               path.endsWith(".eot") || path.startsWith("/css/") || path.startsWith("/js/") ||
               path.startsWith("/images/") || path.startsWith("/assets/") || path.startsWith("/static/") ||
               path.equals("/") || path.equals("/index.html");
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
        return path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/actuator") ||
               path.startsWith("/error");
    }
}