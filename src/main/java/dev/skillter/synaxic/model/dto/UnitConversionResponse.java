package dev.skillter.synaxic.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a unit conversion")
public record UnitConversionResponse(
        @Schema(description = "The unit to convert from", example = "mi")
        String from,
        @Schema(description = "The unit to convert to", example = "km")
        String to,
        @Schema(description = "The original value", example = "3.1")
        double value,
        @Schema(description = "The converted value", example = "4.9889664")
        double result
) {}