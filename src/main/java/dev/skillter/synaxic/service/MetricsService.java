package dev.skillter.synaxic.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;

    private static final String METRIC_API_REQUESTS_TOTAL = "synaxic.api.requests.total";
    private static final String METRIC_API_RESPONSE_TIME = "synaxic.api.response.time.seconds";
    private static final String METRIC_API_ERRORS_TOTAL = "synaxic.api.errors.total";

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
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