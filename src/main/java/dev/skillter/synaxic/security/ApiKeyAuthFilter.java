package dev.skillter.synaxic.security;

import dev.skillter.synaxic.model.entity.ApiKey;
import dev.skillter.synaxic.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;
    public static final String API_KEY_PREFIX_ATTRIBUTE = "apiKeyPrefix";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String apiKey = extractApiKey(request);

        if (apiKey != null) {
            Optional<ApiKey> apiKeyOptional = apiKeyService.validateApiKey(apiKey);
            if (apiKeyOptional.isPresent()) {
                ApiKey validApiKey = apiKeyOptional.get();
                ApiKeyAuthentication auth = new ApiKeyAuthentication(validApiKey);
                SecurityContextHolder.getContext().setAuthentication(auth);
                request.setAttribute(API_KEY_PREFIX_ATTRIBUTE, validApiKey.getPrefix());
                log.debug("API key authenticated for user {}", validApiKey.getUser().getId());
            } else {
                log.warn("Invalid API Key provided");
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractApiKey(HttpServletRequest request) {
        String headerValue = request.getHeader("Authorization");
        if (StringUtils.hasText(headerValue)) {
            if (headerValue.startsWith("ApiKey ")) {
                return headerValue.substring(7);
            }
            // Also accept bare API key format (e.g., "syn_live_...")
            if (headerValue.startsWith("syn_live_") || headerValue.startsWith("syn_test_")) {
                return headerValue;
            }
        }

        return request.getHeader("X-API-Key");
    }
}