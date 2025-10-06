package dev.skillter.synaxic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableJpaRepositories("dev.skillter.synaxic.repository")
public class SynaxicApplication {
    public static void main(String[] args) {
        SpringApplication.run(SynaxicApplication.class, args);
    }
}