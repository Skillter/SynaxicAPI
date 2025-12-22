package dev.skillter.synaxic;

import dev.skillter.synaxic.config.NoDockerConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.main.allow-bean-definition-overriding=true"
)
@ActiveProfiles("test")
@Import(NoDockerConfig.class)
public abstract class BaseIntegrationTest {

    static PostgreSQLContainer<?> postgresContainer;
    static GenericContainer<?> redisContainer;
    static boolean dockerAvailable = false;

    static {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                postgresContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));
                redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
                
                postgresContainer.start();
                redisContainer.start();
                dockerAvailable = true;
                System.out.println("✅ Docker containers started successfully.");
            } else {
                System.out.println("⚠️ Docker not available. Using fallback configuration.");
            }
        } catch (Throwable e) {
            System.err.println("❌ Failed to initialize Docker containers: " + e.getMessage());
            dockerAvailable = false;
            stopContainers();
        }
    }

    private static void stopContainers() {
        try {
            if (postgresContainer != null) postgresContainer.stop();
        } catch (Exception ignored) {}
        try {
            if (redisContainer != null) redisContainer.stop();
        } catch (Exception ignored) {}
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        if (dockerAvailable && postgresContainer != null && postgresContainer.isRunning()) {
            registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
            registry.add("spring.datasource.username", postgresContainer::getUsername);
            registry.add("spring.datasource.password", postgresContainer::getPassword);
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        }

        if (dockerAvailable && redisContainer != null && redisContainer.isRunning()) {
            registry.add("spring.data.redis.host", redisContainer::getHost);
            registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
        }
    }
}

