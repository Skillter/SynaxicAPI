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

        String domain = email.substring(email.indexOf('@') + 1);
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

    public Set<String> getDisposableDomains() {
        return Collections.unmodifiableSet(disposableDomains);
    }
}