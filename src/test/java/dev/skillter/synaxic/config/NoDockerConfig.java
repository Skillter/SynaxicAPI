package dev.skillter.synaxic.config;

import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;

import java.lang.reflect.Constructor;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
@Conditional(TestContainersCondition.class)
public class NoDockerConfig {

    @Bean
    @Primary
    public RedissonClient redissonClient() {
        RedissonClient mockClient = mock(RedissonClient.class);
        
        RAtomicLong mockAtomicLong = mock(RAtomicLong.class);
        when(mockAtomicLong.incrementAndGet()).thenReturn(1L);
        when(mockAtomicLong.get()).thenReturn(0L);
        when(mockClient.getAtomicLong(anyString())).thenReturn(mockAtomicLong);
        
        RTopic mockTopic = mock(RTopic.class);
        when(mockClient.getTopic(anyString())).thenReturn(mockTopic);
        
        RBucket<Object> mockBucket = mock(RBucket.class);
        when(mockBucket.get()).thenReturn(null);
        when(mockClient.getBucket(anyString())).thenReturn(mockBucket);
        
        return mockClient;
    }

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public ProxyManager<String> proxyManager() {
        ProxyManager<String> mockProxyManager = mock(ProxyManager.class);
        RemoteBucketBuilder<String> mockBuilder = mock(RemoteBucketBuilder.class);
        BucketProxy mockBucket = mock(BucketProxy.class);

        when(mockProxyManager.builder()).thenReturn(mockBuilder);
        when(mockBuilder.build(anyString(), any(Supplier.class))).thenReturn(mockBucket);
        
        try {
            // Safe reflection for test compatibility
            Constructor<?>[] constructors = ConsumptionProbe.class.getDeclaredConstructors();
            for (Constructor<?> constructor : constructors) {
                if (constructor.getParameterCount() == 4) {
                    constructor.setAccessible(true);
                    ConsumptionProbe probe = (ConsumptionProbe) constructor.newInstance(true, 100L, 0L, Long.MAX_VALUE);
                    when(mockBucket.tryConsumeAndReturnRemaining(anyLong())).thenReturn(probe);
                    break;
                }
            }
        } catch (Exception e) {
            // Ignore reflection errors in mock setup
        }

        return mockProxyManager;
    }

    @Bean(name = "tieredCacheManager")
    @Primary
    public CacheManager tieredCacheManager() {
        return mock(CacheManager.class);
    }
}

