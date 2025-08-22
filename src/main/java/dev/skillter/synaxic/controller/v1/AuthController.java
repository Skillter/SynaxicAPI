package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.UserDto;
import dev.skillter.synaxic.model.entity.ApiKey;
import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
            security = @SecurityRequirement(name = "ApiKey"))
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        Optional<ApiKey> apiKeyOpt = apiKeyService.findByUserId(user.getId());

        UserDto userDto = UserDto.builder()
                .email(user.getEmail())
                .memberSince(user.getCreatedAt())
                .apiKey(apiKeyOpt.isPresent() ? "Key provisioned" : "No key found")
                .build();

        return ResponseEntity.ok(userDto);
    }

    @GetMapping("/login-success")
    @Operation(summary = "Login success confirmation",
            description = "A simple endpoint to confirm that the OAuth2 login flow was successful. In a real app, this would redirect to a dashboard.")
    public ResponseEntity<Map<String, String>> loginSuccess() {
        return ResponseEntity.ok(Map.of("status", "success", "message", "You have been logged in. Please use the /v1/auth/me endpoint with your API key to get your details."));
    }
}