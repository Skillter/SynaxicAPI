package dev.skillter.synaxic.config;

import dev.skillter.synaxic.security.ApiKeyAuthFilter;
import dev.skillter.synaxic.security.OAuth2LoginSuccessHandler;
import dev.skillter.synaxic.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyAuthFilter apiKeyAuthFilter;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final RateLimitFilter rateLimitFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/",
            "/error",
            "/v1/ip",
            "/v1/whoami",
            "/v1/echo",
            "/v1/convert/**",
            "/v1/color/**",
            "/v1/email/validate",
            "/v1/auth/login-success",
            "/login",
            "/oauth2/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/**",
            "/api/stats",
            "/api/debug/**",
            "/css/**",
            "/js/**",
            "/assets/**",
            "/favicon.ico",
            "/index.html",
            "/analytics.html",
            "/analytics",
            "/health.html",
            "/health",
            "/login-success.html",
            "/privacy-policy.html",
            "/privacy-policy",
            "/terms-of-service.html",
            "/terms-of-service",
            "/fair-use-policy.html",
            "/fair-use-policy"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, ApiKeyAuthFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/dashboard", "/dashboard.html").authenticated()
                        .requestMatchers("/v1/auth/**").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2LoginSuccessHandler)
                );

        return http.build();
    }
}