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
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    // OAuth2 Session Endpoints (for dashboard)

    @GetMapping("/session")
    @Operation(summary = "Get Current OAuth2 Session", description = "Returns the currently authenticated OAuth2 user information")
    public ResponseEntity<Map<String, Object>> getCurrentSession(@AuthenticationPrincipal OAuth2User oauth2User) {
        if (oauth2User == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("name", oauth2User.getAttribute("name"));
        userInfo.put("email", oauth2User.getAttribute("email"));
        userInfo.put("picture", oauth2User.getAttribute("picture"));

        return ResponseEntity.ok(userInfo);
    }

    @GetMapping("/api-keys")
    @Operation(summary = "Get All API Keys", description = "Returns all API keys for the authenticated user")
    public ResponseEntity<List<Map<String, Object>>> getApiKeys(@AuthenticationPrincipal OAuth2User oauth2User) {
        if (oauth2User == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = oauth2User.getAttribute("email");
        // TODO: Implement getting all keys for user
        // For now, return empty list as placeholder
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/api-key/create")
    @Operation(summary = "Create New API Key", description = "Creates a new API key for the authenticated OAuth2 user")
    public ResponseEntity<Map<String, String>> createApiKey(@AuthenticationPrincipal OAuth2User oauth2User,
                                                             @RequestBody(required = false) Map<String, String> body) {
        if (oauth2User == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = oauth2User.getAttribute("email");
        String name = body != null ? body.get("name") : null;

        // TODO: Implement API key creation for OAuth2 user
        // For now, return placeholder
        return ResponseEntity.ok(Map.of("key", "key_live_placeholder"));
    }

    @DeleteMapping("/api-key/{keyId}")
    @Operation(summary = "Delete API Key", description = "Deletes the specified API key")
    public ResponseEntity<Void> deleteApiKey(@AuthenticationPrincipal OAuth2User oauth2User,
                                              @PathVariable String keyId) {
        if (oauth2User == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // TODO: Implement API key deletion
        return ResponseEntity.ok().build();
    }

    @GetMapping("/stats")
    @Operation(summary = "Get User Statistics", description = "Returns usage statistics for the authenticated user")
    public ResponseEntity<Map<String, Object>> getUserStats(@AuthenticationPrincipal OAuth2User oauth2User) {
        if (oauth2User == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // TODO: Implement stats retrieval
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRequests", 0);
        stats.put("requestsToday", 0);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/export-data")
    @Operation(summary = "Export User Data", description = "Exports all user data in JSON format (GDPR compliance)")
    public ResponseEntity<Map<String, Object>> exportData(@AuthenticationPrincipal OAuth2User oauth2User) {
        if (oauth2User == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("user", Map.of(
                "name", oauth2User.getAttribute("name"),
                "email", oauth2User.getAttribute("email")
        ));
        data.put("apiKeys", List.of());
        data.put("exportDate", java.time.Instant.now().toString());

        return ResponseEntity.ok(data);
    }

    @DeleteMapping("/delete-account")
    @Operation(summary = "Delete Account", description = "Permanently deletes the user account and all associated data")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal OAuth2User oauth2User) {
        if (oauth2User == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // TODO: Implement account deletion
        return ResponseEntity.ok().build();
    }
}