package dev.skillter.synaxic.cache;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CacheEventPublisher {

    private final RedissonClient redissonClient;

    public void publish(String topic, CacheEvent event) {
        RTopic rTopic = redissonClient.getTopic(topic);
        rTopic.publish(event);
    }
}