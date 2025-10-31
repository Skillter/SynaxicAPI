package dev.skillter.synaxic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800) // 30 minutes
public class HttpSessionConfig {

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SYNAXIC_SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax"); // Protect against CSRF
        // Use secure cookie in production (HTTPS)
        serializer.setUseSecureCookie(true);
        // Don't set domain pattern - let Spring Session handle it naturally for localhost
        // Spring Session will NOT set a domain cookie attributes for localhost, which is correct
        return serializer;
    }
}