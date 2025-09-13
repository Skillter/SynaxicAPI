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
import org.springframework.http.ProblemDetail;
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
@Tag(name = "Unit & Byte Converters", description = "Endpoints for converting physical units and byte sizes.")
public class UnitConverterController {

    private final ConversionService conversionService;

    @GetMapping(value = "/units", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Convert Physical Units",
            description = """
                    Performs conversions for various physical units like length, mass, and temperature.
                    Uses JSR-385 standard unit symbols.
                                        
                    **Common Symbols:**
                    - **Length:** `mi` (mile), `km` (kilometer), `m` (meter)
                    - **Mass:** `lb` (pound), `kg` (kilogram)
                    - **Temperature:** `F` (Fahrenheit), `C` (Celsius), `K` (Kelvin)
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful conversion.",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = UnitConversionResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid or incompatible units.",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
            })
    public UnitConversionResponse convertUnits(
            @Parameter(description = "The unit symbol to convert from.", required = true, example = "mi")
            @RequestParam @NotBlank String from,
            @Parameter(description = "The unit symbol to convert to.", required = true, example = "km")
            @RequestParam @NotBlank String to,
            @Parameter(description = "The numeric value to convert.", required = true, example = "3.1")
            @RequestParam double value) {
        return conversionService.convertUnits(from, to, value);
    }

    @GetMapping(value = "/bytes", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Convert Byte Sizes",
            description = """
                    Performs conversions between SI (decimal) and IEC (binary) byte units.
                                        
                    - **SI Units:** `B`, `kB`, `MB`, `GB`, `TB`
                    - **IEC Units:** `B`, `KiB`, `MiB`, `GiB`, `TiB`
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful conversion.",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ByteConversionResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid byte unit symbol.",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
            })
    public ByteConversionResponse convertBytes(
            @Parameter(description = "The byte unit to convert from.", required = true, example = "MiB")
            @RequestParam @NotBlank String from,
            @Parameter(description = "The byte unit to convert to.", required = true, example = "MB")
            @RequestParam @NotBlank String to,
            @Parameter(description = "The numeric value to convert.", required = true, example = "128")
            @RequestParam @Positive BigDecimal value) {
        return conversionService.convertBytes(from, to, value);
    }
}