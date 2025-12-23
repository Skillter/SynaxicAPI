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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

            // Register cache eviction to run AFTER transaction commits
            // This prevents race conditions where cache is evicted before DB commit
            // If no transaction is active (e.g., in tests), evict immediately
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        evictFromCache(prefix);
                    }
                });
            } else {
                // No active transaction, evict immediately (safe for tests and fallback in production)
                evictFromCache(prefix);
            }

            apiKeyRepository.delete(key);
            log.info("Deleted API key {} for user {}", prefix, userId);
        } else {
            log.warn("Attempted to delete non-existent API key ID: {}", id);
        }
    }

    private void evictFromCache(String prefix) {
        try {
            Cache cache = cacheManager.getCache(CacheConfig.CACHE_API_KEY_BY_PREFIX);
            if (cache != null) {
                cache.evict(prefix);
                log.debug("Evicted API key {} from cache", prefix);
            }
        } catch (Exception e) {
            log.error("Failed to evict key from cache: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void deleteAllByUserId(Long userId) {
        // Find all keys first to evict them after transaction commit
        java.util.List<ApiKey> keys = apiKeyRepository.findAllByUser_Id(userId);
        java.util.List<String> prefixes = keys.stream().map(ApiKey::getPrefix).toList();

        // Register cache eviction to run AFTER transaction commits
        // If no transaction is active (e.g., in tests), evict immediately
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String prefix : prefixes) {
                        evictFromCache(prefix);
                    }
                    log.debug("Evicted {} API keys from cache after transaction commit", prefixes.size());
                }
            });
        } else {
            // No active transaction, evict immediately (safe for tests and fallback in production)
            for (String prefix : prefixes) {
                evictFromCache(prefix);
            }
        }

        apiKeyRepository.deleteAllByUser_Id(userId);
        log.info("Deleted all API keys for user {}", userId);
    }
}

