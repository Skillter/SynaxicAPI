package dev.skillter.synaxic.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Authenticated user information")
public class UserDto {
    @Schema(description = "User's email address", example = "developer@example.com")
    private String email;

    @Schema(description = "Timestamp when the user first signed in")
    private Instant memberSince;

    @Schema(description = "The prefix of the user's current API key", example = "syn_live_abc")
    private String apiKeyPrefix;

    @Schema(description = "A message confirming API key status", example = "API key is active")
    private String apiKeyStatus;
}