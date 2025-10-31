package dev.skillter.synaxic.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class IpExtractor {

    private static final List<String> IP_HEADERS = Arrays.asList(
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
    );

    // IPv4 and IPv6 validation patterns
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::1$|^::$"
    );

    private static final Pattern COMPRESSED_IPV6_PATTERN = Pattern.compile(
            "^(([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4})?::(([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4})?$"
    );

    // Private/local IP ranges that should not be treated as external client IPs
    private static final List<String> PRIVATE_IP_PREFIXES = Arrays.asList(
            "10.", "172.16.", "172.17.", "172.18.", "172.19.", "172.20.", "172.21.", "172.22.", "172.23.",
            "172.24.", "172.25.", "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.",
            "192.168.", "127.", "169.254.", "::1", "fc00:", "fe80:"
    );

    public String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "0.0.0.0";
        }

        for (String header : IP_HEADERS) {
            String ipList = request.getHeader(header);
            if (StringUtils.hasText(ipList) && !"unknown".equalsIgnoreCase(ipList)) {
                // The X-Forwarded-For header can contain a comma-separated list of IPs.
                // The first one is the original client, but we need to validate it.
                String potentialIp = ipList.split(",")[0].trim();

                if (isValidIp(potentialIp) && !isPrivateIp(potentialIp)) {
                    return potentialIp;
                }
                // If the first IP is invalid or private, continue checking other headers
            }
        }

        // If no valid headers are found, fall back to the remote address.
        String remoteAddr = request.getRemoteAddr();
        if (isValidIp(remoteAddr)) {
            return remoteAddr;
        }

        return "0.0.0.0";
    }

    /**
     * Validates if the given string is a valid IPv4 or IPv6 address.
     */
    private boolean isValidIp(String ip) {
        if (!StringUtils.hasText(ip) || ip.length() > 45) { // IPv6 max length is 45
            return false;
        }

        // Remove any surrounding whitespace
        ip = ip.trim();

        // Check for common invalid values
        if ("unknown".equalsIgnoreCase(ip) || ip.contains("..") || ip.contains(":::")) {
            return false;
        }

        // Validate IPv4
        if (ip.contains(".")) {
            return IPV4_PATTERN.matcher(ip).matches();
        }

        // Validate IPv6
        if (ip.contains(":")) {
            return IPV6_PATTERN.matcher(ip).matches() ||
                   COMPRESSED_IPV6_PATTERN.matcher(ip).matches();
        }

        return false;
    }

    /**
     * Checks if the IP address is a private/local address.
     */
    private boolean isPrivateIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return true;
        }

        String lowerIp = ip.toLowerCase();
        return PRIVATE_IP_PREFIXES.stream().anyMatch(lowerIp::startsWith);
    }
}