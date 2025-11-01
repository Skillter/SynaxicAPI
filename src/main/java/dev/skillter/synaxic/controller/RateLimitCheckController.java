package dev.skillter.synaxic.controller;

import dev.skillter.synaxic.model.dto.RateLimitStatus;
import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.security.ApiKeyAuthentication;
import dev.skillter.synaxic.service.RateLimitService;
import dev.skillter.synaxic.util.IpExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Tag(name = "Debug", description = "Debug and monitoring endpoints")
public class RateLimitCheckController {

    private final RateLimitService rateLimitService;
    private final IpExtractor ipExtractor;

    @GetMapping("/rate-limit")
    @Operation(summary = "Check Current Rate Limit Status",
            description = "Returns the current rate limit status for your IP address or account (depending on authentication)")
    @ApiResponse(responseCode = "200", description = "Current rate limit status",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = RateLimitStatus.class)))
    public RateLimitStatus checkRateLimit(HttpServletRequest request) {
        String clientIp = ipExtractor.extractClientIp(request);
        String key;
        RateLimitService.RateLimitTier tier;

        // Check for API key authentication first
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            if (authentication instanceof ApiKeyAuthentication apiKeyAuth) {
                // API key authentication - use account-level rate limiting
                User user = apiKeyAuth.getApiKey().getUser();
                key = "account:" + user.getId();
                tier = RateLimitService.RateLimitTier.ACCOUNT;
            } else if (authentication.getPrincipal() instanceof User user) {
                // OAuth2 authentication - use account-level rate limiting
                key = "account:" + user.getId();
                tier = RateLimitService.RateLimitTier.ACCOUNT;
            } else {
                key = clientIp;
                tier = RateLimitService.RateLimitTier.ANONYMOUS;
            }
        } else {
            key = clientIp;
            tier = RateLimitService.RateLimitTier.ANONYMOUS;
        }

        return rateLimitService.getStatus(key, tier);
    }
}
