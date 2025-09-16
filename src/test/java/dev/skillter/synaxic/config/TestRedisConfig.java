package dev.skillter.synaxic.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestRedisConfig {

    @Bean(destroyMethod = "shutdown")
    @Primary
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host}") String redisHost,
            @Value("${spring.data.redis.port}") int redisPort
    ) {
        Config config = new Config();
        String redisAddress = "redis://" + redisHost + ":" + redisPort;
        config.useSingleServer().setAddress(redisAddress);
        return Redisson.create(config);
    }
}