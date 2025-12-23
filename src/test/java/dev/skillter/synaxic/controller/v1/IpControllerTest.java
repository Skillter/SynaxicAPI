package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.EchoResponse;
import dev.skillter.synaxic.model.dto.IpResponse;
import dev.skillter.synaxic.model.dto.WhoAmIResponse;
import dev.skillter.synaxic.service.IpInspectorService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IpControllerTest {

    @Mock
    private IpInspectorService ipInspectorService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private IpController ipController;

    private IpResponse ipResponse;
    private WhoAmIResponse whoAmIResponse;
    private EchoResponse echoResponse;

    @BeforeEach
    void setUp() {
        ipResponse = IpResponse.builder()
                .ip("192.168.1.1")
                .ipVersion("IPv4")
                .build();

        whoAmIResponse = WhoAmIResponse.builder()
                .ip("192.168.1.1")
                .method("GET")
                .protocol("HTTP/1.1")
                .build();

        echoResponse = EchoResponse.builder()
                .size(100)
                .sha256("abc123")
                .contentType("application/json")
                .build();
    }

    @Test
    void getIp_ShouldReturnIpResponse() {
        when(ipInspectorService.getIpInfo(any(HttpServletRequest.class))).thenReturn(ipResponse);

        IpResponse response = ipController.getIp(request);

        assertThat(response).isNotNull();
        assertThat(response.getIp()).isEqualTo("192.168.1.1");
        assertThat(response.getIpVersion()).isEqualTo("IPv4");
        verify(ipInspectorService).getIpInfo(request);
    }

    @Test
    void whoAmI_ShouldReturnWhoAmIResponse() {
        when(ipInspectorService.getRequestDetails(any(HttpServletRequest.class))).thenReturn(whoAmIResponse);

        WhoAmIResponse response = ipController.whoAmI(request);

        assertThat(response).isNotNull();
        assertThat(response.getIp()).isEqualTo("192.168.1.1");
        assertThat(response.getMethod()).isEqualTo("GET");
        verify(ipInspectorService).getRequestDetails(request);
    }

    @Test
    void echo_WithBody_ShouldReturnEchoResponse() {
        byte[] body = "test data".getBytes();
        when(ipInspectorService.processEcho(any(byte[].class), anyString())).thenReturn(echoResponse);

        ResponseEntity<EchoResponse> response = ipController.echo(body, "application/json");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSize()).isEqualTo(100L);
        assertThat(response.getBody().getSha256()).isEqualTo("abc123");
        verify(ipInspectorService).processEcho(eq(body), eq("application/json"));
    }

    @Test
    void echo_WithNullBody_ShouldReturnEchoResponse() {
        when(ipInspectorService.processEcho(any(), any())).thenReturn(echoResponse);

        ResponseEntity<EchoResponse> response = ipController.echo(null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(ipInspectorService).processEcho(any(), eq(null));
    }

    @Test
    void echo_WithNullContentType_ShouldReturnEchoResponse() {
        byte[] body = "test data".getBytes();
        when(ipInspectorService.processEcho(any(byte[].class), any())).thenReturn(echoResponse);

        ResponseEntity<EchoResponse> response = ipController.echo(body, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(ipInspectorService).processEcho(eq(body), eq(null));
    }
}
