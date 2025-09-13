package dev.skillter.synaxic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableRedisHttpSession
public class HttpSessionConfig {

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SYNAXIC_SESSION");
        serializer.setCookiePath("/");
        serializer.setDomainNamePattern("^.+?\\.(\\w+\\.[a-z]+)$");
        return serializer;
    }
}```

        #### **File: `src/main/java/dev/skillter/synaxic/config/RateLimitConfig.java`**

        ```java
package dev.skillter.synaxic.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

@Configuration
public class RateLimitConfig {

    @Bean
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(
            @Value("${synaxic.redis.mode:singleserver}") String redisMode,
            @Value("${synaxic.redis.address}") String redisAddress,
            @Value("${synaxic.redis.password:#{null}}") String redisPassword,
            @Value("${synaxic.redis.sentinel.master-name:#{null}}") String sentinelMasterName,
            @Value("${synaxic.redis.sentinel.nodes:#{null}}") String sentinelNodes,
            @Value("${synaxic.redis.cluster.nodes:#{null}}") String clusterNodes
    ) {
        Config config = new Config();

        switch (redisMode.toLowerCase()) {
            case "sentinel":
                if (!StringUtils.hasText(sentinelMasterName) || !StringUtils.hasText(sentinelNodes)) {
                    throw new IllegalArgumentException("Sentinel mode requires 'master-name' and 'nodes' to be set.");
                }
                var sentinelConfig = config.useSentinelServers()
                        .setMasterName(sentinelMasterName)
                        .addSentinelAddress(formatAddresses(sentinelNodes));
                if (StringUtils.hasText(redisPassword)) {
                    sentinelConfig.setPassword(redisPassword);
                }
                break;

            case "cluster":
                if (!StringUtils.hasText(clusterNodes)) {
                    throw new IllegalArgumentException("Cluster mode requires 'nodes' to be set.");
                }
                var clusterConfig = config.useClusterServers()
                        .addNodeAddress(formatAddresses(clusterNodes));
                if (StringUtils.hasText(redisPassword)) {
                    clusterConfig.setPassword(redisPassword);
                }
                break;

            default:
                var serverConfig = config.useSingleServer()
                        .setAddress(redisAddress);
                if (StringUtils.hasText(redisPassword)) {
                    serverConfig.setPassword(redisPassword);
                }
                break;
        }

        return Redisson.create(config);
    }

    private String[] formatAddresses(String addresses) {
        return Arrays.stream(addresses.split(","))
                .map(String::trim)
                .map(address -> address.startsWith("redis://") ? address : "redis://" + address)
                .toArray(String[]::new);
    }

    @Bean
    public ProxyManager<String> proxyManager(RedissonClient redissonClient) {
        return Bucket4jRedisson.casBasedBuilder(redissonClient.getCommandExecutor())
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                                Duration.ofHours(1)
                        )
                )
                .build();
    }
}