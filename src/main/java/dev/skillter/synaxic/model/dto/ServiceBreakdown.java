package dev.skillter.synaxic.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Breakdown of requests by service type")
public class ServiceBreakdown {

    @Schema(description = "Number of IP Inspector API requests", example = "5420")
    private long ipInspectorRequests;

    @Schema(description = "Number of Email Validator API requests", example = "3200")
    private long emailValidatorRequests;

    @Schema(description = "Number of Unit Converter API requests", example = "1850")
    private long unitConverterRequests;

    @Schema(description = "Number of other/misc requests", example = "320")
    private long otherRequests;
}
