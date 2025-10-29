package dev.skillter.synaxic.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RedissonClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsServiceTest {

    private MeterRegistry meterRegistry;
    @Mock
    private RedissonClient redissonClient;
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(meterRegistry, redissonClient);
    }

    @Test
    void incrementApiRequest_shouldCreateAndIncrementCounter() {
        metricsService.incrementApiRequest("/v1/ip", "GET", 200, "anonymous", "US");
        metricsService.incrementApiRequest("/v1/ip", "GET", 200, "anonymous", "US");

        Counter counter = meterRegistry.find("synaxic.api.requests.total")
                .tag("endpoint", "/v1/ip")
                .tag("status", "200")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    void recordResponseTime_shouldRecordDuration() {
        metricsService.recordResponseTime("/v1/whoami", "GET", Duration.ofMillis(150));

        Timer timer = meterRegistry.find("synaxic.api.response.time.seconds")
                .tag("endpoint", "/v1/whoami")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(150.0);
    }

    @Test
    void incrementErrorCount_shouldIncrementErrorCounter() {
        metricsService.incrementErrorCount(404);
        metricsService.incrementErrorCount(500);
        metricsService.incrementErrorCount(500);

        Counter notFoundCounter = meterRegistry.find("synaxic.api.errors.total").tag("status", "404").counter();
        Counter serverErrorCounter = meterRegistry.find("synaxic.api.errors.total").tag("status", "500").counter();

        assertThat(notFoundCounter).isNotNull();
        assertThat(serverErrorCounter).isNotNull();
        assertThat(notFoundCounter.count()).isEqualTo(1.0);
        assertThat(serverErrorCounter.count()).isEqualTo(2.0);
    }
}