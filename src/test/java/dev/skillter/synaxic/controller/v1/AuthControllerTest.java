package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.AccountUsageDto;
import dev.skillter.synaxic.model.dto.GeneratedApiKey;
import dev.skillter.synaxic.model.entity.ApiKey;
import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.repository.ApiKeyUsageRepository;
import dev.skillter.synaxic.service.AccountUsageService;
import dev.skillter.synaxic.service.ApiKeyService;
import dev.skillter.synaxic.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private UserService userService;

    @Mock
    private AccountUsageService accountUsageService;

    @Mock
    private ApiKeyUsageRepository apiKeyUsageRepository;

    @InjectMocks
    private AuthController authController;

    private OAuth2User oauth2User;
    private User testUser;
    private ApiKey testApiKey;

    @BeforeEach
    void setUp() {
        oauth2User = mock(OAuth2User.class);
        lenient().when(oauth2User.getAttribute("email")).thenReturn("test@example.com");
        lenient().when(oauth2User.getAttribute("name")).thenReturn("Test User");
        lenient().when(oauth2User.getAttribute("picture")).thenReturn("https://example.com/pic.jpg");

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
                .quotaLimit(10000)
                .createdAt(Instant.now())
                .lastUsedAt(Instant.now())
                .build();
    }

    @Test
    void getApiKeys_WithValidUser_ShouldReturnKeys() {
        when(userService.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(apiKeyService.findAllByUserId(1L)).thenReturn(Arrays.asList(testApiKey));
        // Mock batch query methods to avoid NPE
        Object[] todayRow = new Object[]{1L, 10L};
        Object[] totalRow = new Object[]{1L, 100L};
        List<Object[]> todayResult = Collections.singletonList(todayRow);
        List<Object[]> totalResult = Collections.singletonList(totalRow);
        when(apiKeyUsageRepository.getTodayRequestsForApiKeys(anyList(), any())).thenReturn(todayResult);
        when(apiKeyUsageRepository.getTotalRequestsForApiKeys(anyList())).thenReturn(totalResult);

        ResponseEntity<List<Map<String, Object>>> response = authController.getApiKeys(oauth2User);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).get("keyPrefix")).isEqualTo("syn_live_abc");
    }

    @Test
    void getUserStats_WithValidUser_ShouldReturnStats() {
        when(userService.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        // Mock account usage service calls
        when(accountUsageService.getTotalRequestsForUser(1L)).thenReturn(500L);
        when(accountUsageService.getTodayRequestsForUser(1L)).thenReturn(50L);

        ResponseEntity<Map<String, Object>> response = authController.getUserStats(oauth2User);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsEntry("totalRequests", 500L);
        assertThat(response.getBody()).containsEntry("requestsToday", 50L);
    }

    @Test
    void getAccountUsage_WithValidUser_ShouldReturnUsage() {
        AccountUsageDto usageDto = AccountUsageDto.builder()
                .accountId(1L)
                .accountRateLimit(10000L)
                .accountRequestsUsed(500L)
                .build();

        when(userService.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(accountUsageService.getAccountUsage(1L)).thenReturn(usageDto);

        ResponseEntity<AccountUsageDto> response = authController.getAccountUsage(oauth2User);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(usageDto);
    }
}

