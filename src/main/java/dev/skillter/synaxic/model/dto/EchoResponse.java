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
@Schema(description = "Echo response with request metadata")
public class EchoResponse {

    @Schema(description = "Size of the request body in bytes", example = "1024")
    private int size;

    @Schema(description = "SHA-256 hash of the request body", example = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    private String sha256;

    @Schema(description = "Content-Type of the request", example = "application/json")
    private String contentType;

    @Schema(description = "Whether the request body was empty", example = "false")
    private boolean isEmpty;
}