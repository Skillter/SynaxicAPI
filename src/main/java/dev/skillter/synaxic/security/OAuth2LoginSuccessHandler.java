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
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        User user = userService.processOAuth2User(oauth2User);

        if (apiKeyService.findByUserId(user.getId()).isEmpty()) {
            apiKeyService.generateAndSaveKey(user);
        }

        // Explicitly store user info in session for later retrieval
        HttpSession session = request.getSession(true);
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("name", oauth2User.getAttribute("name"));
        userInfo.put("email", oauth2User.getAttribute("email"));
        userInfo.put("picture", oauth2User.getAttribute("picture"));

        session.setAttribute("oauth2_user", userInfo);
        session.setAttribute("user_id", user.getId());
        log.info("OAuth2 user logged in: {} ({})", oauth2User.getAttribute("email"), user.getId());

        this.setDefaultTargetUrl("/v1/auth/login-success");
        super.onAuthenticationSuccess(request, response, authentication);
    }
}