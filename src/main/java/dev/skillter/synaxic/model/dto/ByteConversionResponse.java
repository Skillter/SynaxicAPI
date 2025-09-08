package dev.skillter.synaxic.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Result of a byte size conversion")
public record ByteConversionResponse(
        @Schema(description = "The byte unit to convert from (IEC or SI)", example = "MiB")
        String from,
        @Schema(description = "The byte unit to convert to (IEC or SI)", example = "MB")
        String to,
        @Schema(description = "The original value", example = "128")
        BigDecimal value,
        @Schema(description = "The converted value", example = "134.217728")
        BigDecimal result,
        @Schema(description = "The conversion ratio", example = "1.048576")
        BigDecimal ratio
) {}