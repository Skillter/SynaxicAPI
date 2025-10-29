package dev.skillter.synaxic.service;

import dev.skillter.synaxic.model.entity.ApiStats;
import dev.skillter.synaxic.repository.ApiStatsRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class MetricsService {

    private final MeterRegistry meterRegistry;
    private final ApiStatsRepository apiStatsRepository;

    private static final String METRIC_API_REQUESTS_TOTAL = "synaxic.api.requests.total";
    private static final String METRIC_API_RESPONSE_TIME = "synaxic.api.response.time.seconds";
    private static final String METRIC_API_ERRORS_TOTAL = "synaxic.api.errors.total";

    public MetricsService(MeterRegistry meterRegistry, ApiStatsRepository apiStatsRepository) {
        this.meterRegistry = meterRegistry;
        this.apiStatsRepository = apiStatsRepository;
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

        // Also increment persistent database counter
        try {
            var stats = apiStatsRepository.findByCounterName("total_api_requests")
                    .orElseGet(() -> ApiStats.builder()
                            .counterName("total_api_requests")
                            .counterValue(0L)
                            .build());
            stats.setCounterValue(stats.getCounterValue() + 1);
            apiStatsRepository.save(stats);
        } catch (Exception e) {
            log.warn("Failed to increment database metrics counter", e);
        }
    }

    /**
     * Returns the total API request count from database (persistent across all restarts).
     */
    public long getTotalApiRequests() {
        try {
            return apiStatsRepository.findByCounterName("total_api_requests")
                    .map(ApiStats::getCounterValue)
                    .orElse(0L);
        } catch (Exception e) {
            log.warn("Failed to get database metrics counter", e);
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