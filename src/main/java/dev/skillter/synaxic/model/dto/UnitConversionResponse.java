package dev.skillter.synaxic.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The result of a physical unit conversion.")
public record UnitConversionResponse(
        @Schema(description = "The unit converted from.", example = "mi")
        String from,
        @Schema(description = "The unit converted to.", example = "km")
        String to,
        @Schema(description = "The original value provided for conversion.", example = "3.1")
        double value,
        @Schema(description = "The calculated result of the conversion.", example = "4.9889664")
        double result
) {}