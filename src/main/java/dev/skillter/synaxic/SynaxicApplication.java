package dev.skillter.synaxic;

import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {RedissonAutoConfigurationV2.class})
@EnableCaching
@EnableAsync
public class SynaxicApplication {
    public static void main(String[] args) {
        SpringApplication.run(SynaxicApplication.class, args);
    }
}