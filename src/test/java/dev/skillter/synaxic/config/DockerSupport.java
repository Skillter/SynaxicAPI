package dev.skillter.synaxic.config;

import org.testcontainers.DockerClientFactory;

public class DockerSupport {

    private static final boolean IS_DOCKER_AVAILABLE;

    static {
        boolean available = false;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            // Docker is not available or Testcontainers failed to initialize
            System.err.println("Docker check failed: " + ex.getMessage());
        }
        IS_DOCKER_AVAILABLE = available;
    }

    public static boolean isAvailable() {
        return IS_DOCKER_AVAILABLE;
    }
}

