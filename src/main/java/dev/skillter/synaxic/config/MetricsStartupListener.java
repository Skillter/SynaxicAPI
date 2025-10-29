package dev.skillter.synaxic.config;

import dev.skillter.synaxic.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listener that restores API request metrics from Redis on application startup.
 * Ensures that the metric counter persists across app restarts.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MetricsStartupListener {

    private final MetricsService metricsService;

    /**
     * Called after the application is fully started.
     * Restores the in-memory metrics counter from Redis.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Restoring API metrics from Redis...");
        metricsService.restoreCounterFromRedis();
    }
}
