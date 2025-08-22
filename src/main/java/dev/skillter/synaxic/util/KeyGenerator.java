package dev.skillter.synaxic.util;

import dev.skillter.synaxic.model.dto.GeneratedApiKey;
import dev.skillter.synaxic.model.entity.ApiKey;
import dev.skillter.synaxic.model.entity.User;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class KeyGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PREFIX = "syn_live_";
    private static final int KEY_LENGTH = 32;

    public GeneratedApiKey generate(User user) {
        byte[] randomBytes = new byte[KEY_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String fullKey = PREFIX + key;

        String keyHash = calculateSha256(fullKey);
        String prefix = fullKey.substring(0, 12);

        ApiKey apiKeyEntity = ApiKey.builder()
                .user(user)
                .prefix(prefix)
                .keyHash(keyHash)
                .build();

        return new GeneratedApiKey(fullKey, apiKeyEntity);
    }

    public String calculateSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}