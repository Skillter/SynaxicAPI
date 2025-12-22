package dev.skillter.synaxic.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class CspNonceFilter implements Filter {

    private static final String NONCE_REQUEST_ATTRIBUTE = "cspNonce";
    private static final String CSP_NONCE_HEADER = "X-CSP-Nonce";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Generate a new nonce for each request
        String nonce = generateNonce();

        // Store nonce in request for templates to use
        httpRequest.setAttribute(NONCE_REQUEST_ATTRIBUTE, nonce);

        // Also add it as a response header for debugging
        httpResponse.setHeader(CSP_NONCE_HEADER, nonce);

        // Continue with the filter chain
        chain.doFilter(request, response);
    }

    private String generateNonce() {
        byte[] nonceBytes = new byte[16]; // 128 bits
        SECURE_RANDOM.nextBytes(nonceBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
    }

    public static String getNonce(HttpServletRequest request) {
        Object nonce = request.getAttribute(NONCE_REQUEST_ATTRIBUTE);
        return nonce != null ? nonce.toString() : "";
    }
}