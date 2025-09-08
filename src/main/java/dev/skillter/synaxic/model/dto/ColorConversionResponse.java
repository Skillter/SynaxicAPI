package dev.skillter.synaxic.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Result of a color format conversion")
public record ColorConversionResponse(
        @Schema(description = "The HEX representation of the color", example = "#ffcc00")
        String hex,
        @Schema(description = "The RGB representation of the color", example = "rgb(255, 204, 0)")
        String rgb,
        @Schema(description = "The HSL representation of the color", example = "hsl(48, 100%, 50%)")
        String hsl
) {}