package dev.skillter.synaxic.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
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
        };
    }

    private Bandwidth getBandwidthForTier(RateLimitTier tier) {
        return switch (tier) {
            case API_KEY -> Bandwidth.classic(apiKeyCapacity, Refill.intervally(apiKeyCapacity, Duration.ofMinutes(apiKeyRefillMinutes)));
            case STATIC -> Bandwidth.classic(staticCapacity, Refill.intervally(staticCapacity, Duration.ofMinutes(staticRefillMinutes)));
            case ANONYMOUS -> Bandwidth.classic(anonymousCapacity, Refill.intervally(anonymousCapacity, Duration.ofMinutes(anonymousRefillMinutes)));
        };
    }

    public enum RateLimitTier {
        ANONYMOUS, API_KEY, STATIC
    }
}