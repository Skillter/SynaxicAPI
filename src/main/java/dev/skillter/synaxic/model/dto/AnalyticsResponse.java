package dev.skillter.synaxic.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Schema(description = "A snapshot of real-time API analytics for the current service instance.")
public class AnalyticsResponse {

    @Schema(description = "The total uptime of the current application instance.", example = "1d 4h 32m 15s")
    private String uptime;

    @Schema(description = "Statistics about processed requests.")
    private RequestStats requests;

    @Schema(description = "Statistics about API response latency.")
    private LatencyStats latency;

    @Schema(description = "Breakdowns of API usage by different dimensions.")
    private Map<String, List<BreakdownItem>> breakdowns;

    @Schema(description = "Request and error rate statistics.")
    private RateStats rates;

    @Schema(description = "Cache performance statistics.")
    private CacheStats cache;

    @Schema(description = "Breakdown of requests by service type.")
    private ServiceBreakdown serviceBreakdown;

    @Schema(description = "Detailed response time statistics.")
    private ResponseTimeStats responseTime;

    @Schema(description = "API key usage statistics.")
    private ApiKeyStats apiKeys;
}