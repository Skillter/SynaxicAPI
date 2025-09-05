package dev.skillter.synaxic;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.command.CommandAsyncExecutor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("dev")
@Import(SynaxicApplicationTests.TestConfig.class)
class SynaxicApplicationTests {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public RedissonClient redissonClient() {
            Redisson mockRedisson = Mockito.mock(Redisson.class);
            CommandAsyncExecutor mockExecutor = Mockito.mock(CommandAsyncExecutor.class);
            when(mockRedisson.getCommandExecutor()).thenReturn(mockExecutor);
            return mockRedisson;
        }
    }

    @Test
    void contextLoads() {
    }

}