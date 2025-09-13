package dev.skillter.synaxic.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Information about the authenticated user.")
public class UserDto {
    @Schema(description = "The user's unique identifier.", example = "1")
    private Long id;

    @Schema(description = "The user's email address associated with their account.", example = "developer@example.com")
    private String email;

    @Schema(description = "The timestamp when the user first signed in.")
    private Instant memberSince;

    @Schema(description = "The prefix of the user's current API key (e.g., 'syn_live_abc...'). Useful for identification without exposing the full key.", example = "syn_live_abc")
    private String apiKeyPrefix;

    @Schema(description = "The timestamp when the API key was last used.")
    private Instant apiKeyLastUsed;
}