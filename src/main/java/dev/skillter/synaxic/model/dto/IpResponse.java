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
@Schema(description = "The client's public IP address information.")
public class IpResponse {

    @Schema(description = "The client's public IP address.", example = "203.0.113.195")
    private String ip;

    @Schema(description = "The version of the IP address.", example = "IPv4", allowableValues = {"IPv4", "IPv6", "unknown"})
    private String ipVersion;
}