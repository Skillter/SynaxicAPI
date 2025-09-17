package dev.skillter.synaxic.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.model.CountryResponse;
import dev.skillter.synaxic.config.CacheConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Optional;

@Service
@Slf4j
public class GeoIpService {

    private final ResourceLoader resourceLoader;
    private DatabaseReader databaseReader;

    public GeoIpService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        try {
            Resource resource = resourceLoader.getResource("file:/app/geodb/GeoLite2-City.mmdb");
            if (!resource.exists()) {
                log.warn("GeoLite2-City.mmdb database not found at /app/geodb/GeoLite2-City.mmdb. Geolocation features will be disabled.");
                return;
            }
            try (InputStream dbStream = resource.getInputStream()) {
                databaseReader = new DatabaseReader.Builder(dbStream).build();
                log.info("GeoIP database loaded successfully.");
            }
        } catch (IOException e) {
            log.error("Failed to load GeoIP database", e);
        }
    }

    @Cacheable(value = CacheConfig.CACHE_GEO_IP, key = "#ipAddress", unless = "#result == null || !#result.isPresent()")
    public Optional<String> getCountry(String ipAddress) {
        if (databaseReader == null || ipAddress == null) {
            return Optional.empty();
        }

        try {
            InetAddress ip = InetAddress.getByName(ipAddress);
            CountryResponse response = databaseReader.country(ip);
            return Optional.ofNullable(response.getCountry().getIsoCode());
        } catch (AddressNotFoundException e) {
            log.trace("IP address not found in GeoIP database: {}", ipAddress);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Error during GeoIP lookup for address {}: {}", ipAddress, e.getMessage());
            return Optional.empty();
        }
    }
}