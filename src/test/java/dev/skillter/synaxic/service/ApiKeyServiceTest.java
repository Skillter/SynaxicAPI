package dev.skillter.synaxic.service;

import dev.skillter.synaxic.model.dto.GeneratedApiKey;
import dev.skillter.synaxic.model.entity.ApiKey;
import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.repository.ApiKeyRepository;
import dev.skillter.synaxic.util.KeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private KeyGenerator keyGenerator;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private User testUser;
    private ApiKey testApiKey;
    private GeneratedApiKey generatedApiKey;

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
                .quotaLimit(10000)
                .createdAt(Instant.now())
                .build();

        generatedApiKey = new GeneratedApiKey("syn_live_abcdefghijklmnop", testApiKey);
    }

    @Test
    void generateAndSaveKey_ShouldGenerateAndSaveKey() {
        when(keyGenerator.generate(testUser)).thenReturn(generatedApiKey);
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

        GeneratedApiKey result = apiKeyService.generateAndSaveKey(testUser);

        assertThat(result).isNotNull();
        assertThat(result.fullKey()).isEqualTo("syn_live_abcdefghijklmnop");
        verify(keyGenerator).generate(testUser);
        verify(apiKeyRepository).save(testApiKey);
    }

    @Test
    void regenerateKeyForUser_WhenOldKeyExists_ShouldRevokeAndGenerateNew() {
        // Mock finding the old key by user ID
        when(apiKeyRepository.findByUser_Id(1L)).thenReturn(Optional.of(testApiKey));
        
        // Mock finding the key by ID (required by deleteById)
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(testApiKey));
        
        // Mock generation of new key
        when(keyGenerator.generate(testUser)).thenReturn(generatedApiKey);
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

        GeneratedApiKey result = apiKeyService.regenerateKeyForUser(testUser);

        assertThat(result).isNotNull();
        
        // Verify flow
        verify(apiKeyRepository).findByUser_Id(1L);
        verify(apiKeyRepository).findById(1L); // Verified call inside deleteById
        verify(apiKeyRepository).delete(testApiKey);
        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    void regenerateKeyForUser_WhenNoOldKey_ShouldGenerateNew() {
        when(apiKeyRepository.findByUser_Id(1L)).thenReturn(Optional.empty());
        when(keyGenerator.generate(testUser)).thenReturn(generatedApiKey);
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

        GeneratedApiKey result = apiKeyService.regenerateKeyForUser(testUser);

        assertThat(result).isNotNull();
        verify(apiKeyRepository).findByUser_Id(1L);
        verify(apiKeyRepository, never()).delete(any());
        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    void validateApiKey_WithValidKey_ShouldReturnApiKey() {
        String fullKey = "syn_live_abcdefghijklmnop";
        String hashedKey = "hashedkey";

        when(apiKeyRepository.findByPrefix("syn_live_abc")).thenReturn(Optional.of(testApiKey));
        when(keyGenerator.calculateSha256(fullKey)).thenReturn(hashedKey);

        Optional<ApiKey> result = apiKeyService.validateApiKey(fullKey);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testApiKey);
    }

    @Test
    void validateApiKey_WithInvalidPrefix_ShouldReturnEmpty() {
        Optional<ApiKey> result = apiKeyService.validateApiKey("invalid_key");

        assertThat(result).isEmpty();
        verify(apiKeyRepository, never()).findByPrefix(any());
    }

    @Test
    void validateApiKey_WithNullKey_ShouldReturnEmpty() {
        Optional<ApiKey> result = apiKeyService.validateApiKey(null);

        assertThat(result).isEmpty();
        verify(apiKeyRepository, never()).findByPrefix(any());
    }

    @Test
    void validateApiKey_WithWrongHash_ShouldReturnEmpty() {
        String fullKey = "syn_live_abcdefghijklmnop";

        when(apiKeyRepository.findByPrefix("syn_live_abc")).thenReturn(Optional.of(testApiKey));
        when(keyGenerator.calculateSha256(fullKey)).thenReturn("wronghash");

        Optional<ApiKey> result = apiKeyService.validateApiKey(fullKey);

        assertThat(result).isEmpty();
    }

    @Test
    void findByUserId_ShouldReturnApiKey() {
        when(apiKeyRepository.findByUser_Id(1L)).thenReturn(Optional.of(testApiKey));

        Optional<ApiKey> result = apiKeyService.findByUserId(1L);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testApiKey);
        verify(apiKeyRepository).findByUser_Id(1L);
    }

    @Test
    void findAllByUserId_ShouldReturnAllKeys() {
        ApiKey key2 = ApiKey.builder()
                .id(2L)
                .user(testUser)
                .prefix("syn_live_xyz")
                .keyHash("hashedkey2")
                .build();

        List<ApiKey> keys = Arrays.asList(testApiKey, key2);
        when(apiKeyRepository.findAllByUser_Id(1L)).thenReturn(keys);

        List<ApiKey> result = apiKeyService.findAllByUserId(1L);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(testApiKey, key2);
        verify(apiKeyRepository).findAllByUser_Id(1L);
    }

    @Test
    void deleteById_WhenKeyExists_ShouldDeleteKey() {
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(testApiKey));

        apiKeyService.deleteById(1L);

        verify(apiKeyRepository).findById(1L);
        verify(apiKeyRepository).delete(testApiKey);
    }

    @Test
    void deleteById_WhenKeyDoesNotExist_ShouldNotDelete() {
        when(apiKeyRepository.findById(999L)).thenReturn(Optional.empty());

        apiKeyService.deleteById(999L);

        verify(apiKeyRepository).findById(999L);
        verify(apiKeyRepository, never()).delete(any());
    }

    @Test
    void deleteAllByUserId_ShouldCallRepository() {
        doNothing().when(apiKeyRepository).deleteAllByUser_Id(1L);

        apiKeyService.deleteAllByUserId(1L);

        verify(apiKeyRepository).deleteAllByUser_Id(1L);
    }
}

