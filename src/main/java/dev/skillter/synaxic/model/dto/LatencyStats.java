package dev.skillter.synaxic.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "API response latency percentiles in milliseconds")
public class LatencyStats {
    @Schema(description = "50th percentile (median) response time in milliseconds", example = "50")
    private double p50_ms;

    @Schema(description = "95th percentile response time in milliseconds", example = "250")
    private double p95_ms;

    @Schema(description = "99th percentile response time in milliseconds", example = "800")
    private double p99_ms;
}