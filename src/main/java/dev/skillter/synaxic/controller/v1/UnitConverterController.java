package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.ByteConversionResponse;
import dev.skillter.synaxic.model.dto.UnitConversionResponse;
import dev.skillter.synaxic.service.ConversionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/v1/convert")
@RequiredArgsConstructor
@Validated
@Tag(name = "Unit & Byte Converters", description = "Endpoints for converting physical units and byte sizes")
public class UnitConverterController {

    private final ConversionService conversionService;

    @GetMapping(value = "/units", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Convert physical units",
            description = "Performs conversions for various physical units like length, mass, and temperature. Uses JSR-385 standard unit symbols.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful conversion"),
                    @ApiResponse(responseCode = "400", description = "Invalid or incompatible units", content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
            })
    public UnitConversionResponse convertUnits(
            @Parameter(description = "The unit symbol to convert from (e.g., 'mi', 'kg', 'C')", required = true, example = "mi")
            @RequestParam @NotBlank String from,
            @Parameter(description = "The unit symbol to convert to (e.g., 'km', 'lb', 'F')", required = true, example = "km")
            @RequestParam @NotBlank String to,
            @Parameter(description = "The numeric value to convert", required = true, example = "3.1")
            @RequestParam double value) {
        return conversionService.convertUnits(from, to, value);
    }

    @GetMapping(value = "/bytes", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Convert byte sizes",
            description = "Performs conversions between SI (kB, MB) and IEC (KiB, MiB) byte units.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful conversion"),
                    @ApiResponse(responseCode = "400", description = "Invalid byte unit symbol", content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
            })
    public ByteConversionResponse convertBytes(
            @Parameter(description = "The byte unit to convert from (e.g., 'MiB', 'GB')", required = true, example = "MiB")
            @RequestParam @NotBlank String from,
            @Parameter(description = "The byte unit to convert to (e.g., 'MB', 'GiB')", required = true, example = "MB")
            @RequestParam @NotBlank String to,
            @Parameter(description = "The numeric value to convert", required = true, example = "128")
            @RequestParam @Positive BigDecimal value) {
        return conversionService.convertBytes(from, to, value);
    }
}