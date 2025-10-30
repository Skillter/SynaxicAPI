package dev.skillter.synaxic.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Detailed response time statistics")
public class ResponseTimeStats {

    @Schema(description = "Minimum response time in milliseconds", example = "5.2")
    private double minMs;

    @Schema(description = "Average response time in milliseconds", example = "45.8")
    private double avgMs;

    @Schema(description = "Maximum response time in milliseconds", example = "1250.5")
    private double maxMs;

    @Schema(description = "Total number of timed requests", example = "10520")
    private long count;
}
