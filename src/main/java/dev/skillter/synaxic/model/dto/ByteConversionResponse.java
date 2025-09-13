package dev.skillter.synaxic.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "The result of a byte size conversion.")
public record ByteConversionResponse(
        @Schema(description = "The byte unit converted from (IEC or SI).", example = "MiB")
        String from,
        @Schema(description = "The byte unit converted to (IEC or SI).", example = "MB")
        String to,
        @Schema(description = "The original value provided for conversion.", example = "128")
        BigDecimal value,
        @Schema(description = "The calculated result of the conversion.", example = "134.217728")
        BigDecimal result,
        @Schema(description = "The conversion ratio used (from / to).", example = "1.048576")
        BigDecimal ratio
) {}