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
        String[] locations = {
            "file:/app/geodb/GeoLite2-City.mmdb",
            "classpath:GeoLite2-City.mmdb",
            "file:./GeoLite2-City.mmdb"
        };

        for (String location : locations) {
            try {
                Resource resource = resourceLoader.getResource(location);
                if (resource.exists()) {
                    try (InputStream dbStream = resource.getInputStream()) {
                        databaseReader = new DatabaseReader.Builder(dbStream).build();
                        log.info("GeoIP database loaded successfully from {}", location);
                        return;
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to load GeoIP from {}: {}", location, e.getMessage());
            }
        }
        
        log.warn("GeoLite2-City.mmdb database not found. Geolocation features will be disabled.");
    }

    @Cacheable(value = CacheConfig.CACHE_GEO_IP, key = "#ipAddress", unless = "#result == null || !#result.isPresent()")
    public Optional<String> getCountry(String ipAddress) {
        if (databaseReader == null || ipAddress == null) {
            return Optional.empty();
        }

        if (isPrivateIp(ipAddress)) {
            return Optional.empty();
        }

        try {
            InetAddress ip = InetAddress.getByName(ipAddress);
            CountryResponse response = databaseReader.country(ip);
            return Optional.ofNullable(response.getCountry().getIsoCode());
        } catch (AddressNotFoundException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Error during GeoIP lookup for address {}: {}", ipAddress, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isPrivateIp(String ip) {
        return ip.startsWith("127.") || 
               ip.startsWith("10.") || 
               ip.startsWith("192.168.") || 
               ip.startsWith("172.16.") ||
               ip.equals("::1") ||
               ip.equals("0:0:0:0:0:0:0:1");
    }
}

