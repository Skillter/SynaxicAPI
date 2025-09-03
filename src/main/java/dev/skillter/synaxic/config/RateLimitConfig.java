package dev.skillter.synaxic.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.cas.RedissonBasedProxyManager;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    /**
     * Only create this bean if RedissonClient is not already auto-configured
     * by redisson-spring-boot-starter
     */
    @Bean
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.url:#{null}}") String redisUrl,
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${spring.data.redis.port:6379}") int redisPort,
            @Value("${spring.data.redis.password:#{null}}") String redisPassword
    ) {
        Config config = new Config();
        String redisAddress;
        if (StringUtils.hasText(redisUrl)) {
            redisAddress = redisUrl;
        } else {
            redisAddress = "redis://" + redisHost + ":" + redisPort;
        }

        var serverConfig = config.useSingleServer()
                .setAddress(redisAddress);

        // Set password if provided
        if (StringUtils.hasText(redisPassword)) {
            serverConfig.setPassword(redisPassword);
        }

        // Production-ready settings
        serverConfig
                .setConnectionPoolSize(10)
                .setConnectionMinimumIdleSize(5)
                .setSubscriptionConnectionPoolSize(5)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        return Redisson.create(config);
    }

    @Bean
    public ProxyManager<String> proxyManager(RedissonClient redissonClient) {
        return RedissonBasedProxyManager.builderFor(redissonClient)
                .withExpirationAfterWrite(Duration.ofHours(1))
                .build();
    }
}