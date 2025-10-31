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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
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

    // Endpoints that don't require CSRF protection (API endpoints and OAuth2)
    private static final String[] CSRF_EXCLUDED_ENDPOINTS = {
            "/v1/**",
            "/api/**",
            "/oauth2/**",
            "/login",
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        // Set the name of the attribute the CsrfToken will be populated on
        requestHandler.setCsrfRequestAttributeName("_csrf");

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers(CSRF_EXCLUDED_ENDPOINTS)
                )
                .headers(headers -> headers
                    // Content Security Policy to prevent XSS
                    .contentSecurityPolicy(csp -> csp
                        .policyDirectives(
                            "default-src 'self'; " +
                            "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                            "font-src 'self' https://fonts.gstatic.com; " +
                            "img-src 'self' data: https:; " +
                            "connect-src 'self'; " +
                            "frame-src 'none'; " +
                            "object-src 'none'; " +
                            "base-uri 'self'; " +
                            "form-action 'self'; " +
                            "frame-ancestors 'none'; " +
                            "upgrade-insecure-requests"
                        )
                    )
                    // Prevent content-type sniffing
                    .contentTypeOptions(contentType -> contentType.disable())
                    // Clickjacking protection
                    .frameOptions(frameOptions -> frameOptions.deny())
                    // Referrer policy
                    .referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                    )
                    // HSTS (HTTP Strict Transport Security)
                    .httpStrictTransportSecurity(hsts -> hsts
                        .maxAgeInSeconds(31536000) // 1 year
                        .preload(false)
                    )
                )
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