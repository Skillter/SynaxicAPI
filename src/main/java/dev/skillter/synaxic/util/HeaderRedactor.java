package dev.skillter.synaxic.util;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
public class HeaderRedactor {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "x-api-key",
            "cookie",
            "set-cookie",
            "proxy-authorization",
            "x-auth-token"
    );

    private static final String REDACTED_VALUE = "[REDACTED]";

    public Map<String, String> redactSensitiveHeaders(Map<String, String> headers) {
        Map<String, String> redactedHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((key, value) -> {
            if (SENSITIVE_HEADERS.contains(key.toLowerCase())) {
                redactedHeaders.put(key, REDACTED_VALUE);
            } else {
                redactedHeaders.put(key, value);
            }
        });
        return redactedHeaders;
    }
}