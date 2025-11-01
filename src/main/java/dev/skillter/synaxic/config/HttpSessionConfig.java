package dev.skillter.synaxic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 86400) // 24 hours
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
        // Remove explicit domain setting to let browser handle it automatically
        // This fixes session cookie visibility issues
        // serializer.setDomainName("synaxic.skillter.dev");
        return serializer;
    }
}