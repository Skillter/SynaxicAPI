package dev.skillter.synaxic.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "API response latency percentiles, measured in milliseconds.")
public class LatencyStats {
    @Schema(description = "50th percentile (median) response time in milliseconds.", example = "50.5")
    private double p50_ms;

    @Schema(description = "95th percentile response time in milliseconds.", example = "250.0")
    private double p95_ms;

    @Schema(description = "99th percentile response time in milliseconds.", example = "800.2")
    private double p99_ms;
}