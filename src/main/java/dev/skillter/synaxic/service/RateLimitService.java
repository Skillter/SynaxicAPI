package dev.skillter.synaxic.service;

import com.bucket4j.Bandwidth;
import com.bucket4j.Bucket;
import com.bucket4j.Refill;
import com.bucket4j.distributed.proxy.ProxyManager;
import dev.skillter.synaxic.model.entity.ApiKey;
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
    private int anonymousCapacity;

    @Value("${synaxic.rate-limit.anonymous.refill-minutes:60}")
    private int anonymousRefillMinutes;

    @Value("${synaxic.rate-limit.api-key.capacity:1000}")
    private int apiKeyCapacity;

    @Value("${synaxic.rate-limit.api-key.refill-minutes:60}")
    private int apiKeyRefillMinutes;

    public Bucket resolveBucket(String key) {
        return proxyManager.builder().build(key, () -> getRateLimitPlan(key));
    }

    private Bandwidth getRateLimitPlan(String key) {
        // API Keys are associated with a User ID (Long), IPs are strings.
        // This is a simple way to differentiate.
        if (isApiKey(key)) {
            return Bandwidth.classic(apiKeyCapacity, Refill.intervally(apiKeyCapacity, Duration.ofMinutes(apiKeyRefillMinutes)));
        }
        return Bandwidth.classic(anonymousCapacity, Refill.intervally(anonymousCapacity, Duration.ofMinutes(anonymousRefillMinutes)));
    }

    private boolean isApiKey(String key) {
        // A simple heuristic: our API keys are tied to the ApiKey entity's ID, which is a number.
        // IP addresses are not. This can be made more robust if needed.
        try {
            Long.parseLong(key);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}