package dev.skillter.synaxic.exception;

import dev.skillter.synaxic.service.MetricsService;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MetricsService metricsService;

    private ProblemDetail createProblemDetail(HttpStatus status, String title, String detail, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create("https://synaxic.skillter.dev/errors/" + title.toLowerCase().replace(" ", "-")));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("path", request.getDescription(false).replace("uri=", ""));
        return problemDetail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        log.warn("Validation failed: {}", ex.getMessage());
        metricsService.incrementErrorCount(HttpStatus.BAD_REQUEST.value());
        String violations = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));

        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Validation Failed",
                "Invalid request parameters: " + violations,
                request
        );
        return ResponseEntity.badRequest().body(problemDetail);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException ex, WebRequest request) {
        log.warn("Missing required parameter: {}", ex.getParameterName());
        metricsService.incrementErrorCount(HttpStatus.BAD_REQUEST.value());
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Missing Required Parameter",
                String.format("Required parameter '%s' of type '%s' is missing",
                        ex.getParameterName(), ex.getParameterType()),
                request
        );
        return ResponseEntity.badRequest().body(problemDetail);
    }

    @ExceptionHandler(ConversionException.class)
    public ResponseEntity<ProblemDetail> handleConversionException(ConversionException ex, WebRequest request) {
        log.warn("Conversion failed: {}", ex.getMessage());
        metricsService.incrementErrorCount(HttpStatus.BAD_REQUEST.value());
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid Conversion Request",
                ex.getMessage(),
                request
        );
        return ResponseEntity.badRequest().body(problemDetail);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxSizeException(MaxUploadSizeExceededException ex, WebRequest request) {
        log.warn("Max upload size exceeded: {}", ex.getMessage());
        metricsService.incrementErrorCount(HttpStatus.PAYLOAD_TOO_LARGE.value());
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Payload Too Large",
                "The request payload exceeds the maximum allowed size of 10MB.",
                request
        );
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, WebRequest request) {
        log.error("Unhandled exception for request: {}", request.getDescription(false), ex);
        metricsService.incrementErrorCount(HttpStatus.INTERNAL_SERVER_ERROR.value());
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }
}```

        ### 4. Configuration Files

#### `application.properties` (Modified)
We add tags to all metrics for easier filtering in Grafana.

        **`src/main/resources/application.properties`**
        ```properties
# ===================================================================
        # BASE CONFIGURATION (Shared across all environments)
# ===================================================================

        # --- Application ---
spring.application.name=Synaxic API
server.port=8080
server.servlet.context-path=/

        # --- API Documentation ---
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.operationsSorter=method
springdoc.default-produces-media-type=application/json
springdoc.default-consumes-media-type=application/json

# --- Jackson ---
spring.jackson.serialization.write-dates-as-timestamps=false
spring.jackson.serialization.indent-output=true
spring.jackson.default-property-inclusion=non_null

# --- Actuator ---
management.endpoints.web.exposure.include=health,info,prometheus,metrics,caches
management.endpoint.health.show-details=always
management.metrics.export.prometheus.enabled=true
management.metrics.tags.application=${spring.application.name}

# --- Problem Details ---
spring.mvc.problemdetails.enabled=true

        # --- Compression & Request Size ---
server.compression.enabled=true
server.compression.mime-types=application/json,application/xml,text/html,text/xml,text/plain
server.compression.min-response-size=1024
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
server.tomcat.max-http-form-post-size=10MB

# --- Cache ---
spring.cache.type=caffeine
spring.cache.caffeine.spec=maximumSize=10000,expireAfterWrite=10m

# --- Redis ---
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.timeout=2000ms

# --- Rate Limiting (Bucket4j) ---
synaxic.rate-limit.anonymous.capacity=100
synaxic.rate-limit.anonymous.refill-minutes=60
synaxic.rate-limit.api-key.capacity=1000
synaxic.rate-limit.api-key.refill-minutes=60

        # --- JPA ---
spring.jpa.open-in-view=false


        # --- Secrets ---
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
spring.security.oauth2.client.registration.google.scope=openid,profile,email