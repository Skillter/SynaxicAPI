package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.GeneratedApiKey;
import dev.skillter.synaxic.model.dto.UserDto;
import dev.skillter.synaxic.model.entity.ApiKey;
import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication and API key management.")
@SecurityRequirement(name = "ApiKeyAuth")
public class AuthController {

    private final ApiKeyService apiKeyService;

    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Current User Info",
            description = "Returns information about the user associated with the provided API key. This is useful for verifying your key and checking its status.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved user information.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserDto.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized - API key is missing or invalid.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<ApiKey> apiKeyOpt = apiKeyService.findByUserId(user.getId());

        UserDto userDto = apiKeyOpt.map(apiKey -> UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .memberSince(user.getCreatedAt())
                .apiKeyPrefix(apiKey.getPrefix())
                .apiKeyLastUsed(apiKey.getLastUsedAt())
                .build()
        ).orElseGet(() -> UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .memberSince(user.getCreatedAt())
                .build());

        return ResponseEntity.ok(userDto);
    }

    @PostMapping(value = "/api-key", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Generate or Regenerate an API Key",
            description = "Generates a new API key for the authenticated user. **Warning:** Any existing key will be immediately invalidated. The new key is returned only once upon creation, so be sure to store it securely.")
    @ApiResponse(responseCode = "200", description = "Successfully generated a new API key.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = @ExampleObject(value = "{\"apiKey\": \"syn_live_aBcDeFgHiJkLmNoPqRsTuVwXyZ123456\"}")))
    @ApiResponse(responseCode = "401", description = "Unauthorized - A valid API key is required to perform this action.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Map<String, String>> regenerateApiKey(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        GeneratedApiKey generatedKey = apiKeyService.regenerateKeyForUser(user);
        return ResponseEntity.ok(Map.of("apiKey", generatedKey.fullKey()));
    }
}