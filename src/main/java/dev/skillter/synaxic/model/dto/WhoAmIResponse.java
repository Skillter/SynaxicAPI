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
@Schema(description = "Detailed request information")
public class WhoAmIResponse {

    @Schema(description = "Client IP address", example = "192.168.1.1")
    private String ip;

    @Schema(description = "IP version", example = "IPv4")
    private String ipVersion;

    @Schema(description = "HTTP headers (sensitive headers redacted)")
    private Map<String, String> headers;

    @Schema(description = "User-Agent string", example = "Mozilla/5.0...")
    private String userAgent;

    @Schema(description = "HTTP method", example = "GET")
    private String method;

    @Schema(description = "HTTP protocol", example = "HTTP/1.1")
    private String protocol;
}