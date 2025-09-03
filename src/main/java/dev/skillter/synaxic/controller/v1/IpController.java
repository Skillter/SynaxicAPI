package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.EchoResponse;
import dev.skillter.synaxic.model.dto.IpResponse;
import dev.skillter.synaxic.model.dto.WhoAmIResponse;
import dev.skillter.synaxic.service.IpInspectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Request Inspector", description = "Endpoints for inspecting IP addresses and request details")
public class IpController {

    private final IpInspectorService ipInspectorService;

    @GetMapping(value = "/ip", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get client IP address",
            description = "Returns the client's public IP address and its version (IPv4 or IPv6).")
    public IpResponse getIp(HttpServletRequest request) {
        return ipInspectorService.getIpInfo(request);
    }

    @GetMapping(value = "/whoami", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get detailed request info",
            description = "Returns detailed information about the incoming request, including IP, headers, and user agent. Sensitive headers are redacted.")
    public WhoAmIResponse whoAmI(HttpServletRequest request) {
        return ipInspectorService.getRequestDetails(request);
    }

    @PostMapping(value = "/echo",
            consumes = MediaType.ALL_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Echo request metadata",
            description = "Receives any data in the request body and returns metadata about it, such as size and SHA-256 hash, without echoing the content itself.")
    public ResponseEntity<EchoResponse> echo(
            @RequestBody(required = false) byte[] body,
            @RequestHeader(value = "Content-Type", required = false) String contentType) {

        EchoResponse response = ipInspectorService.processEcho(body, contentType);
        return ResponseEntity.ok(response);
    }
}