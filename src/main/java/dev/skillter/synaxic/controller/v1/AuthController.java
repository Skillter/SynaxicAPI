package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.AccountUsageDto;
import dev.skillter.synaxic.model.dto.GeneratedApiKey;
import dev.skillter.synaxic.model.dto.UserDto;
import dev.skillter.synaxic.model.entity.ApiKey;
import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.repository.ApiKeyUsageRepository;
import dev.skillter.synaxic.security.ApiKeyAuthentication;
import dev.skillter.synaxic.service.AccountUsageService;
import dev.skillter.synaxic.service.ApiKeyService;
import dev.skillter.synaxic.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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
import java.time.Instant;
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
    private final UserService userService;
    private final AccountUsageService accountUsageService;
    private final ApiKeyUsageRepository apiKeyUsageRepository;

    private User resolveUser(Object principal) {
        if (principal instanceof User) {
            return (User) principal;
        } else if (principal instanceof ApiKeyAuthentication apiKeyAuth) {
            return apiKeyAuth.getApiKey().getUser();
        } else if (principal instanceof OAuth2User oauth2User) {
            String email = oauth2User.getAttribute("email");
            return userService.findByEmail(email).orElse(null);
        }
        return null;
    }

    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Current User Info", description = "Returns information about the user associated with the provided API key or Session.")
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal Object principal) {
        User user = resolveUser(principal);

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
    @Operation(summary = "Generate or Regenerate an API Key", description = "Generates a new API key for the authenticated user.")
    public ResponseEntity<Map<String, String>> regenerateApiKey(@AuthenticationPrincipal Object principal) {
        User user = resolveUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        GeneratedApiKey generatedKey = apiKeyService.regenerateKeyForUser(user);
        return ResponseEntity.ok(Map.of("apiKey", generatedKey.fullKey()));
    }

    @GetMapping("/session")
    public ResponseEntity<?> getCurrentSession(
            @AuthenticationPrincipal OAuth2User oauth2User,
            jakarta.servlet.http.HttpServletRequest request) {

        jakarta.servlet.http.HttpSession session = request.getSession(false);
        boolean isAuthenticated = session != null && Boolean.TRUE.equals(session.getAttribute("authenticated"));

        if (oauth2User != null) {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("name", oauth2User.getAttribute("name"));
            userInfo.put("email", oauth2User.getAttribute("email"));
            userInfo.put("picture", oauth2User.getAttribute("picture"));
            
            // Try to fetch DB user details if possible
            if (oauth2User.getAttribute("email") != null) {
                User dbUser = userService.findByEmail(oauth2User.getAttribute("email")).orElse(null);
                if (dbUser != null) {
                    userInfo.put("id", dbUser.getId());
                    userInfo.put("memberSince", dbUser.getCreatedAt());
                }
            }
            return ResponseEntity.ok(userInfo);
        }

        if (isAuthenticated) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sessionUserInfo = (Map<String, Object>) session.getAttribute("oauth2_user");
            if (sessionUserInfo != null) {
                Long userId = (Long) session.getAttribute("user_id");
                if (userId != null) {
                    sessionUserInfo.put("id", userId);
                }
                return ResponseEntity.ok(sessionUserInfo);
            }
        }

        // Return 204 No Content if not logged in
        return ResponseEntity.noContent().build();
    }

    // ... (rest of the controller methods remain unchanged) ...
    @GetMapping("/api-keys")
    public ResponseEntity<List<Map<String, Object>>> getApiKeys(@AuthenticationPrincipal Object principal) {
        User user = resolveUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<ApiKey> keys = apiKeyService.findAllByUserId(user.getId());

        List<Map<String, Object>> result = keys.stream().map(key -> {
            Map<String, Object> keyMap = new HashMap<>();
            keyMap.put("id", key.getId().toString());
            keyMap.put("name", key.getKeyName() != null ? key.getKeyName() : "API Key");
            keyMap.put("keyPrefix", key.getPrefix());
            keyMap.put("keySuffix", key.getPrefix().substring(Math.max(0, key.getPrefix().length() - 4)));
            keyMap.put("createdAt", key.getCreatedAt());
            keyMap.put("lastUsedAt", key.getLastUsedAt());

            Map<String, Object> usageStats = new HashMap<>();
            Instant todayStart = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
            Long todayRequests = apiKeyUsageRepository.getTodayRequestsForApiKey(key.getId(), todayStart);
            Long totalRequests = apiKeyUsageRepository.getTotalRequestsForApiKey(key.getId());

            usageStats.put("requestsToday", todayRequests != null ? todayRequests : 0L);
            usageStats.put("totalRequests", totalRequests != null ? totalRequests : 0L);
            keyMap.put("usageStats", usageStats);

            return keyMap;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/api-key/create")
    public ResponseEntity<Map<String, String>> createApiKey(@AuthenticationPrincipal Object principal,
                                                             @RequestBody(required = false) Map<String, String> body) {
        User user = resolveUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<ApiKey> existingKeys = apiKeyService.findAllByUserId(user.getId());
        if (existingKeys.size() >= 2) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Maximum API key limit reached. You can have up to 2 API keys per account."));
        }

        GeneratedApiKey generatedKey = apiKeyService.generateAndSaveKey(user);
        
        if (body != null && body.containsKey("name")) {
            String name = body.get("name");
            if (name != null && !name.isBlank()) {
                ApiKey key = generatedKey.apiKey();
                key.setKeyName(name.trim());
                apiKeyService.save(key);
            }
        }

        return ResponseEntity.ok(Map.of("key", generatedKey.fullKey()));
    }

    @DeleteMapping("/api-key/{keyId}")
    public ResponseEntity<Void> deleteApiKey(@AuthenticationPrincipal Object principal,
                                              @PathVariable String keyId) {
        User user = resolveUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Long id = Long.parseLong(keyId);
            List<ApiKey> userKeys = apiKeyService.findAllByUserId(user.getId());
            boolean ownsKey = userKeys.stream().anyMatch(k -> k.getId().equals(id));
            
            if (ownsKey) {
                apiKeyService.deleteById(id);
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error deleting API key", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getUserStats(@AuthenticationPrincipal Object principal) {
        User user = resolveUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long totalRequests = accountUsageService.getTotalRequestsForUser(user.getId());
        Long todayRequests = accountUsageService.getTodayRequestsForUser(user.getId());

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRequests", totalRequests);
        stats.put("requestsToday", todayRequests);
        stats.put("totalApiKeys", apiKeyService.findAllByUserId(user.getId()).size());

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/export-data")
    public ResponseEntity<Map<String, Object>> exportData(@AuthenticationPrincipal Object principal) {
        User user = resolveUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("user", Map.of(
                "email", user.getEmail(),
                "memberSince", user.getCreatedAt().toString()
        ));

        List<ApiKey> keys = apiKeyService.findAllByUserId(user.getId());
        List<Map<String, Object>> keyData = keys.stream().map(key -> Map.of(
            "prefix", (Object) key.getPrefix(),
            "name", key.getKeyName() != null ? key.getKeyName() : "API Key",
            "createdAt", key.getCreatedAt().toString(),
            "lastUsedAt", key.getLastUsedAt() != null ? key.getLastUsedAt().toString() : "Never"
        )).collect(Collectors.toList());
        data.put("apiKeys", keyData);

        data.put("exportDate", java.time.Instant.now().toString());

        return ResponseEntity.ok(data);
    }

    @DeleteMapping("/delete-account")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal Object principal,
                                               jakarta.servlet.http.HttpServletRequest request) {
        User user = resolveUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        apiKeyService.deleteAllByUserId(user.getId());
        userService.deleteUser(user.getId());
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/account-usage")
    public ResponseEntity<AccountUsageDto> getAccountUsage(@AuthenticationPrincipal Object principal) {
        User user = resolveUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AccountUsageDto accountUsage = accountUsageService.getAccountUsage(user.getId());
        return ResponseEntity.ok(accountUsage);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("SYNAXIC_SESSION", "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok().build();
    }
}

