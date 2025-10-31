package dev.skillter.synaxic.service;

import com.google.common.net.InternetDomainName;
import dev.skillter.synaxic.model.dto.EmailValidationResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailValidationService {

    private final DnsService dnsService;
    private final ResourceLoader resourceLoader;
    private final Set<String> disposableDomains = new HashSet<>();

    @PostConstruct
    public void init() {
        log.info("Loading disposable domains list...");
        Resource resource = resourceLoader.getResource("classpath:disposable-domains.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().forEach(disposableDomains::add);
            log.info("Successfully loaded {} disposable domains.", disposableDomains.size());
        } catch (IOException e) {
            log.error("Failed to load disposable domains list", e);
        }
    }

    public EmailValidationResponse validateEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return EmailValidationResponse.builder()
                    .email(email)
                    .isValidSyntax(false)
                    .build();
        }

        boolean isValidSyntax = EmailValidator.getInstance().isValid(email);
        if (!isValidSyntax) {
            return EmailValidationResponse.builder()
                    .email(email)
                    .isValidSyntax(false)
                    .build();
        }

        // Safely extract domain with bounds checking
        String domain = extractDomainSafely(email);
        if (domain == null) {
            // This shouldn't happen if EmailValidator passed, but let's be safe
            return EmailValidationResponse.builder()
                    .email(email)
                    .isValidSyntax(false)
                    .build();
        }

        boolean isDisposable = isDisposable(domain);
        boolean hasMxRecords = dnsService.hasMxRecords(domain);

        return EmailValidationResponse.builder()
                .email(email)
                .domain(domain)
                .isValidSyntax(true)
                .isDisposable(isDisposable)
                .hasMxRecords(hasMxRecords)
                .build();
    }

    private boolean isDisposable(String domain) {
        if (!InternetDomainName.isValid(domain)) {
            return false;
        }

        try {
            InternetDomainName domainName = InternetDomainName.from(domain);
            while (domainName != null) {
                if (disposableDomains.contains(domainName.toString())) {
                    return true;
                }
                domainName = domainName.hasParent() ? domainName.parent() : null;
            }
        } catch (IllegalArgumentException e) {
            log.warn("Could not parse domain name: {}", domain, e);
            return disposableDomains.contains(domain);
        }
        return false;
    }

    /**
     * Safely extracts the domain part from an email address with bounds checking.
     * @param email The email address (must be validated first)
     * @return The domain part, or null if extraction fails
     */
    private String extractDomainSafely(String email) {
        if (email == null || email.isEmpty()) {
            return null;
        }

        int atIndex = email.indexOf('@');
        // Basic validation - should have exactly one @ and not at start/end
        if (atIndex <= 0 || atIndex >= email.length() - 1) {
            return null;
        }

        // Check for multiple @ symbols
        if (email.indexOf('@', atIndex + 1) != -1) {
            return null;
        }

        // Extract domain safely
        return email.substring(atIndex + 1);
    }

    public Set<String> getDisposableDomains() {
        return Collections.unmodifiableSet(disposableDomains);
    }
}