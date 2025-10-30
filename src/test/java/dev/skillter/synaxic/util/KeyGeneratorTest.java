package dev.skillter.synaxic.util;

import dev.skillter.synaxic.model.dto.GeneratedApiKey;
import dev.skillter.synaxic.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class KeyGeneratorTest {

    private KeyGenerator keyGenerator;
    private User testUser;

    @BeforeEach
    void setUp() {
        keyGenerator = new KeyGenerator();
        testUser = User.builder()
                .id(1L)
                .googleSub("google123")
                .email("test@example.com")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void generate_ShouldReturnValidGeneratedApiKey() {
        GeneratedApiKey result = keyGenerator.generate(testUser);

        assertThat(result).isNotNull();
        assertThat(result.fullKey()).isNotNull();
        assertThat(result.apiKey()).isNotNull();
    }

    @Test
    void generate_ShouldStartWithCorrectPrefix() {
        GeneratedApiKey result = keyGenerator.generate(testUser);

        assertThat(result.fullKey()).startsWith("syn_live_");
        assertThat(result.apiKey().getPrefix()).startsWith("syn_live_");
    }

    @Test
    void generate_ShouldHaveCorrectLength() {
        GeneratedApiKey result = keyGenerator.generate(testUser);

        // syn_live_ (9 chars) + base64-encoded 32 bytes (43 chars without padding) = 52 total
        assertThat(result.fullKey().length()).isGreaterThanOrEqualTo(50);
    }

    @Test
    void generate_ShouldHaveCorrectPrefixLength() {
        GeneratedApiKey result = keyGenerator.generate(testUser);

        // Prefix should be first 12 characters
        assertThat(result.apiKey().getPrefix()).hasSize(12);
    }

    @Test
    void generate_ShouldGenerateUniqueKeys() {
        GeneratedApiKey key1 = keyGenerator.generate(testUser);
        GeneratedApiKey key2 = keyGenerator.generate(testUser);

        assertThat(key1.fullKey()).isNotEqualTo(key2.fullKey());
        assertThat(key1.apiKey().getKeyHash()).isNotEqualTo(key2.apiKey().getKeyHash());
    }

    @Test
    void generate_ShouldSetUserReference() {
        GeneratedApiKey result = keyGenerator.generate(testUser);

        assertThat(result.apiKey().getUser()).isEqualTo(testUser);
    }

    @Test
    void generate_ShouldHashKey() {
        GeneratedApiKey result = keyGenerator.generate(testUser);

        assertThat(result.apiKey().getKeyHash()).isNotNull();
        assertThat(result.apiKey().getKeyHash()).isNotEqualTo(result.fullKey());
        // SHA-256 hash should be 64 characters (hex)
        assertThat(result.apiKey().getKeyHash()).hasSize(64);
    }

    @Test
    void calculateSha256_ShouldReturnConsistentHash() {
        String input = "test_input_string";

        String hash1 = keyGenerator.calculateSha256(input);
        String hash2 = keyGenerator.calculateSha256(input);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }

    @Test
    void calculateSha256_ShouldReturnDifferentHashesForDifferentInputs() {
        String input1 = "test_input_1";
        String input2 = "test_input_2";

        String hash1 = keyGenerator.calculateSha256(input1);
        String hash2 = keyGenerator.calculateSha256(input2);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void calculateSha256_ShouldOnlyContainHexCharacters() {
        String input = "test_input";

        String hash = keyGenerator.calculateSha256(input);

        assertThat(hash).matches("^[a-f0-9]+$");
    }
}
