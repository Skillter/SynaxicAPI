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
            "x-auth-token",
            "api-key",
            "secret",
            "password",
            "token"
    );

    private static final String REDACTED = "[REDACTED]";

    public Map<String, String> redactSensitiveHeaders(Map<String, String> headers) {
        Map<String, String> redacted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.forEach((key, value) -> {
            if (shouldRedact(key)) {
                redacted.put(key, REDACTED);
            } else {
                redacted.put(key, value);
            }
        });

        return redacted;
    }

    private boolean shouldRedact(String headerName) {
        if (headerName == null) return false;
        String lowerName = headerName.toLowerCase();
        return SENSITIVE_HEADERS.stream()
                .anyMatch(lowerName::contains);
    }
}