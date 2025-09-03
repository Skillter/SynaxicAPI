package dev.skillter.synaxic.security;

import com.bucket4j.Bucket;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import com.bucket4j.ConsumptionProbe;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.service.RateLimitService;
import dev.skillter.synaxic.util.IpExtractor;
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
        boolean isApiKeyUser = false;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof User) {
            User user = (User) authentication.getPrincipal();
            key = user.getId().toString();
            isApiKeyUser = true;
        } else {
            key = ipExtractor.extractClientIp(request);
        }

        Bucket bucket = rateLimitService.resolveBucket(key, isApiKeyUser);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
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
            log.warn("Rate limit exceeded for key: {}", key);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }
}