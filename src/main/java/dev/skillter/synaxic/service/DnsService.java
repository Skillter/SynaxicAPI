package dev.skillter.synaxic.service;

import dev.skillter.synaxic.config.CacheConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.MXRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.util.Arrays;

@Service
@Slf4j
public class DnsService {

    @Cacheable(value = CacheConfig.CACHE_MX_RECORDS, key = "#domain")
    public boolean hasMxRecords(String domain) {
        log.debug("Performing MX record lookup for domain: {}", domain);
        try {
            Lookup lookup = new Lookup(domain, Type.MX);
            org.xbill.DNS.Record[] records = lookup.run();

            if (lookup.getResult() != Lookup.SUCCESSFUL) {
                log.warn("MX lookup failed for domain {}: {}", domain, lookup.getErrorString());
                return false;
            }

            return records != null && records.length > 0 && Arrays.stream(records)
                    .anyMatch(record -> record instanceof MXRecord);

        } catch (TextParseException e) {
            log.error("Invalid domain format for DNS lookup: {}", domain, e);
            return false;
        } catch (Exception e) {
            log.error("An unexpected error occurred during DNS lookup for domain: {}", domain, e);
            return false;
        }
    }
}