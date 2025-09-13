package dev.skillter.synaxic.cache;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CacheInvalidationListener {

    private final RedissonClient redissonClient;
    @Qualifier("tieredCacheManager")
    private final CacheManager cacheManager;

    @PostConstruct
    public void init() {
        RTopic topic = redissonClient.getTopic(CacheConfig.CACHE_INVALIDATION_TOPIC);
        topic.addListener(CacheEvent.class, (channel, event) -> {
            log.debug("Received cache invalidation event for cache '{}', key '{}'", event.cacheName(), event.key());
            if (cacheManager instanceof TieredCacheManager tieredCacheManager) {
                tieredCacheManager.evictLocalCache(event.cacheName(), event.key());
            }
        });
    }
}