package dev.skillter.synaxic.service;

import dev.skillter.synaxic.BaseIntegrationTest;
import dev.skillter.synaxic.model.dto.EmailValidationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class EmailValidationServiceTest extends BaseIntegrationTest {

    @MockitoBean
    private DnsService dnsService;

    @MockitoBean
    private ResourceLoader resourceLoader;

    @Autowired
    private EmailValidationService emailValidationService;

    @BeforeEach
    void setUp() {
        String domains = "mailinator.com\ntemp-mail.org";
        when(resourceLoader.getResource(anyString()))
                .thenReturn(new ByteArrayResource(domains.getBytes(StandardCharsets.UTF_8)));
        emailValidationService.init();
    }

    @Test
    void validateEmail_withValidNonDisposableEmail_shouldSucceed() {
        when(dnsService.hasMxRecords("gmail.com")).thenReturn(true);

        EmailValidationResponse response = emailValidationService.validateEmail("test@gmail.com");

        assertThat(response.isValidSyntax()).isTrue();
        assertThat(response.isDisposable()).isFalse();
        assertThat(response.isHasMxRecords()).isTrue();
        assertThat(response.getDomain()).isEqualTo("gmail.com");
    }

    @Test
    void validateEmail_withDisposableDomain_shouldFlagAsDisposable() {
        when(dnsService.hasMxRecords("mailinator.com")).thenReturn(true);

        EmailValidationResponse response = emailValidationService.validateEmail("test@mailinator.com");

        assertThat(response.isValidSyntax()).isTrue();
        assertThat(response.isDisposable()).isTrue();
        assertThat(response.isHasMxRecords()).isTrue();
    }

    @Test
    void validateEmail_withDisposableSubdomain_shouldFlagAsDisposable() {
        when(dnsService.hasMxRecords("sub.mailinator.com")).thenReturn(true);

        EmailValidationResponse response = emailValidationService.validateEmail("test@sub.mailinator.com");

        assertThat(response.isValidSyntax()).isTrue();
        assertThat(response.isDisposable()).isTrue();
        assertThat(response.isHasMxRecords()).isTrue();
    }

    @Test
    void validateEmail_withInvalidSyntax_shouldFail() {
        EmailValidationResponse response = emailValidationService.validateEmail("invalid-email");

        assertThat(response.isValidSyntax()).isFalse();
        assertThat(response.isDisposable()).isFalse();
        assertThat(response.isHasMxRecords()).isFalse();
    }

    @Test
    void validateEmail_withNoMxRecords_shouldFlag() {
        when(dnsService.hasMxRecords("no-mx-domain.com")).thenReturn(false);

        EmailValidationResponse response = emailValidationService.validateEmail("test@no-mx-domain.com");

        assertThat(response.isValidSyntax()).isTrue();
        assertThat(response.isDisposable()).isFalse();
        assertThat(response.isHasMxRecords()).isFalse();
    }

    @Test
    void getDisposableDomains_shouldReturnLoadedDomains() {
        assertThat(emailValidationService.getDisposableDomains()).contains("mailinator.com", "temp-mail.org");
    }
}