package dev.skillter.synaxic.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class IpExtractor {

    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED"
    };

    public String extractClientIp(HttpServletRequest request) {
        // Check all known headers
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                // Handle comma-separated IPs (take the first one)
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                log.trace("Found IP {} in header {}", ip, header);
                return ip;
            }
        }

        // Fall back to remote address
        String remoteAddr = request.getRemoteAddr();
        log.trace("Using remote address: {}", remoteAddr);
        return remoteAddr;
    }
}