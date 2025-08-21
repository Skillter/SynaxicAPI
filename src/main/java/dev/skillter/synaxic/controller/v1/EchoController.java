package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.EchoResponse;
import dev.skillter.synaxic.service.EchoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Echo", description = "Request echo and inspection")
public class EchoController {

    private final EchoService echoService;

    @PostMapping(value = "/echo",
            consumes = MediaType.ALL_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Echo request details",
            description = "Returns metadata about the posted content without echoing the actual content")
    public ResponseEntity<EchoResponse> echo(
            @RequestBody(required = false) byte[] body,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            HttpServletRequest request) {

        EchoResponse response = echoService.processEcho(body, contentType);
        return ResponseEntity.ok(response);
    }
}