package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.ColorConversionResponse;
import dev.skillter.synaxic.model.dto.ContrastRatioResponse;
import dev.skillter.synaxic.service.ConversionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/color")
@RequiredArgsConstructor
@Validated
@Tag(name = "Color Converters", description = "Endpoints for color format conversions and contrast ratio calculation.")
public class ColorConverterController {

    private final ConversionService conversionService;

    @GetMapping(value = "/convert", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Convert Between Color Formats",
            description = "Converts a color value between HEX, RGB, and HSL formats. The response will always include all three formats.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful conversion.",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ColorConversionResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid color format or value.",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
            })
    public ColorConversionResponse convertColor(
            @Parameter(description = "The format of the input color value. Use 'auto' to let the API detect the format.", required = true, example = "hex", schema = @Schema(allowableValues = {"hex", "rgb", "hsl", "auto"}))
            @RequestParam @NotBlank String from,
            @Parameter(description = "The target format to convert to (currently ignored, as the API returns all formats).", example = "hsl")
            @RequestParam(required = false) String to,
            @Parameter(description = "The color value to convert. **Remember to URL-encode HEX values** (e.g., `%23ffcc00`).", required = true, example = "#ffcc00")
            @RequestParam @NotBlank String value) {
        return conversionService.convertColor(from, to, value);
    }

    @GetMapping(value = "/contrast", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Calculate WCAG Contrast Ratio",
            description = "Calculates the contrast ratio between two colors and indicates whether they meet WCAG 2.1 AA (>= 4.5) and AAA (>= 7.0) accessibility standards.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful calculation.",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ContrastRatioResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid color format or value provided for foreground or background.",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
            })
    public ContrastRatioResponse getContrastRatio(
            @Parameter(description = "The foreground color. Can be HEX (URL-encoded) or RGB. E.g., `%23000000` or `rgb(0,0,0)`.", required = true, example = "#000000")
            @RequestParam @NotBlank String fg,
            @Parameter(description = "The background color. Can be HEX (URL-encoded) or RGB. E.g., `%23ffffff` or `rgb(255,255,255)`.", required = true, example = "#ffffff")
            @RequestParam @NotBlank String bg) {
        return conversionService.calculateContrastRatio(fg, bg);
    }
}