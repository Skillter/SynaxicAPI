package dev.skillter.synaxic.service;

import dev.skillter.synaxic.model.dto.GeneratedApiKey;
import dev.skillter.synaxic.model.entity.ApiKey;
import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.repository.ApiKeyRepository;
import dev.skillter.synaxic.util.KeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional
    public GeneratedApiKey generateAndSaveKey(User user) {
        GeneratedApiKey generatedKey = keyGenerator.generate(user);
        apiKeyRepository.save(generatedKey.apiKey());
        log.info("Generated new API key for user {}", user.getId());
        return generatedKey;
    }

    @Transactional
    public GeneratedApiKey regenerateKeyForUser(User user) {
        apiKeyRepository.findByUser_Id(user.getId()).ifPresent(apiKeyRepository::delete);
        log.info("Revoked existing API key for user {}", user.getId());
        return generateAndSaveKey(user);
    }

    @Transactional(readOnly = true)
    public Optional<ApiKey> validateApiKey(String fullKey) {
        if (fullKey == null || !fullKey.startsWith(KeyGenerator.PREFIX) || fullKey.length() < 12) {
            return Optional.empty();
        }

        String prefix = fullKey.substring(0, 12);
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByPrefix(prefix);

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
}