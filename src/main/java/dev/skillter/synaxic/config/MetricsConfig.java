package dev.skillter.synaxic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

@Configuration
public class MetricsConfig {

    @Bean
    public Instant applicationStartTime() {
        return Instant.now();
    }
}