package dev.skillter.synaxic.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.lang.NonNull;

import java.util.concurrent.Callable;

public class TieredCache extends AbstractValueAdaptingCache {

    private final String name;
    private final Cache<Object, Object> l1Cache;
    private final org.springframework.cache.Cache l2Cache;
    private final CacheEventPublisher eventPublisher;
    private final String invalidationTopic;

    public TieredCache(String name,
                       Cache<Object, Object> l1Cache,
                       org.springframework.cache.Cache l2Cache,
                       CacheEventPublisher eventPublisher,
                       String invalidationTopic,
                       boolean allowNullValues) {
        super(allowNullValues);
        this.name = name;
        this.l1Cache = l1Cache;
        this.l2Cache = l2Cache;
        this.eventPublisher = eventPublisher;
        this.invalidationTopic = invalidationTopic;
    }

    @Override
    @NonNull
    protected Object lookup(@NonNull Object key) {
        Object value = l1Cache.getIfPresent(key);
        if (value != null) {
            return value;
        }

        ValueWrapper valueWrapper = l2Cache.get(key);
        if (valueWrapper != null) {
            Object l2Value = valueWrapper.get();
            if (l2Value != null) {
                l1Cache.put(key, l2Value);
            }
            return l2Value;
        }

        return null;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public Object getNativeCache() {
        return l1Cache;
    }

    public Cache<Object, Object> getL1Cache() {
        return l1Cache;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(@NonNull Object key, @NonNull Callable<T> valueLoader) {
        return (T) fromStoreValue(l1Cache.get(key, k -> {
            ValueWrapper l2Value = l2Cache.get(k);
            if (l2Value != null && l2Value.get() != null) {
                return l2Value.get();
            }

            try {
                T loadedValue = valueLoader.call();
                if (loadedValue != null) {
                    l2Cache.put(k, loadedValue);
                }
                return loadedValue;
            } catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            }
        }));
    }

    @Override
    public void put(@NonNull Object key, Object value) {
        l2Cache.put(key, value);
        eventPublisher.publish(invalidationTopic, new CacheEvent(name, key));
        l1Cache.put(key, toStoreValue(value));
    }

    @Override
    public void evict(@NonNull Object key) {
        l2Cache.evict(key);
        eventPublisher.publish(invalidationTopic, new CacheEvent(name, key));
        l1Cache.invalidate(key);
    }

    @Override
    public void clear() {
        l2Cache.clear();
        eventPublisher.publish(invalidationTopic, new CacheEvent(name, null));
        l1Cache.invalidateAll();
    }
}