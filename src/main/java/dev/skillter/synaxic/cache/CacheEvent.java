package dev.skillter.synaxic.cache;

import java.io.Serializable;

public record CacheEvent(String cacheName, Object key) implements Serializable {
    private static final long serialVersionUID = 1L;
}