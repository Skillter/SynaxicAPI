package dev.skillter.synaxic.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class MetricsService {

    private final MeterRegistry meterRegistry;
    private final RedissonClient redissonClient;
    private static final String REDIS_API_REQUESTS_KEY = "metrics:api:requests:total";

    private static final String METRIC_API_REQUESTS_TOTAL = "synaxic.api.requests.total";
    private static final String METRIC_API_RESPONSE_TIME = "synaxic.api.response.time.seconds";
    private static final String METRIC_API_ERRORS_TOTAL = "synaxic.api.errors.total";

    public MetricsService(MeterRegistry meterRegistry, RedissonClient redissonClient) {
        this.meterRegistry = meterRegistry;
        this.redissonClient = redissonClient;
    }

    public void incrementApiRequest(String endpoint, String method, int statusCode, String apiKeyPrefix, String geoCountry) {
        Counter.builder(METRIC_API_REQUESTS_TOTAL)
                .description("Total number of API requests processed")
                .tags(
                        "endpoint", endpoint,
                        "method", method,
                        "status", String.valueOf(statusCode),
                        "apiKeyPrefix", apiKeyPrefix,
                        "geoCountry", geoCountry
                )
                .register(meterRegistry)
                .increment();

        // Also increment persistent Redis counter
        try {
            RAtomicLong redisCounter = redissonClient.getAtomicLong(REDIS_API_REQUESTS_KEY);
            redisCounter.incrementAndGet();
        } catch (Exception e) {
            log.warn("Failed to increment Redis metrics counter", e);
        }
    }

    /**
     * Returns the total API request count from Redis (persistent across restarts).
     */
    public long getTotalApiRequests() {
        try {
            RAtomicLong redisCounter = redissonClient.getAtomicLong(REDIS_API_REQUESTS_KEY);
            return redisCounter.get();
        } catch (Exception e) {
            log.warn("Failed to get Redis metrics counter", e);
            return 0;
        }
    }

    /**
     * Initializes the in-memory counter from Redis on startup.
     */
    public void restoreCounterFromRedis() {
        try {
            long redisCount = getTotalApiRequests();
            if (redisCount > 0) {
                // Initialize the in-memory counter to match Redis value
                // This is a workaround since Micrometer counters can't be set directly
                // We'll increment the counter to match Redis value
                long currentCount = getCurrentInMemoryCount();
                long difference = redisCount - currentCount;
                if (difference > 0) {
                    for (long i = 0; i < difference; i++) {
                        Counter.builder(METRIC_API_REQUESTS_TOTAL)
                                .description("Total number of API requests processed")
                                .tags("source", "redis-restore")
                                .register(meterRegistry)
                                .increment();
                    }
                    log.info("Restored metrics from Redis: {} total API requests", redisCount);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to restore metrics from Redis", e);
        }
    }

    /**
     * Gets the current in-memory counter value from Micrometer.
     */
    private long getCurrentInMemoryCount() {
        try {
            return meterRegistry.find(METRIC_API_REQUESTS_TOTAL)
                    .counters()
                    .stream()
                    .mapToLong(c -> (long) c.count())
                    .sum();
        } catch (Exception e) {
            return 0;
        }
    }

    public void recordResponseTime(String endpoint, String method, Duration duration) {
        Timer.builder(METRIC_API_RESPONSE_TIME)
                .description("API request response time")
                .tags("endpoint", endpoint, "method", method)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(duration);
    }

    public void incrementErrorCount(int statusCode) {
        Counter.builder(METRIC_API_ERRORS_TOTAL)
                .description("Total number of error responses")
                .tags("status", String.valueOf(statusCode))
                .register(meterRegistry)
                .increment();
    }
}