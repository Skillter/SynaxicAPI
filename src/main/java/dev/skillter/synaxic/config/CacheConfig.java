package dev.skillter.synaxic.config;

import dev.skillter.synaxic.cache.CacheEventPublisher;
import dev.skillter.synaxic.cache.TieredCacheManager;
import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_EMAIL_VALIDATION = "emailValidation";
    public static final String CACHE_GEO_IP = "geoIp";
    public static final String CACHE_API_KEY_BY_PREFIX = "apiKeyByPrefix";
    public static final String CACHE_MX_RECORDS = "mxRecords";
    public static final String CACHE_INVALIDATION_TOPIC = "synaxic:cache:invalidation";

    @Bean("redissonCacheManager")
    public CacheManager redissonCacheManager(RedissonClient redissonClient) {
        Map<String, org.redisson.spring.cache.CacheConfig> config = new HashMap<>();

        config.put(CACHE_EMAIL_VALIDATION, new org.redisson.spring.cache.CacheConfig(Duration.ofHours(24).toMillis(), 0));
        config.put(CACHE_GEO_IP, new org.redisson.spring.cache.CacheConfig(Duration.ofHours(1).toMillis(), 0));
        config.put(CACHE_API_KEY_BY_PREFIX, new org.redisson.spring.cache.CacheConfig(Duration.ofMinutes(30).toMillis(), 0));
        config.put(CACHE_MX_RECORDS, new org.redisson.spring.cache.CacheConfig(Duration.ofHours(24).toMillis(), 0));

        return new RedissonSpringCacheManager(redissonClient, config);
    }

    @Bean
    @Primary
    public CacheManager tieredCacheManager(CacheManager redissonCacheManager, CacheEventPublisher eventPublisher) {
        return new TieredCacheManager(redissonCacheManager, eventPublisher, CACHE_INVALIDATION_TOPIC);
    }
}