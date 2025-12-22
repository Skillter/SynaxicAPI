package dev.skillter.synaxic.service;

import dev.skillter.synaxic.model.dto.RateLimitStatus;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final ProxyManager<String> proxyManager;

    @Value("${synaxic.rate-limit.anonymous.capacity:100}")
    private long anonymousCapacity;

    @Value("${synaxic.rate-limit.anonymous.refill-minutes:60}")
    private long anonymousRefillMinutes;

    @Value("${synaxic.rate-limit.api-key.capacity:1000}")
    private long apiKeyCapacity;

    @Value("${synaxic.rate-limit.api-key.refill-minutes:60}")
    private long apiKeyRefillMinutes;

    @Value("${synaxic.rate-limit.static.capacity:50000}")
    private long staticCapacity;

    @Value("${synaxic.rate-limit.static.refill-minutes:60}")
    private long staticRefillMinutes;

    @Value("${synaxic.rate-limit.account.capacity:10000}")
    private long accountCapacity;

    @Value("${synaxic.rate-limit.account.refill-minutes:60}")
    private long accountRefillMinutes;

    public Bucket resolveBucket(String key, RateLimitTier tier) {
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(getBandwidthForTier(tier))
                .build();
        return proxyManager.builder().build(key, () -> configuration);
    }

    public long getLimit(RateLimitTier tier) {
        return switch (tier) {
            case API_KEY -> apiKeyCapacity;
            case STATIC -> staticCapacity;
            case ANONYMOUS -> anonymousCapacity;
            case ACCOUNT -> accountCapacity;
        };
    }

    public RateLimitStatus getStatus(String key, RateLimitTier tier) {
        Bucket bucket = resolveBucket(key, tier);
        // Get available tokens without consuming any
        long availableTokens = bucket.getAvailableTokens();

        return RateLimitStatus.builder()
                .key(key)
                .tier(tier)
                .limit(getLimit(tier))
                .remainingTokens(availableTokens)
                .isConsumed(true) // Status check doesn't consume tokens
                .build();
    }

    private Bandwidth getBandwidthForTier(RateLimitTier tier) {
        return switch (tier) {
            case API_KEY -> Bandwidth.simple(apiKeyCapacity, Duration.ofMinutes(apiKeyRefillMinutes));
            case STATIC -> Bandwidth.simple(staticCapacity, Duration.ofMinutes(staticRefillMinutes));
            case ANONYMOUS -> Bandwidth.simple(anonymousCapacity, Duration.ofMinutes(anonymousRefillMinutes));
            case ACCOUNT -> Bandwidth.simple(accountCapacity, Duration.ofMinutes(accountRefillMinutes));
        };
    }

    public enum RateLimitTier {
        ANONYMOUS, API_KEY, STATIC, ACCOUNT
    }
}

