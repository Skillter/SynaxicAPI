package dev.skillter.synaxic.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "The detailed result of an email validation check.")
public class EmailValidationResponse {

    @Schema(description = "The email address that was validated.", example = "test@example.com")
    private String email;

    @Schema(description = "The domain part of the email address.", example = "example.com")
    private String domain;

    @Schema(description = "Indicates if the email address has a valid format according to RFC 5322.", example = "true")
    private boolean isValidSyntax;

    @Schema(description = "Indicates if the domain is from a known disposable (temporary) email provider.", example = "false")
    private boolean isDisposable;

    @Schema(description = "Indicates if the domain has valid MX (Mail Exchange) DNS records, suggesting it can receive email.", example = "true")
    private boolean hasMxRecords;

    @Schema(description = "A suggestion for correction if a common typo is detected in the domain (feature not yet implemented).", example = "Did you mean example.com?")
    private String suggestion;
}