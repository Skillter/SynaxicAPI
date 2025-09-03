package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.GeneratedApiKey;
import dev.skillter.synaxic.model.dto.UserDto;
import dev.skillter.synaxic.model.entity.ApiKey;
import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@Tag(name = "Authentication", description = "User authentication and API key management")
public class AuthController {

    private final ApiKeyService apiKeyService;

    @GetMapping("/me")
    @Operation(summary = "Get current user info",
            description = "Returns information about the user associated with the provided API key.",
            security = @SecurityRequirement(name = "ApiKeyAuth"))
    @ApiResponse(responseCode = "200", description = "Successfully retrieved user information")
    @ApiResponse(responseCode = "401", description = "Unauthorized - API key is missing or invalid", content = @Content)
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

    @PostMapping("/api-key")
    @Operation(summary = "Generate or regenerate an API key",
            description = "Generates a new API key for the authenticated user. Any existing key will be invalidated. The new key is returned only once.",
            security = @SecurityRequirement(name = "ApiKeyAuth"))
    @ApiResponse(responseCode = "200", description = "Successfully generated a new API key.",
            content = @Content(schema = @Schema(example = "{\"apiKey\": \"syn_live_...\"}")))
    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    public ResponseEntity<Map<String, String>> regenerateApiKey(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        GeneratedApiKey generatedKey = apiKeyService.regenerateKeyForUser(user);
        return ResponseEntity.ok(Map.of("apiKey", generatedKey.fullKey()));
    }
}