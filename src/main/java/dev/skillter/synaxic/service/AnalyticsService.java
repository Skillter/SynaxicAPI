package dev.skillter.synaxic.service;

import dev.skillter.synaxic.model.dto.AnalyticsResponse;
import dev.skillter.synaxic.model.dto.BreakdownItem;
import dev.skillter.synaxic.model.dto.LatencyStats;
import dev.skillter.synaxic.model.dto.RequestStats;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final MeterRegistry meterRegistry;
    private final Instant applicationStartTime;

    private static final String METRIC_REQUESTS_TOTAL = "synaxic.api.requests.total";
    private static final String METRIC_RESPONSE_TIME = "synaxic.api.response.time.seconds";
    private static final String TAG_ENDPOINT = "endpoint";
    private static final String TAG_API_KEY_PREFIX = "apiKeyPrefix";
    private static final String TAG_GEO_COUNTRY = "geoCountry";

    public AnalyticsResponse getAnalytics() {
        return AnalyticsResponse.builder()
                .uptime(getUptime())
                .requests(getRequestStats())
                .latency(getLatencyStats())
                .breakdowns(getBreakdowns())
                .build();
    }

    private String getUptime() {
        Duration uptime = Duration.between(applicationStartTime, Instant.now());
        return String.format("%dd %dh %dm %ds",
                uptime.toDays(),
                uptime.toHoursPart(),
                uptime.toMinutesPart(),
                uptime.toSecondsPart());
    }

    private RequestStats getRequestStats() {
        long total = 0;
        long successCount = 0;
        long clientErrorCount = 0;
        long serverErrorCount = 0;

        for (Counter counter : meterRegistry.find(METRIC_REQUESTS_TOTAL).counters()) {
            double count = counter.count();
            total += count;
            String status = counter.getId().getTag("status");
            if (status != null) {
                if (status.startsWith("2")) {
                    successCount += count;
                } else if (status.startsWith("4")) {
                    clientErrorCount += count;
                } else if (status.startsWith("5")) {
                    serverErrorCount += count;
                }
            }
        }

        return RequestStats.builder()
                .total(total)
                .successCount(successCount)
                .clientErrorCount(clientErrorCount)
                .serverErrorCount(serverErrorCount)
                .build();
    }

    private LatencyStats getLatencyStats() {
        Timer timer = meterRegistry.find(METRIC_RESPONSE_TIME).timer();
        if (timer == null) {
            return LatencyStats.builder().build();
        }

        ValueAtPercentile[] percentiles = timer.takeSnapshot().percentileValues();
        double p50 = Arrays.stream(percentiles)
                .filter(p -> p.percentile() == 0.5)
                .findFirst()
                .map(p -> p.value(TimeUnit.MILLISECONDS))
                .orElse(0.0);
        double p95 = Arrays.stream(percentiles)
                .filter(p -> p.percentile() == 0.95)
                .findFirst()
                .map(p -> p.value(TimeUnit.MILLISECONDS))
                .orElse(0.0);
        double p99 = Arrays.stream(percentiles)
                .filter(p -> p.percentile() == 0.99)
                .findFirst()
                .map(p -> p.value(TimeUnit.MILLISECONDS))
                .orElse(0.0);

        return LatencyStats.builder()
                .p50_ms(p50)
                .p95_ms(p95)
                .p99_ms(p99)
                .build();
    }

    private Map<String, List<BreakdownItem>> getBreakdowns() {
        Collection<Meter> requestMeters = meterRegistry.find(METRIC_REQUESTS_TOTAL).meters();

        return Map.of(
                "topEndpoints", getTopItemsByTag(requestMeters, TAG_ENDPOINT, 10),
                "topApiKeys", getTopItemsByTag(requestMeters, TAG_API_KEY_PREFIX, 10, "anonymous"),
                "topCountries", getTopItemsByTag(requestMeters, TAG_GEO_COUNTRY, 10, "unknown")
        );
    }

    private List<BreakdownItem> getTopItemsByTag(Collection<Meter> meters, String tagKey, int limit, String... exclusions) {
        List<String> exclusionList = Arrays.asList(exclusions);

        return meters.stream()
                .map(Meter::getId)
                .filter(id -> id.getTag(tagKey) != null && !exclusionList.contains(id.getTag(tagKey)))
                .collect(Collectors.groupingBy(
                        id -> id.getTag(tagKey),
                        Collectors.summingLong(id -> (long) meterRegistry.get(id.getName()).tags(id.getTags()).counter().count())
                ))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(entry -> new BreakdownItem(entry.getKey(), entry.getValue()))
                .toList();
    }
}