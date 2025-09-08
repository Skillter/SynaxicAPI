package dev.skillter.synaxic.exception;

import jakarta.validation.ConstraintViolationException;
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
public class GlobalExceptionHandler {

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
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }
}