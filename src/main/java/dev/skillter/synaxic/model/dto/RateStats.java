package dev.skillter.synaxic.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Request and error rate statistics")
public class RateStats {

    @Schema(description = "Requests per minute (average over last 5 minutes)", example = "125.5")
    private double requestsPerMinute;

    @Schema(description = "Error rate as a percentage", example = "2.5")
    private double errorRatePercent;

    @Schema(description = "Success rate as a percentage", example = "97.5")
    private double successRatePercent;
}
