package dev.skillter.synaxic.service;

import dev.skillter.synaxic.model.dto.EmailValidationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class EmailValidationServiceTest {

    @Mock
    private DnsService dnsService;

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private Resource resource;

    private EmailValidationService emailValidationService;

    @BeforeEach
    void setUp() {
        // Setup disposable domains list
        String domains = "mailinator.com\ntemp-mail.org";
        given(resourceLoader.getResource(anyString())).willReturn(resource);
        try {
            given(resource.getInputStream()).willReturn(new ByteArrayInputStream(domains.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        emailValidationService = new EmailValidationService(dnsService, resourceLoader);
        emailValidationService.init();
    }

    @Test
    void validateEmail_ValidEmail_ReturnsValid() {
        given(dnsService.hasMxRecords("gmail.com")).willReturn(true);

        EmailValidationResponse response = emailValidationService.validateEmail("test@gmail.com");

        assertThat(response.isValidSyntax()).isTrue();
        assertThat(response.isDisposable()).isFalse();
        assertThat(response.isHasMxRecords()).isTrue();
    }

    @Test
    void validateEmail_DisposableEmail_ReturnsDisposable() {
        given(dnsService.hasMxRecords("mailinator.com")).willReturn(true);

        EmailValidationResponse response = emailValidationService.validateEmail("test@mailinator.com");

        assertThat(response.isValidSyntax()).isTrue();
        assertThat(response.isDisposable()).isTrue();
    }

    @Test
    void validateEmail_InvalidSyntax_ReturnsInvalid() {
        EmailValidationResponse response = emailValidationService.validateEmail("invalid-email");

        assertThat(response.isValidSyntax()).isFalse();
    }
}

