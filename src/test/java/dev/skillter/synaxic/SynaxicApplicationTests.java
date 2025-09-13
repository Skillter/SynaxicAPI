package dev.skillter.synaxic;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.Redisson;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.springframework.boot.actuate.autoconfigure.metrics.cache.CacheMetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@EnableAutoConfiguration(exclude = {CacheMetricsAutoConfiguration.class})
@ActiveProfiles("dev")
@Import(SynaxicApplicationTests.TestConfig.class)
class SynaxicApplicationTests {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        @SuppressWarnings("unchecked")
        public RedissonClient redissonClient() {
            Redisson mockRedisson = Mockito.mock(Redisson.class);
            CommandAsyncExecutor mockExecutor = Mockito.mock(CommandAsyncExecutor.class);
            RMapCache<Object, Object> mockMapCache = Mockito.mock(RMapCache.class);

            when(mockRedisson.getCommandExecutor()).thenReturn(mockExecutor);
            when(mockRedisson.getMapCache(anyString(), any(Codec.class))).thenReturn(mockMapCache);

            return mockRedisson;
        }
    }

    @Test
    void contextLoads() {
    }

}