package dev.skillter.synaxic.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Cache performance statistics")
public class CacheStats {

    @Schema(description = "Cache hit rate as a percentage", example = "85.5")
    private double hitRatePercent;

    @Schema(description = "Total cache hits", example = "1250")
    private long hits;

    @Schema(description = "Total cache misses", example = "200")
    private long misses;

    @Schema(description = "Total cache requests", example = "1450")
    private long totalRequests;
}
