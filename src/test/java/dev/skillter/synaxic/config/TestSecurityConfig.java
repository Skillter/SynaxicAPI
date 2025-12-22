package dev.skillter.synaxic.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.skillter.synaxic.security.ApiKeyAuthFilter;
import dev.skillter.synaxic.security.OAuth2LoginSuccessHandler;
import dev.skillter.synaxic.security.RateLimitFilter;
import dev.skillter.synaxic.service.UserService;
import dev.skillter.synaxic.util.RequestLoggingInterceptor;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.mockito.Mockito.mock;

@TestConfiguration
@SuppressWarnings("unchecked")
public class TestSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                    .frameOptions(frameOptions -> frameOptions.deny())
                )
                .build();
    }

    @Bean
    @Primary
    public ApiKeyAuthFilter apiKeyAuthFilter() {
        return mock(ApiKeyAuthFilter.class);
    }

    @Bean
    @Primary
    public OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler() {
        return mock(OAuth2LoginSuccessHandler.class);
    }

    @Bean
    @Primary
    public RateLimitFilter rateLimitFilter() {
        return mock(RateLimitFilter.class);
    }

    @Bean
    @Primary
    public ProxyManager<String> proxyManager() {
        return mock(ProxyManager.class);
    }

    @Bean
    @Primary
    public RequestLoggingInterceptor requestLoggingInterceptor() {
        return mock(RequestLoggingInterceptor.class);
    }

    @Bean
    @Primary
    public CorsConfigurationSource corsConfigurationSource() {
        return mock(CorsConfigurationSource.class);
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        // Use a real ObjectMapper instead of a mock to prevent NPEs in JSON providers
        return Jackson2ObjectMapperBuilder.json()
                .modules(new JavaTimeModule())
                .build();
    }

    @Bean
    @Primary
    public UserService userService() {
        return mock(UserService.class);
    }

    @Bean(name = "tieredCacheManager")
    @Primary
    public CacheManager tieredCacheManager() {
        return mock(CacheManager.class);
    }
}

