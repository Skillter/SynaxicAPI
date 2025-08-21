package dev.skillter.synaxic.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "IP address information")
public class IpResponse {

    @Schema(description = "Client IP address", example = "192.168.1.1")
    private String ip;

    @Schema(description = "IP version", example = "IPv4", allowableValues = {"IPv4", "IPv6", "unknown"})
    private String ipVersion;
}