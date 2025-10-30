package dev.skillter.synaxic.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "API key usage statistics")
public class ApiKeyStats {

    @Schema(description = "Total number of registered API keys", example = "150")
    private long totalKeys;

    @Schema(description = "Number of API keys used in the last 24 hours", example = "45")
    private long activeKeysLast24h;

    @Schema(description = "Number of anonymous (no API key) requests", example = "3200")
    private long anonymousRequests;
}
