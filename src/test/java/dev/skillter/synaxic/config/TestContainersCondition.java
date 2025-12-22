package dev.skillter.synaxic.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.testcontainers.DockerClientFactory;

public class TestContainersCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        try {
            // Return true (load mocks) if Docker is NOT available
            return !DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            // If checking Docker fails, assume it's unavailable and load mocks
            return true;
        }
    }
}

