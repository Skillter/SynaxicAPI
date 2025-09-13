package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.EchoResponse;
import dev.skillter.synaxic.model.dto.IpResponse;
import dev.skillter.synaxic.model.dto.WhoAmIResponse;
import dev.skillter.synaxic.service.IpInspectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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
@Tag(name = "Request Inspector", description = "Endpoints for inspecting IP addresses and request details.")
public class IpController {

    private final IpInspectorService ipInspectorService;

    @GetMapping(value = "/ip", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Your Public IP Address",
            description = "Returns the public IP address of the client making the request, along with its version (IPv4 or IPv6).")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved the IP address.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = IpResponse.class)))
    public IpResponse getIp(HttpServletRequest request) {
        return ipInspectorService.getIpInfo(request);
    }

    @GetMapping(value = "/whoami", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Detailed Request Information",
            description = "Returns detailed information about the incoming request, including IP, headers, and user agent. Sensitive headers like `Authorization` and `Cookie` are redacted for security.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved request details.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = WhoAmIResponse.class)))
    public WhoAmIResponse whoAmI(HttpServletRequest request) {
        return ipInspectorService.getRequestDetails(request);
    }

    @PostMapping(value = "/echo",
            consumes = MediaType.ALL_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Echo Request Body Metadata",
            description = "Receives any data in the request body and returns metadata about it, such as size and SHA-256 hash. The content of the body is never stored or returned.")
    @ApiResponse(responseCode = "200", description = "Successfully processed the request body and returned metadata.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = EchoResponse.class)))
    @ApiResponse(responseCode = "413", description = "Payload too large. The request body exceeds the server's limit.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<EchoResponse> echo(
            @RequestBody(required = false) byte[] body,
            @RequestHeader(value = "Content-Type", required = false) String contentType) {

        EchoResponse response = ipInspectorService.processEcho(body, contentType);
        return ResponseEntity.ok(response);
    }
}