package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.AnalyticsResponse;
import dev.skillter.synaxic.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin & Analytics", description = "Endpoints for administrative tasks and real-time analytics.")
@SecurityRequirement(name = "ApiKeyAuth")
public class AdminController {

    private final AnalyticsService analyticsService;

    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Real-Time Instance Analytics",
            description = "Returns a snapshot of key metrics for the current application instance, such as uptime, request counts, and latency percentiles. Requires authentication via API key.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved analytics snapshot.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = AnalyticsResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized - API key is missing or invalid.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AnalyticsResponse> getAnalytics() {
        return ResponseEntity.ok(analyticsService.getAnalytics());
    }
}