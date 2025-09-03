package dev.skillter.synaxic.service;

import dev.skillter.synaxic.model.dto.EchoResponse;
import dev.skillter.synaxic.model.dto.IpResponse;
import dev.skillter.synaxic.model.dto.WhoAmIResponse;
import dev.skillter.synaxic.util.HeaderRedactor;
import dev.skillter.synaxic.util.IpExtractor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class IpInspectorService {

    private final HeaderRedactor headerRedactor;
    private final IpExtractor ipExtractor;

    public IpResponse getIpInfo(HttpServletRequest request) {
        String clientIp = ipExtractor.extractClientIp(request);
        String ipVersion = determineIpVersion(clientIp);

        return IpResponse.builder()
                .ip(clientIp)
                .ipVersion(ipVersion)
                .build();
    }

    public WhoAmIResponse getRequestDetails(HttpServletRequest request) {
        String clientIp = ipExtractor.extractClientIp(request);
        Map<String, String> headers = extractHeaders(request);
        String userAgent = request.getHeader("User-Agent");
        String method = request.getMethod();
        String protocol = request.getProtocol();

        return WhoAmIResponse.builder()
                .ip(clientIp)
                .ipVersion(determineIpVersion(clientIp))
                .headers(headerRedactor.redactSensitiveHeaders(headers))
                .userAgent(userAgent)
                .method(method)
                .protocol(protocol)
                .build();
    }

    public EchoResponse processEcho(byte[] body, String contentType) {
        if (body == null || body.length == 0) {
            return EchoResponse.builder()
                    .size(0)
                    .sha256(calculateSha256(new byte[0]))
                    .contentType(contentType)
                    .isEmpty(true)
                    .build();
        }

        String sha256 = calculateSha256(body);

        return EchoResponse.builder()
                .size(body.length)
                .sha256(sha256)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .isEmpty(false)
                .build();
    }

    private String determineIpVersion(String ip) {
        if (ip == null) {
            return "unknown";
        }
        return ip.contains(":") ? "IPv6" : "IPv4";
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Collections.list(request.getHeaderNames()).forEach(name -> {
            headers.put(name, request.getHeader(name));
        });
        return headers;
    }

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}