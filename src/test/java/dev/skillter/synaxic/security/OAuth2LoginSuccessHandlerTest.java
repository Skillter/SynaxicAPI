package dev.skillter.synaxic.security;

import dev.skillter.synaxic.model.dto.GeneratedApiKey;
import dev.skillter.synaxic.model.entity.ApiKey;
import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.service.ApiKeyService;
import dev.skillter.synaxic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock
    private UserService userService;

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2User oauth2User;

    @Mock
    private HttpSession session;

    @InjectMocks
    private OAuth2LoginSuccessHandler handler;

    private User testUser;
    private ApiKey testApiKey;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .googleSub("google123")
                .email("test@example.com")
                .createdAt(Instant.now())
                .build();

        testApiKey = ApiKey.builder()
                .id(1L)
                .user(testUser)
                .prefix("syn_live_abc")
                .keyHash("hashedkey")
                .build();

        lenient().when(authentication.getPrincipal()).thenReturn(oauth2User);
        lenient().when(oauth2User.getAttribute("name")).thenReturn("Test User");
        lenient().when(oauth2User.getAttribute("email")).thenReturn("test@example.com");
        lenient().when(oauth2User.getAttribute("picture")).thenReturn("https://example.com/pic.jpg");
        lenient().when(request.getSession(true)).thenReturn(session);
    }

    @Test
    void onAuthenticationSuccess_WhenNewUser_ShouldCreateUserAndApiKey() throws Exception {
        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(userService.processOAuth2User(oauth2User)).thenReturn(testUser);
        when(apiKeyService.findByUserId(1L)).thenReturn(Optional.empty());

        GeneratedApiKey generatedKey = new GeneratedApiKey("syn_live_newkey", testApiKey);
        when(apiKeyService.generateAndSaveKey(testUser)).thenReturn(generatedKey);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(userService).processOAuth2User(oauth2User);
        verify(apiKeyService).findByUserId(1L);
        verify(apiKeyService).generateAndSaveKey(testUser);
        verify(session).setAttribute(eq("oauth2_user"), any());
        verify(session).setAttribute("user_id", 1L);
    }

    @Test
    void onAuthenticationSuccess_WhenExistingUser_ShouldNotCreateApiKey() throws Exception {
        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(userService.processOAuth2User(oauth2User)).thenReturn(testUser);
        when(apiKeyService.findByUserId(1L)).thenReturn(Optional.of(testApiKey));

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(userService).processOAuth2User(oauth2User);
        verify(apiKeyService).findByUserId(1L);
        verify(apiKeyService, never()).generateAndSaveKey(any());
        verify(session).setAttribute(eq("oauth2_user"), any());
        verify(session).setAttribute("user_id", 1L);
    }

    @Test
    void onAuthenticationSuccess_ShouldStoreUserInfoInSession() throws Exception {
        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(userService.processOAuth2User(oauth2User)).thenReturn(testUser);
        when(apiKeyService.findByUserId(1L)).thenReturn(Optional.of(testApiKey));

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(session).setAttribute(eq("oauth2_user"), argThat(userInfo -> {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> info = (java.util.Map<String, Object>) userInfo;
            return "Test User".equals(info.get("name")) &&
                   "test@example.com".equals(info.get("email")) &&
                   "https://example.com/pic.jpg".equals(info.get("picture"));
        }));
    }
}
