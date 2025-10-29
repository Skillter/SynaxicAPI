package dev.skillter.synaxic.controller;

import dev.skillter.synaxic.model.dto.RateLimitStatus;
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
            description = "Returns the current rate limit status for your IP address (ANONYMOUS tier)")
    @ApiResponse(responseCode = "200", description = "Current rate limit status",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = RateLimitStatus.class)))
    public RateLimitStatus checkRateLimit(HttpServletRequest request) {
        String clientIp = ipExtractor.extractClientIp(request);
        return rateLimitService.getStatus(clientIp, RateLimitService.RateLimitTier.ANONYMOUS);
    }
}
