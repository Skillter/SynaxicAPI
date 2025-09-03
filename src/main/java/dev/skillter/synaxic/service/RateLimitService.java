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

    public Bucket resolveBucket(String key, boolean isApiKey) {
        Bandwidth limit = isApiKey ? getApiKeyPlan() : getAnonymousPlan();
        return proxyManager.builder().build(key, () -> BucketConfiguration.builder().addLimit(limit).build());
    }

    private Bandwidth getAnonymousPlan() {
        return Bandwidth.classic(anonymousCapacity, Refill.intervally(anonymousCapacity, Duration.ofMinutes(anonymousRefillMinutes)));
    }

    private Bandwidth getApiKeyPlan() {
        return Bandwidth.classic(apiKeyCapacity, Refill.intervally(apiKeyCapacity, Duration.ofMinutes(apiKeyRefillMinutes)));
    }
}