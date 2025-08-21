package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.IpResponse;
import dev.skillter.synaxic.model.dto.WhoAmIResponse;
import dev.skillter.synaxic.service.IpInspectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "IP Inspector", description = "IP address and request inspection endpoints")
public class IpController {

    private final IpInspectorService ipInspectorService;

    @GetMapping(value = "/ip", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get client IP address",
            description = "Returns the client's IP address and version")
    public IpResponse getIp(HttpServletRequest request) {
        return ipInspectorService.getIpInfo(request);
    }

    @GetMapping(value = "/whoami", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get request details",
            description = "Returns detailed information about the incoming request")
    public WhoAmIResponse whoAmI(HttpServletRequest request) {
        return ipInspectorService.getRequestDetails(request);
    }
}