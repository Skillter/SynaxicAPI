package dev.skillter.synaxic.security;

import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.service.ApiKeyService;
import dev.skillter.synaxic.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;
    private final ApiKeyService apiKeyService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        try {
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            User user = userService.processOAuth2User(oauth2User);

            if (apiKeyService.findByUserId(user.getId()).isEmpty()) {
                apiKeyService.generateAndSaveKey(user);
            }

            // Session fixation protection: invalidate existing session and create new one
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }

            // Create new session with security settings
            HttpSession newSession = request.getSession(true);

            // Set session timeout (30 minutes)
            newSession.setMaxInactiveInterval(1800);

            // Minimal user data storage - only store essential information
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("name", sanitizeString(oauth2User.getAttribute("name")));
            userInfo.put("email", sanitizeString(oauth2User.getAttribute("email")));
            // Store picture URL but validate it's a proper URL format
            String pictureUrl = sanitizeString(oauth2User.getAttribute("picture"));
            if (isValidUrl(pictureUrl)) {
                userInfo.put("picture", pictureUrl);
            }

            newSession.setAttribute("oauth2_user", userInfo);
            newSession.setAttribute("user_id", user.getId());
            newSession.setAttribute("login_time", System.currentTimeMillis());

            // Set secure session attributes
            newSession.setAttribute("authenticated", true);

            log.info("OAuth2 user logged in: {} ({}) - Session secured",
                    oauth2User.getAttribute("email"), user.getId());

            this.setDefaultTargetUrl("/v1/auth/login-success");
            super.onAuthenticationSuccess(request, response, authentication);
        } catch (Exception e) {
            log.error("Error during OAuth2 login success for user: {}",
                    oauth2User != null ? oauth2User.getAttribute("email") : "unknown", e);

            // Fallback: still try to redirect but with error logging
            this.setDefaultTargetUrl("/v1/auth/login-success");
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }

    /**
     * Sanitizes string input to prevent injection attacks
     */
    private String sanitizeString(String input) {
        if (input == null) {
            return null;
        }

        // Basic sanitization - remove potentially dangerous characters
        return input.replaceAll("[<>\"'&]", "");
    }

    /**
     * Basic URL validation to prevent injection
     */
    private boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        // Basic URL pattern check
        return url.matches("^https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$");
    }
}