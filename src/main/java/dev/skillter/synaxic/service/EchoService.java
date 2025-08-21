package dev.skillter.synaxic.service;

import dev.skillter.synaxic.model.dto.EchoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@Slf4j
public class EchoService {

    public EchoResponse processEcho(byte[] body, String contentType) {
        if (body == null || body.length == 0) {
            return EchoResponse.builder()
                    .size(0)
                    .sha256(null)
                    .contentType(contentType)
                    .isEmpty(true)
                    .build();
        }

        String sha256 = calculateSha256(body);

        log.debug("Echo request: size={}, contentType={}", body.length, contentType);

        return EchoResponse.builder()
                .size(body.length)
                .sha256(sha256)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .isEmpty(false)
                .build();
    }

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            return null;
        }
    }
}