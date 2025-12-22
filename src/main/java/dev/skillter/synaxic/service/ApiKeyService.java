package dev.skillter.synaxic.service;

import dev.skillter.synaxic.config.CacheConfig;
import dev.skillter.synaxic.model.dto.GeneratedApiKey;
import dev.skillter.synaxic.model.entity.ApiKey;
import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.repository.ApiKeyRepository;
import dev.skillter.synaxic.util.KeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final KeyGenerator keyGenerator;
    private final CacheManager cacheManager;

    @Transactional
    public GeneratedApiKey generateAndSaveKey(User user) {
        GeneratedApiKey generatedKey = keyGenerator.generate(user);
        apiKeyRepository.save(generatedKey.apiKey());
        log.info("Generated new API key for user {}", user.getId());
        return generatedKey;
    }

    @Transactional
    public GeneratedApiKey regenerateKeyForUser(User user) {
        apiKeyRepository.findByUser_Id(user.getId()).ifPresent(key -> deleteById(key.getId()));
        return generateAndSaveKey(user);
    }

    /**
     * Saves an API key entity. Useful for updating metadata like key names.
     */
    @Transactional
    public void save(ApiKey apiKey) {
        apiKeyRepository.save(apiKey);
    }

    @Transactional(readOnly = true)
    public Optional<ApiKey> validateApiKey(String fullKey) {
        if (fullKey == null || !fullKey.startsWith(KeyGenerator.PREFIX) || fullKey.length() < 12) {
            return Optional.empty();
        }

        String prefix = fullKey.substring(0, 12);
        Optional<ApiKey> apiKeyOpt = findApiKeyByPrefix(prefix);

        if (apiKeyOpt.isEmpty()) {
            return Optional.empty();
        }

        ApiKey apiKey = apiKeyOpt.get();
        String providedKeyHash = keyGenerator.calculateSha256(fullKey);

        if (MessageDigest.isEqual(providedKeyHash.getBytes(StandardCharsets.UTF_8), apiKey.getKeyHash().getBytes(StandardCharsets.UTF_8))) {
            updateLastUsedAsync(apiKey.getId());
            return Optional.of(apiKey);
        }

        return Optional.empty();
    }

    @Cacheable(value = CacheConfig.CACHE_API_KEY_BY_PREFIX, key = "#prefix", unless = "!#result.isPresent()")
    public Optional<ApiKey> findApiKeyByPrefix(String prefix) {
        log.debug("DB lookup for API key with prefix: {}", prefix);
        return apiKeyRepository.findByPrefix(prefix);
    }

    @Async
    @Transactional
    public void updateLastUsedAsync(Long apiKeyId) {
        apiKeyRepository.findById(apiKeyId).ifPresent(apiKey -> {
            apiKey.setLastUsedAt(Instant.now());
            apiKeyRepository.save(apiKey);
        });
    }

    public Optional<ApiKey> findByUserId(Long userId) {
        return apiKeyRepository.findByUser_Id(userId);
    }

    @Transactional(readOnly = true)
    public java.util.List<ApiKey> findAllByUserId(Long userId) {
        return apiKeyRepository.findAllByUser_Id(userId);
    }

    @Transactional
    public void deleteById(Long id) {
        Optional<ApiKey> keyOpt = apiKeyRepository.findById(id);
        if (keyOpt.isPresent()) {
            ApiKey key = keyOpt.get();
            String prefix = key.getPrefix();
            Long userId = key.getUser().getId();

            // Manually evict from cache to avoid internal proxy call issues
            try {
                Cache cache = cacheManager.getCache(CacheConfig.CACHE_API_KEY_BY_PREFIX);
                if (cache != null) {
                    cache.evict(prefix);
                    log.debug("Evicted API key {} from cache", prefix);
                }
            } catch (Exception e) {
                log.warn("Failed to evict key from cache during deletion: {}", e.getMessage());
                // Continue with deletion even if cache eviction fails
            }

            apiKeyRepository.delete(key);
            log.info("Deleted API key {} for user {}", prefix, userId);
        } else {
            log.warn("Attempted to delete non-existent API key ID: {}", id);
        }
    }

    @Transactional
    public void deleteAllByUserId(Long userId) {
        // Find all keys first to evict them
        java.util.List<ApiKey> keys = apiKeyRepository.findAllByUser_Id(userId);
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_API_KEY_BY_PREFIX);
        
        for (ApiKey key : keys) {
            if (cache != null) {
                cache.evict(key.getPrefix());
            }
        }
        
        apiKeyRepository.deleteAllByUser_Id(userId);
        log.info("Deleted all API keys for user {}", userId);
    }
}

