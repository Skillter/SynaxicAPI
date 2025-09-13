package dev.skillter.synaxic.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class TieredCacheManager implements CacheManager {

    private final CacheManager l2CacheManager;
    private final CacheEventPublisher eventPublisher;
    private final String invalidationTopic;
    private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>(16);
    private final Set<String> cacheNames;

    public TieredCacheManager(CacheManager l2CacheManager, CacheEventPublisher eventPublisher, String invalidationTopic) {
        this.l2CacheManager = l2CacheManager;
        this.eventPublisher = eventPublisher;
        this.invalidationTopic = invalidationTopic;
        this.cacheNames = ConcurrentHashMap.newKeySet();
        this.cacheNames.addAll(l2CacheManager.getCacheNames());
    }

    @Override
    public Cache getCache(@NonNull String name) {
        return this.cacheMap.computeIfAbsent(name, this::createTieredCache);
    }

    private TieredCache createTieredCache(String name) {
        com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();

        org.springframework.cache.Cache redissonCache = l2CacheManager.getCache(name);
        if (redissonCache == null) {
            throw new IllegalArgumentException("Cannot find cache named '" + name + "' in L2 cache manager");
        }

        cacheNames.add(name);
        return new TieredCache(name, caffeineCache, redissonCache, eventPublisher, invalidationTopic, true);
    }

    @Override
    @NonNull
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(this.cacheNames);
    }

    public void evictLocalCache(String cacheName, Object key) {
        Cache cache = cacheMap.get(cacheName);
        if (cache instanceof TieredCache tieredCache) {
            com.github.benmanes.caffeine.cache.Cache<Object, Object> l1Cache =
                    (com.github.benmanes.caffeine.cache.Cache<Object, Object>) tieredCache.getNativeCache();
            if (key == null) {
                l1Cache.invalidateAll();
            } else {
                l1Cache.invalidate(key);
            }
        }
    }
}