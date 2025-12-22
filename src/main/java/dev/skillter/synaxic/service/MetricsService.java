package dev.skillter.synaxic.service;

import dev.skillter.synaxic.model.entity.ApiStats;
import dev.skillter.synaxic.repository.ApiStatsRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class MetricsService {

    private final MeterRegistry meterRegistry;
    private final ApiStatsRepository apiStatsRepository;
    
    private final AtomicLong pendingRequestCount = new AtomicLong(0);

    private static final String METRIC_API_REQUESTS_TOTAL = "synaxic.api.requests.total";
    private static final String METRIC_API_RESPONSE_TIME = "synaxic.api.response.time.seconds";
    private static final String METRIC_API_ERRORS_TOTAL = "synaxic.api.errors.total";
    private static final String COUNTER_DB_NAME = "total_api_requests";

    public MetricsService(MeterRegistry meterRegistry, ApiStatsRepository apiStatsRepository) {
        this.meterRegistry = meterRegistry;
        this.apiStatsRepository = apiStatsRepository;
    }

    @PostConstruct
    public void init() {
        try {
            long dbValue = getTotalApiRequests();
            log.info("Initializing metrics. Total historical requests: {}", dbValue);
        } catch (Exception e) {
            log.warn("Could not initialize metrics from DB: {}", e.getMessage());
        }
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

        pendingRequestCount.incrementAndGet();
    }

    @Scheduled(fixedRate = 5000)
    @Transactional
    public void flushMetricsToDatabase() {
        long delta = pendingRequestCount.getAndSet(0);
        if (delta > 0) {
            try {
                apiStatsRepository.findByCounterName(COUNTER_DB_NAME)
                        .ifPresentOrElse(stats -> {
                            stats.setCounterValue(stats.getCounterValue() + delta);
                            apiStatsRepository.save(stats);
                        }, () -> {
                            ApiStats stats = ApiStats.builder()
                                    .counterName(COUNTER_DB_NAME)
                                    .counterValue(delta)
                                    .build();
                            apiStatsRepository.save(stats);
                        });
            } catch (Exception e) {
                pendingRequestCount.addAndGet(delta);
                log.error("Failed to flush metrics to database", e);
            }
        }
    }

    @Transactional(readOnly = true)
    public long getTotalApiRequests() {
        try {
            long dbValue = apiStatsRepository.findByCounterName(COUNTER_DB_NAME)
                    .map(ApiStats::getCounterValue)
                    .orElse(0L);
            return dbValue + pendingRequestCount.get();
        } catch (Exception e) {
            return pendingRequestCount.get();
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

