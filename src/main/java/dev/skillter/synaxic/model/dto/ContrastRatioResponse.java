package dev.skillter.synaxic.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The result of a WCAG contrast ratio analysis between two colors.")
public record ContrastRatioResponse(
        @Schema(description = "The calculated contrast ratio.", example = "21.0")
        double ratio,
        @Schema(description = "Indicates if the contrast ratio meets the WCAG AA standard (>= 4.5).", example = "true")
        boolean aa,
        @Schema(description = "Indicates if the contrast ratio meets the WCAG AAA standard (>= 7.0).", example = "true")
        boolean aaa
) {}