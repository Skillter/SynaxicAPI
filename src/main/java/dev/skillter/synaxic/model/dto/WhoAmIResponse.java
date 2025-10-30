package dev.skillter.synaxic.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Detailed information about the incoming client request.")
public class WhoAmIResponse {

    @Schema(description = "The client's public IP address.", example = "203.0.113.195")
    private String ip;

    @Schema(description = "The version of the IP address.", example = "IPv4")
    private String ipVersion;

    @Schema(description = "A map of HTTP headers from the request. Sensitive headers are redacted.",
            example = "{\"host\": \"synaxic.skillter.dev\", \"user-agent\": \"curl/7.81.0\", \"authorization\": \"[REDACTED]\"}")
    private Map<String, String> headers;

    @Schema(description = "The User-Agent string from the request headers.", example = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...")
    private String userAgent;

    @Schema(description = "The HTTP method used for the request.", example = "GET")
    private String method;

    @Schema(description = "The protocol used for the request.", example = "HTTP/1.1")
    private String protocol;
}