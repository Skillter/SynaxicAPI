package dev.skillter.synaxic.config;

import dev.skillter.synaxic.security.ApiKeyAuthFilter;
import dev.skillter.synaxic.security.OAuth2LoginSuccessHandler;
import dev.skillter.synaxic.security.RateLimitFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
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
    public CorsConfigurationSource corsConfigurationSource() {
        return mock(CorsConfigurationSource.class);
    }
}