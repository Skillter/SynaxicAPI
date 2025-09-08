package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.EmailValidationResponse;
import dev.skillter.synaxic.service.EmailValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/email")
@RequiredArgsConstructor
@Validated
@Tag(name = "Email Validator", description = "Endpoints for email syntax and disposable domain validation")
public class EmailValidatorController {

    private final EmailValidationService emailValidationService;

    @GetMapping(value = "/validate", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Validate an email address",
            description = "Performs a comprehensive check on an email address, including syntax validation, disposable domain detection, and MX record lookup.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Validation check completed successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid request, e.g., missing or malformed email parameter", content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
            })
    public EmailValidationResponse validateEmail(
            @Parameter(description = "The email address to validate", required = true, example = "developer@example.com")
            @RequestParam @NotBlank @Email String email) {
        return emailValidationService.validateEmail(email);
    }
}