package dev.skillter.synaxic.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.config.SslProvider;
import org.redisson.config.SslVerificationMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;

@Configuration
public class RateLimitConfig {

    @Bean(destroyMethod = "shutdown")
    @Profile("prod")
    public RedissonClient redissonClientProd(
            @Value("${spring.data.redis.master.host}") String redisMasterHost,
            @Value("${spring.data.redis.port}") int redisPort,
            @Value("${spring.data.redis.password}") String redisPassword,
            @Value("${synaxic.redis.read-mode:MASTER}") String readMode,
            @Value("${spring.data.redis.slave-hosts:}") String[] slaveHosts,
            @Value("file:/app/redis-tls/truststore.p12") Resource trustStore
    ) throws Exception {
        Config config = new Config();
        String masterAddress = "rediss://" + redisMasterHost + ":" + redisPort;

        MasterSlaveServersConfig masterSlaveConfig = config.useMasterSlaveServers()
                .setMasterAddress(masterAddress)
                .setPassword(redisPassword)
                .setSslProvider(SslProvider.JDK)
                .setSslTruststore(trustStore.getURL())
                .setSslTruststorePassword("changeit")
                .setSslVerificationMode(SslVerificationMode.STRICT)
                .setMasterConnectionPoolSize(64)
                .setSlaveConnectionPoolSize(64)
                .setMasterConnectionMinimumIdleSize(24)
                .setSlaveConnectionMinimumIdleSize(24)
                .setSubscriptionConnectionPoolSize(50)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        if (slaveHosts != null && slaveHosts.length > 0) {
            String[] formattedSlaveHosts = Arrays.stream(slaveHosts)
                    .filter(StringUtils::hasText)
                    .map(host -> host.startsWith("rediss://") ? host : "rediss://" + host)
                    .toArray(String[]::new);
            if (formattedSlaveHosts.length > 0) {
                masterSlaveConfig.addSlaveAddress(formattedSlaveHosts);
            }
        }

        if ("SLAVE".equalsIgnoreCase(readMode)) {
            masterSlaveConfig.setReadMode(ReadMode.SLAVE);
        } else {
            masterSlaveConfig.setReadMode(ReadMode.MASTER);
        }

        return Redisson.create(config);
    }

    @Bean(destroyMethod = "shutdown")
    @Profile("!prod")
    public RedissonClient redissonClientDev(
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${spring.data.redis.port:6379}") int redisPort
    ) {
        Config config = new Config();
        String redisAddress = "redis://" + redisHost + ":" + redisPort;
        config.useSingleServer().setAddress(redisAddress);
        return Redisson.create(config);
    }

    @Bean
    public ProxyManager<String> proxyManager(RedissonClient redissonClient) {
        Redisson redisson = (Redisson) redissonClient;
        return Bucket4jRedisson.casBasedBuilder(redisson.getCommandExecutor())
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                                Duration.ofSeconds(10)
                        )
                )
                .build();
    }
}