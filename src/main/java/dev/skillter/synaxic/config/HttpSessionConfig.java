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
        // Try Lax first - more secure and usually works for OAuth
        serializer.setSameSite("Lax");
        // Use secure cookie in production (HTTPS)
        serializer.setUseSecureCookie(true);
        // Set domain for the exact domain
        serializer.setDomainName("synaxic.skillter.dev");
        return serializer;
    }
}