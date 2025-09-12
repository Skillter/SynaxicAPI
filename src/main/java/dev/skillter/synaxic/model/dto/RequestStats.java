package dev.skillter.synaxic.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Overall request statistics")
public class RequestStats {

    @Schema(description = "Total number of requests processed by this instance since startup", example = "10520")
    private long total;

    @Schema(description = "Number of successful (2xx) requests", example = "10400")
    private long successCount;

    @Schema(description = "Number of client error (4xx) requests", example = "100")
    private long clientErrorCount;

    @Schema(description = "Number of server error (5xx) requests", example = "20")
    private long serverErrorCount;
}