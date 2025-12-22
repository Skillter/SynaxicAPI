package dev.skillter.synaxic.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.command.CommandAsyncExecutor;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class RateLimitConfig {

    @Bean
    public ProxyManager<String> proxyManager(RedissonClient redissonClient) {
        log.info("Initializing Bucket4j ProxyManager with RedissonClient: {}", redissonClient.getClass().getName());

        RedissonClient unwrappedClient = redissonClient;
        
        // Unwrap Spring proxy if present to access the concrete Redisson instance
        if (AopUtils.isAopProxy(redissonClient)) {
            Object target = AopProxyUtils.getSingletonTarget(redissonClient);
            if (target instanceof RedissonClient) {
                unwrappedClient = (RedissonClient) target;
                log.info("Unwrapped RedissonClient proxy to: {}", unwrappedClient.getClass().getName());
            }
        }

        if (unwrappedClient instanceof Redisson redisson) {
            CommandAsyncExecutor commandExecutor = redisson.getCommandExecutor();
            return Bucket4jRedisson.casBasedBuilder(commandExecutor)
                    .expirationAfterWrite(
                            ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                                    Duration.ofSeconds(60)
                            )
                    )
                    .build();
        } else {
            throw new IllegalStateException("RedissonClient bean must be an instance of org.redisson.Redisson to use Bucket4j Redisson backend. Actual type: " + unwrappedClient.getClass().getName());
        }
    }
}

