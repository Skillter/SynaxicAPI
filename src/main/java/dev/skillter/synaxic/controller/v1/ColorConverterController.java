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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/color")
@RequiredArgsConstructor
@Validated
@Tag(name = "Color Converters", description = "Endpoints for color format conversions and contrast ratio calculation")
public class ColorConverterController {

    private final ConversionService conversionService;

    @GetMapping(value = "/convert", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Convert color formats",
            description = "Converts a color value between HEX, RGB, and HSL formats.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful conversion"),
                    @ApiResponse(responseCode = "400", description = "Invalid color format or value", content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
            })
    public ColorConversionResponse convertColor(
            @Parameter(description = "The format of the input color value", required = true, example = "hex", schema = @Schema(allowableValues = {"hex", "rgb", "hsl"}))
            @RequestParam @NotBlank String from,
            @Parameter(description = "The format to convert the color to (currently ignored, returns all formats)", required = true, example = "hsl", schema = @Schema(allowableValues = {"hex", "rgb", "hsl"}))
            @RequestParam @NotBlank String to,
            @Parameter(description = "The color value to convert. URL-encode HEX values (e.g., '%23ffcc00').", required = true, example = "#ffcc00")
            @RequestParam @NotBlank String value) {
        return conversionService.convertColor(from, to, value);
    }

    @GetMapping(value = "/contrast", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Calculate WCAG contrast ratio",
            description = "Calculates the contrast ratio between two colors and checks for WCAG AA/AAA compliance.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful calculation"),
                    @ApiResponse(responseCode = "400", description = "Invalid color format or value", content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
            })
    public ContrastRatioResponse getContrastRatio(
            @Parameter(description = "The foreground color. Can be HEX (URL-encoded) or RGB. E.g., '%23000000' or 'rgb(0,0,0)'.", required = true, example = "#000000")
            @RequestParam @NotBlank String fg,
            @Parameter(description = "The background color. Can be HEX (URL-encoded) or RGB. E.g., '%23ffffff' or 'rgb(255,255,255)'.", required = true, example = "#ffffff")
            @RequestParam @NotBlank String bg) {
        return conversionService.calculateContrastRatio(fg, bg);
    }
}