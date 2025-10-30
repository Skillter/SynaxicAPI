package dev.skillter.synaxic.service;

import dev.skillter.synaxic.model.dto.*;
import dev.skillter.synaxic.repository.ApiKeyRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
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
    private final ApiKeyRepository apiKeyRepository;

    private static final String METRIC_REQUESTS_TOTAL = "synaxic.api.requests.total";
    private static final String METRIC_RESPONSE_TIME = "synaxic.api.response.time.seconds";
    private static final String TAG_ENDPOINT = "endpoint";
    private static final String TAG_API_KEY_PREFIX = "apiKeyPrefix";
    private static final String TAG_GEO_COUNTRY = "geoCountry";

    public AnalyticsResponse getAnalytics() {
        RequestStats requestStats = getRequestStats();

        return AnalyticsResponse.builder()
                .uptime(getUptime())
                .requests(requestStats)
                .latency(getLatencyStats())
                .breakdowns(getBreakdowns())
                .rates(getRateStats(requestStats))
                .cache(getCacheStats())
                .serviceBreakdown(getServiceBreakdown())
                .responseTime(getResponseTimeStats())
                .apiKeys(getApiKeyStats())
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

    private RateStats getRateStats(RequestStats requestStats) {
        // Calculate requests per minute based on uptime
        Duration uptime = Duration.between(applicationStartTime, Instant.now());
        double uptimeMinutes = Math.max(uptime.toMillis() / 60000.0, 1.0); // Avoid division by zero
        double requestsPerMinute = requestStats.getTotal() / uptimeMinutes;

        // Calculate error rate
        long totalErrors = requestStats.getClientErrorCount() + requestStats.getServerErrorCount();
        double errorRate = requestStats.getTotal() > 0
            ? (totalErrors * 100.0 / requestStats.getTotal())
            : 0.0;

        double successRate = requestStats.getTotal() > 0
            ? ((requestStats.getTotal() - totalErrors) * 100.0 / requestStats.getTotal())
            : 100.0;

        return RateStats.builder()
                .requestsPerMinute(requestsPerMinute)
                .errorRatePercent(errorRate)
                .successRatePercent(successRate)
                .build();
    }

    private CacheStats getCacheStats() {
        try {
            // Try to get cache hit/miss metrics from Micrometer
            Counter cacheGets = meterRegistry.find("cache.gets").tag("result", "hit").counter();
            Counter cacheMisses = meterRegistry.find("cache.gets").tag("result", "miss").counter();

            long hits = cacheGets != null ? (long) cacheGets.count() : 0;
            long misses = cacheMisses != null ? (long) cacheMisses.count() : 0;
            long total = hits + misses;

            double hitRate = total > 0 ? (hits * 100.0 / total) : 0.0;

            return CacheStats.builder()
                    .hitRatePercent(hitRate)
                    .hits(hits)
                    .misses(misses)
                    .totalRequests(total)
                    .build();
        } catch (Exception e) {
            // Return empty stats if cache metrics not available
            return CacheStats.builder()
                    .hitRatePercent(0.0)
                    .hits(0)
                    .misses(0)
                    .totalRequests(0)
                    .build();
        }
    }

    private ServiceBreakdown getServiceBreakdown() {
        Collection<Meter> requestMeters = meterRegistry.find(METRIC_REQUESTS_TOTAL).meters();

        long ipRequests = 0;
        long emailRequests = 0;
        long converterRequests = 0;
        long otherRequests = 0;

        for (Meter meter : requestMeters) {
            String endpoint = meter.getId().getTag(TAG_ENDPOINT);
            if (endpoint != null && meter instanceof Counter counter) {
                long count = (long) counter.count();
                if (endpoint.contains("/v1/ip")) {
                    ipRequests += count;
                } else if (endpoint.contains("/v1/email") || endpoint.contains("/v1/validate")) {
                    emailRequests += count;
                } else if (endpoint.contains("/v1/convert")) {
                    converterRequests += count;
                } else {
                    otherRequests += count;
                }
            }
        }

        return ServiceBreakdown.builder()
                .ipInspectorRequests(ipRequests)
                .emailValidatorRequests(emailRequests)
                .unitConverterRequests(converterRequests)
                .otherRequests(otherRequests)
                .build();
    }

    private ResponseTimeStats getResponseTimeStats() {
        Timer timer = meterRegistry.find(METRIC_RESPONSE_TIME).timer();
        if (timer == null) {
            return ResponseTimeStats.builder()
                    .minMs(0.0)
                    .avgMs(0.0)
                    .maxMs(0.0)
                    .count(0)
                    .build();
        }

        double avg = timer.mean(TimeUnit.MILLISECONDS);
        double max = timer.max(TimeUnit.MILLISECONDS);
        long count = timer.count();

        // Note: Micrometer doesn't track min value by default, so we'll use 0 or calculate from percentiles
        double min = 0.0;
        try {
            // Try to get P0 (minimum) from snapshot if available
            HistogramSnapshot snapshot = timer.takeSnapshot();
            ValueAtPercentile[] percentiles = snapshot.percentileValues();
            if (percentiles.length > 0) {
                min = Arrays.stream(percentiles)
                        .mapToDouble(p -> p.value(TimeUnit.MILLISECONDS))
                        .min()
                        .orElse(0.0);
            }
        } catch (Exception e) {
            // If percentiles not available, min stays 0
        }

        return ResponseTimeStats.builder()
                .minMs(min)
                .avgMs(avg)
                .maxMs(max)
                .count(count)
                .build();
    }

    private ApiKeyStats getApiKeyStats() {
        // Get total API keys from database
        long totalKeys = apiKeyRepository.count();

        // Count unique API keys used (from metrics)
        Collection<Meter> requestMeters = meterRegistry.find(METRIC_REQUESTS_TOTAL).meters();
        long activeKeys = requestMeters.stream()
                .map(Meter::getId)
                .map(id -> id.getTag(TAG_API_KEY_PREFIX))
                .filter(prefix -> prefix != null && !prefix.equals("anonymous"))
                .distinct()
                .count();

        // Count anonymous requests
        long anonymousRequests = requestMeters.stream()
                .filter(meter -> {
                    String prefix = meter.getId().getTag(TAG_API_KEY_PREFIX);
                    return "anonymous".equals(prefix);
                })
                .mapToLong(meter -> (long) ((Counter) meter).count())
                .sum();

        return ApiKeyStats.builder()
                .totalKeys(totalKeys)
                .activeKeysLast24h(activeKeys)
                .anonymousRequests(anonymousRequests)
                .build();
    }
}