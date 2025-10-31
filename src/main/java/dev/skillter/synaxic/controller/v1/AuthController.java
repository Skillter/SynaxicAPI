package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.AccountUsageDto;
import dev.skillter.synaxic.model.dto.GeneratedApiKey;
import dev.skillter.synaxic.model.dto.UserDto;
import dev.skillter.synaxic.model.entity.ApiKey;
import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.service.AccountUsageService;
import dev.skillter.synaxic.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "User authentication and API key management.")
@SecurityRequirement(name = "ApiKeyAuth")
public class AuthController {

    private final ApiKeyService apiKeyService;
    private final dev.skillter.synaxic.service.UserService userService;
    private final AccountUsageService accountUsageService;

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
    public ResponseEntity<Map<String, Object>> getCurrentSession(
            @AuthenticationPrincipal OAuth2User oauth2User,
            jakarta.servlet.http.HttpServletRequest request) {

        // Debug logging
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        log.debug("Session check - Session exists: {}, OAuth2User: {}",
                 session != null, oauth2User != null);

        if (session != null) {
            log.debug("Session ID: {}, Authenticated: {}, User ID: {}",
                     session.getId(),
                     session.getAttribute("authenticated"),
                     session.getAttribute("user_id"));
        }

        // Check if user is authenticated via session attribute first
        boolean isAuthenticated = false;
        if (session != null) {
            Boolean authStatus = (Boolean) session.getAttribute("authenticated");
            isAuthenticated = Boolean.TRUE.equals(authStatus);
            log.debug("Session authentication status: {}", isAuthenticated);
        }

        // First try to get OAuth2User from authentication principal
        if (oauth2User != null) {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("name", oauth2User.getAttribute("name"));
            userInfo.put("email", oauth2User.getAttribute("email"));
            userInfo.put("picture", oauth2User.getAttribute("picture"));
            log.info("Found OAuth2User in principal: {}", (Object) oauth2User.getAttribute("email"));
            return ResponseEntity.ok(userInfo);
        }

        // Fallback: check session attributes (these are persisted by OAuth2LoginSuccessHandler)
        if (session != null && isAuthenticated) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sessionUserInfo = (Map<String, Object>) session.getAttribute("oauth2_user");
            if (sessionUserInfo != null && !sessionUserInfo.isEmpty()) {
                log.info("Found user info in session: {}", sessionUserInfo.get("email"));
                return ResponseEntity.ok(sessionUserInfo);
            }

            // Even if oauth2_user is missing, if we have authenticated=true, construct basic user info
            Long userId = (Long) session.getAttribute("user_id");
            if (userId != null) {
                Map<String, Object> basicUserInfo = new HashMap<>();
                basicUserInfo.put("email", "user@" + userId + ".example.com");
                basicUserInfo.put("name", "Authenticated User");
                log.info("Created basic user info from session attributes for user: {}", userId);
                return ResponseEntity.ok(basicUserInfo);
            }
        }

        // No OAuth2 session found
        log.warn("No OAuth2 session found - Session: {}, Authenticated: {}, Principal: {}",
                session != null ? session.getId() : "none", isAuthenticated, oauth2User);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @GetMapping("/api-keys")
    @Operation(summary = "Get All API Keys", description = "Returns all API keys for the authenticated user")
    public ResponseEntity<List<Map<String, Object>>> getApiKeys(@AuthenticationPrincipal OAuth2User oauth2User) {
        if (oauth2User == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = oauth2User.getAttribute("email");
        Optional<User> userOpt = userService.findByEmail(email);

        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<ApiKey> keys = apiKeyService.findAllByUserId(userOpt.get().getId());

        List<Map<String, Object>> result = keys.stream().map(key -> {
            Map<String, Object> keyMap = new HashMap<>();
            keyMap.put("id", key.getId().toString());
            keyMap.put("name", "API Key");
            keyMap.put("keyPrefix", key.getPrefix());
            keyMap.put("keySuffix", key.getPrefix().substring(Math.max(0, key.getPrefix().length() - 4)));
            keyMap.put("createdAt", key.getCreatedAt());
            keyMap.put("lastUsedAt", key.getLastUsedAt());
            return keyMap;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/api-key/create")
    @Operation(summary = "Create New API Key", description = "Creates a new API key for the authenticated OAuth2 user. Maximum 2 keys per account.")
    @ApiResponse(responseCode = "200", description = "Successfully created a new API key.")
    @ApiResponse(responseCode = "400", description = "Maximum API key limit reached (2 keys per account).",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized - A valid session is required.")
    public ResponseEntity<Map<String, String>> createApiKey(@AuthenticationPrincipal OAuth2User oauth2User,
                                                             @RequestBody(required = false) Map<String, String> body) {
        if (oauth2User == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = oauth2User.getAttribute("email");
        Optional<User> userOpt = userService.findByEmail(email);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userOpt.get();

        // Check if user already has 2 API keys (maximum limit)
        List<ApiKey> existingKeys = apiKeyService.findAllByUserId(user.getId());
        if (existingKeys.size() >= 2) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Maximum API key limit reached. You can have up to 2 API keys per account. Please delete an existing key before creating a new one."));
        }

        GeneratedApiKey generatedKey = apiKeyService.generateAndSaveKey(user);

        return ResponseEntity.ok(Map.of("key", generatedKey.fullKey()));
    }

    @DeleteMapping("/api-key/{keyId}")
    @Operation(summary = "Delete API Key", description = "Deletes the specified API key")
    public ResponseEntity<Void> deleteApiKey(@AuthenticationPrincipal OAuth2User oauth2User,
                                              @PathVariable String keyId) {
        if (oauth2User == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = oauth2User.getAttribute("email");
        Optional<User> userOpt = userService.findByEmail(email);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Long id = Long.parseLong(keyId);
            apiKeyService.deleteById(id);
            return ResponseEntity.ok().build();
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "Get User Statistics", description = "Returns usage statistics for the authenticated user")
    public ResponseEntity<Map<String, Object>> getUserStats(@AuthenticationPrincipal OAuth2User oauth2User) {
        if (oauth2User == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = oauth2User.getAttribute("email");
        Optional<User> userOpt = userService.findByEmail(email);

        if (userOpt.isEmpty()) {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalRequests", 0);
            stats.put("requestsToday", 0);
            return ResponseEntity.ok(stats);
        }

        // For now, return placeholder stats
        // TODO: Implement actual stats tracking from ApiStats table if needed
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

        String email = oauth2User.getAttribute("email");
        Optional<User> userOpt = userService.findByEmail(email);

        Map<String, Object> data = new HashMap<>();
        data.put("user", Map.of(
                "name", oauth2User.getAttribute("name"),
                "email", email
        ));

        if (userOpt.isPresent()) {
            List<ApiKey> keys = apiKeyService.findAllByUserId(userOpt.get().getId());
            List<Map<String, Object>> keyData = keys.stream().map(key -> Map.of(
                "prefix", (Object) key.getPrefix(),
                "createdAt", key.getCreatedAt().toString(),
                "lastUsedAt", key.getLastUsedAt() != null ? key.getLastUsedAt().toString() : "Never"
            )).collect(Collectors.toList());
            data.put("apiKeys", keyData);
        } else {
            data.put("apiKeys", List.of());
        }

        data.put("exportDate", java.time.Instant.now().toString());

        return ResponseEntity.ok(data);
    }

    @DeleteMapping("/delete-account")
    @Operation(summary = "Delete Account", description = "Permanently deletes the user account and all associated data")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal OAuth2User oauth2User,
                                               jakarta.servlet.http.HttpServletRequest request) {
        if (oauth2User == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = oauth2User.getAttribute("email");
        Optional<User> userOpt = userService.findByEmail(email);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Delete all API keys first
            apiKeyService.deleteAllByUserId(user.getId());
            // Delete the user
            userService.deleteUser(user.getId());
            // Invalidate the session
            jakarta.servlet.http.HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/account-usage")
    @Operation(summary = "Get Account Usage", description = "Returns detailed account-level usage statistics with breakdown by API key")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved account usage statistics.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = AccountUsageDto.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized - API key is missing or invalid.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AccountUsageDto> getAccountUsage(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AccountUsageDto accountUsage = accountUsageService.getAccountUsage(user.getId());
        return ResponseEntity.ok(accountUsage);
    }

    @GetMapping("/key-usage-breakdown")
    @Operation(summary = "Get Key Usage Breakdown", description = "Returns usage breakdown for all API keys belonging to the account")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved key usage breakdown.")
    @ApiResponse(responseCode = "401", description = "Unauthorized - API key is missing or invalid.")
    public ResponseEntity<List<AccountUsageDto.KeyUsageBreakdown>> getKeyUsageBreakdown(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AccountUsageDto accountUsage = accountUsageService.getAccountUsage(user.getId());
        return ResponseEntity.ok(accountUsage.getKeyUsageBreakdown());
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Logs out the current OAuth2 user and invalidates the session")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // Invalidate the HTTP session
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        // Clear security context
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        // Clear session cookie
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("SYNAXIC_SESSION", "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setMaxAge(0);
        // Note: SameSite attribute is handled by the CookieSerializer configuration
        response.addCookie(cookie);

        return ResponseEntity.ok().build();
    }
}