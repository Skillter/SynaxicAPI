package dev.skillter.synaxic.config;

import dev.skillter.synaxic.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listener that initializes API request metrics from database on application startup.
 * The metric counter is now persisted in PostgreSQL.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MetricsStartupListener {

    private final MetricsService metricsService;

    /**
     * Called after the application is fully started.
     * Initializes metrics from the database.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        long totalRequests = metricsService.getTotalApiRequests();
        log.info("API metrics initialized - Total requests: {}", totalRequests);
    }
}
