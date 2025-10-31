package dev.skillter.synaxic.exception;

import dev.skillter.synaxic.service.MetricsService;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

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

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        log.warn("Access denied for request: {} - Reason: {}", request.getDescription(false), ex.getMessage());
        metricsService.incrementErrorCount(HttpStatus.FORBIDDEN.value());
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.FORBIDDEN,
                "Access Denied",
                "You do not have permission to access this resource.",
                request
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problemDetail);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NoHandlerFoundException ex, WebRequest request) {
        log.warn("Resource not found: {} {}", ex.getHttpMethod(), ex.getRequestURL());
        metricsService.incrementErrorCount(HttpStatus.NOT_FOUND.value());
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.NOT_FOUND,
                "Resource Not Found",
                "The requested resource does not exist.",
                request
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> handleDataAccessException(DataAccessException ex, WebRequest request) {
        log.error("Database access error for request: {}", request.getDescription(false), ex);
        metricsService.incrementErrorCount(HttpStatus.INTERNAL_SERVER_ERROR.value());
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Database Error",
                "A data processing error occurred. Please try again later.",
                request
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        log.warn("Invalid argument for request: {} - Reason: {}", request.getDescription(false), ex.getMessage());
        metricsService.incrementErrorCount(HttpStatus.BAD_REQUEST.value());
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid Request",
                "The request contains invalid parameters.",
                request
        );
        return ResponseEntity.badRequest().body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, WebRequest request) {
        // Log the full error for debugging but return a generic message to client
        log.error("Unhandled exception for request: {} - Error: {}",
                request.getDescription(false),
                ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                ex);

        metricsService.incrementErrorCount(HttpStatus.INTERNAL_SERVER_ERROR.value());
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. The development team has been notified.",
                request
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }
}