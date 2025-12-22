package dev.skillter.synaxic.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    public ProxyManager<String> proxyManager(RedissonClient redissonClient) {
        // Safe check: Only create real ProxyManager if we have a real Redisson instance.
        if (redissonClient instanceof Redisson redisson) {
            return Bucket4jRedisson.casBasedBuilder(redisson.getCommandExecutor())
                    .expirationAfterWrite(
                            ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                                    Duration.ofSeconds(10)
                            )
                    )
                    .build();
        }
        return null;
    }
}

