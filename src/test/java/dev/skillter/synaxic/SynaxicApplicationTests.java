package dev.skillter.synaxic;

import dev.skillter.synaxic.config.TestRedisConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
@Import({TestcontainersConfiguration.class, TestRedisConfig.class})
class SynaxicApplicationTests {

    @Test
    void contextLoads() {
    }

}