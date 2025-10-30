package dev.skillter.synaxic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableRedisHttpSession
public class HttpSessionConfig {

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SYNAXIC_SESSION");
        serializer.setCookiePath("/");
        // Match localhost or any domain with extension: localhost, example.com, sub.example.com
        // Don't set domain pattern - let Spring Session handle it naturally for localhost
        // Spring Session will NOT set a domain cookie attribute for localhost, which is correct
        return serializer;
    }
}