package dev.skillter.synaxic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.skillter.synaxic.controller.v1.*;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("API Endpoint Validation Tests")
public class EndpointValidationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    void setUp() {
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Nested
    @DisplayName("IP Inspector Endpoints")
    class IpInspectorTests {

        @Test
        @DisplayName("GET /v1/ip should return IP information")
        void testGetIp() {
            given()
                .when()
                .get("/v1/ip")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("ip", notNullValue())
                .body("ipVersion", anyOf(equalTo("IPv4"), equalTo("IPv6")))
                .header("X-RateLimit-Limit", "1000")
                .header("X-RateLimit-Remaining", matchesPattern("\\d+"))
                .header("X-Request-Id", matchesPattern("^[a-f0-9\\-]{36}$"));
        }

        @Test
        @DisplayName("GET /v1/whoami should return detailed request info")
        void testWhoAmI() {
            Response response = given()
                .header("User-Agent", "Test-Agent/1.0")
                .header("Accept", "application/json")
                .when()
                .get("/v1/whoami")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("ip", notNullValue())
                .body("ipVersion", anyOf(equalTo("IPv4"), equalTo("IPv6")))
                .body("method", equalTo("GET"))
                .body("protocol", equalTo("HTTP/1.1"))
                .body("userAgent", equalTo("Test-Agent/1.0"))
                .body("headers", notNullValue())
                .body("headers.accept", equalTo("application/json"))
                .body("headers.user-agent", equalTo("Test-Agent/1.0"))
                .extract()
                .response();

            try {
                JsonNode json = objectMapper.readTree(response.asString());

                // Verify security headers are redacted
                assertThat(json.path("headers").has("authorization")).isFalse();
                assertThat(json.path("headers").has("cookie")).isFalse();
                assertThat(json.path("headers").has("authorization")).isFalse();
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse JSON response", e);
            }
        }

        @Test
        @DisplayName("POST /v1/echo should echo request body")
        void testEcho() {
            String testContent = "{\"test\": \"data\", \"number\": 123}";

            given()
                .contentType(ContentType.JSON)
                .body(testContent)
                .when()
                .post("/v1/echo")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size", equalTo(testContent.length()))
                .body("sha256", matchesPattern("^[a-f0-9]{64}$"))
                .body("contentType", equalTo("application/json"))
                .body("isEmpty", equalTo(false));
        }

        @Test
        @DisplayName("POST /v1/echo with empty body should handle correctly")
        void testEchoEmptyBody() {
            given()
                .contentType(ContentType.JSON)
                .body("")
                .when()
                .post("/v1/echo")
                .then()
                .statusCode(200)
                .body("size", equalTo(0))
                .body("isEmpty", equalTo(true));
        }
    }

    @Nested
    @DisplayName("Email Validation Endpoints")
    class EmailValidationTests {

        @Test
        @DisplayName("Valid email should return validation result")
        void testValidateValidEmail() {
            given()
                .queryParam("email", "test@example.com")
                .when()
                .get("/v1/email/validate")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("email", equalTo("test@example.com"))
                .body("domain", equalTo("example.com"))
                .body("validSyntax", equalTo(true))
                .body("disposable", equalTo(false))
                .body("hasMxRecords", anyOf(equalTo(true), equalTo(false)));
        }

        @Test
        @DisplayName("Invalid email should return validation error")
        void testValidateInvalidEmail() {
            given()
                .queryParam("email", "invalid-email")
                .when()
                .get("/v1/email/validate")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400))
                .body("title", equalTo("Validation Failed"))
                .body("detail", containsString("must be a well-formed email address"))
                .body("type", containsString("validation-failed"));
        }

        @Test
        @DisplayName("Missing email parameter should return error")
        void testValidateMissingEmail() {
            given()
                .when()
                .get("/v1/email/validate")
                .then()
                .statusCode(400)
                .body("status", equalTo(400))
                .body("detail", containsString("email"));
        }
    }

    @Nested
    @DisplayName("Unit Converter Endpoints")
    class UnitConverterTests {

        @Test
        @DisplayName("Should convert between units correctly")
        void testUnitConversion() {
            given()
                .queryParam("from", "m")
                .queryParam("to", "ft")
                .queryParam("value", "10")
                .when()
                .get("/v1/convert/units")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("from", equalTo("m"))
                .body("to", equalTo("ft"))
                .body("value", equalTo(10.0))
                .body("result", greaterThan(32.0))
                .body("result", lessThan(33.0));
        }

        @Test
        @DisplayName("Invalid units should return error")
        void testUnitConversionInvalidUnits() {
            given()
                .queryParam("from", "invalidunit")
                .queryParam("to", "ft")
                .queryParam("value", "10")
                .when()
                .get("/v1/convert/units")
                .then()
                .statusCode(400)
                .body("title", equalTo("Invalid Conversion Request"))
                .body("detail", containsString("Invalid unit format"));
        }

        @Test
        @DisplayName("Should convert between byte units")
        void testByteConversion() {
            given()
                .queryParam("from", "MB")
                .queryParam("to", "KB")
                .queryParam("value", "1")
                .queryParam("standard", "SI")
                .when()
                .get("/v1/convert/bytes")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("from", equalTo("MB"))
                .body("to", equalTo("KB"))
                .body("value", equalTo(1))
                .body("result", equalTo(1000))
                .body("ratio", equalTo(1000));
        }

        @Test
        @DisplayName("Binary standard should use 1024 ratio")
        void testByteConversionBinary() {
            given()
                .queryParam("from", "MiB")
                .queryParam("to", "KiB")
                .queryParam("value", "1")
                .queryParam("standard", "binary")
                .when()
                .get("/v1/convert/bytes")
                .then()
                .statusCode(200)
                .body("result", equalTo(1024))
                .body("ratio", equalTo(1024));
        }
    }

    @Nested
    @DisplayName("Color Converter Endpoints")
    class ColorConverterTests {

        @Test
        @DisplayName("Should convert between color formats")
        void testColorConversion() {
            given()
                .queryParam("from", "hex")
                .queryParam("to", "rgb")
                .queryParam("value", "FF5733")
                .when()
                .get("/v1/color/convert")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("hex", equalTo("#ff5733"))
                .body("rgb", equalTo("rgb(255, 87, 51)"))
                .body("hsl", matchesPattern("^hsl\\(\\d+, \\d+%, \\d+%\\)$"));
        }

        @Test
        @DisplayName("Should handle hex with # prefix")
        void testColorConversionWithHash() {
            Response response = given()
                .queryParam("from", "hex")
                .queryParam("to", "hsl")
                .queryParam("value", "#FF5733")
                .when()
                .get("/v1/color/convert")
                .then()
                .statusCode(200)
                .extract()
                .response();

            try {
                JsonNode json = objectMapper.readTree(response.asString());
                assertThat(json.has("hex")).isTrue();
                assertThat(json.has("hsl")).isTrue();
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse JSON response", e);
            }
        }

        @Test
        @DisplayName("Should calculate contrast ratio")
        void testColorContrast() {
            given()
                .queryParam("fg", "#FF5733")
                .queryParam("bg", "#FFFFFF")
                .when()
                .get("/v1/color/contrast")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("ratio", greaterThan(3.0))
                .body("ratio", lessThan(3.2))
                .body("aa", isA(Boolean.class))
                .body("aaa", isA(Boolean.class));
        }

        @Test
        @DisplayName("High contrast should return true for AA")
        void testColorContrastHighContrast() {
            given()
                .queryParam("fg", "#000000")
                .queryParam("bg", "#FFFFFF")
                .when()
                .get("/v1/color/contrast")
                .then()
                .statusCode(200)
                .body("ratio", greaterThan(20.0))
                .body("ratio", lessThan(22.0))
                .body("aa", equalTo(true))
                .body("aaa", equalTo(true));
        }

        @Test
        @DisplayName("Missing parameters should return error")
        void testColorConversionMissingParams() {
            given()
                .queryParam("from", "hex")
                .queryParam("to", "rgb")
                .when()
                .get("/v1/color/convert")
                .then()
                .statusCode(400)
                .body("status", equalTo(400))
                .body("detail", containsString("value"));
        }
    }

    @Nested
    @DisplayName("Stats and Analytics Endpoints")
    class StatsTests {

        @Test
        @DisplayName("GET /api/stats should return public statistics")
        void testPublicStats() {
            given()
                .when()
                .get("/api/stats")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("totalRequests", isA(Number.class))
                .body("totalUsers", isA(Number.class))
                .body("totalRequests", greaterThanOrEqualTo(0))
                .body("totalUsers", greaterThanOrEqualTo(0));
        }

        @Test
        @DisplayName("GET /v1/admin/stats should return admin analytics")
        void testAdminStats() {
            Response response = given()
                .when()
                .get("/v1/admin/stats")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .response();

            String content = response.asString();
            assertThat(content).contains("uptime", "requests", "latency", "rates", "cache", "breakdowns");

            try {
                JsonNode json = objectMapper.readTree(content);
                assertThat(json.has("uptime")).isTrue();
                assertThat(json.has("requests")).isTrue();
                assertThat(json.has("latency")).isTrue();
                assertThat(json.has("rates")).isTrue();
                assertThat(json.has("cache")).isTrue();
                assertThat(json.has("breakdowns")).isTrue();
                assertThat(json.has("serviceBreakdown")).isTrue();
                assertThat(json.has("responseTime")).isTrue();
                assertThat(json.has("apiKeys")).isTrue();
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse JSON response", e);
            }
        }
    }

    @Nested
    @DisplayName("Debug Endpoints")
    class DebugTests {

        @Test
        @DisplayName("GET /api/debug/rate-limit should return rate limit status")
        void testRateLimitDebug() {
            given()
                .when()
                .get("/api/debug/rate-limit")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("key", notNullValue())
                .body("tier", equalTo("ANONYMOUS"))
                .body("limit", equalTo(1000))
                .body("remainingTokens", isA(Number.class))
                .body("consumed", equalTo(true))
                .body("remainingTokens", lessThanOrEqualTo(1000));
        }
    }

    @Nested
    @DisplayName("Error Handling and Response Format Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("All API endpoints should include rate limit headers")
        void testRateLimitHeadersPresent() {
            String[] endpoints = {
                "/v1/ip", "/v1/whoami", "/api/stats"
            };

            for (String endpoint : endpoints) {
                given()
                    .when()
                    .get(endpoint)
                    .then()
                    .statusCode(200)
                    .header("X-RateLimit-Limit", notNullValue())
                    .header("X-RateLimit-Remaining", matchesPattern("\\d+"));
            }
        }

        @Test
        @DisplayName("All API endpoints should include request ID header")
        void testRequestIdHeaderPresent() {
            String[] endpoints = {
                "/v1/ip", "/v1/whoami", "/api/stats"
            };

            for (String endpoint : endpoints) {
                given()
                    .when()
                    .get(endpoint)
                    .then()
                    .statusCode(200)
                    .header("X-Request-Id", matchesPattern("^[a-f0-9\\-]{36}$"));
            }
        }

        @Test
        @DisplayName("Error responses should follow RFC 7807 format")
        void testErrorFormat() {
            given()
                .queryParam("email", "invalid")
                .when()
                .get("/v1/email/validate")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("type", notNullValue())
                .body("title", notNullValue())
                .body("status", equalTo(400))
                .body("detail", notNullValue())
                .body("instance", notNullValue())
                .body("timestamp", notNullValue());
        }

        @Test
        @DisplayName("Non-existent endpoints should return 404")
        void testNotFoundEndpoint() {
            given()
                .when()
                .get("/v1/nonexistent")
                .then()
                .statusCode(404);
        }

        @Test
        @DisplayName("Invalid HTTP method should return 405")
        void testMethodNotAllowed() {
            given()
                .when()
                .delete("/v1/ip")
                .then()
                .statusCode(405);
        }
    }

    @Nested
    @DisplayName("Static Page Endpoints")
    class StaticPageTests {

        @Test
        @DisplayName("GET / should serve main page")
        void testRootPage() {
            given()
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .header("X-Request-Id", notNullValue())
                .header("Cache-Control", containsString("no-store"));
        }

        @Test
        @DisplayName("GET /health should serve health page")
        void testHealthPage() {
            given()
                .when()
                .get("/health")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"));
        }

        @Test
        @DisplayName("Static pages should have security headers")
        void testStaticPageSecurityHeaders() {
            given()
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .header("X-Frame-Options", "DENY")
                .header("X-XSS-Protection", "0")
                .header("Content-Security-Policy", notNullValue())
                .header("Referrer-Policy", "strict-origin-when-cross-origin");
        }
    }

    @Nested
    @DisplayName("Rate Limiting Tests")
    class RateLimitingTests {

        @Test
        @DisplayName("Rate limit should decrement with each request")
        void testRateLimitDecrementation() {
            // Make first request
            Response response1 = given()
                .when()
                .get("/v1/ip")
                .then()
                .statusCode(200)
                .extract()
                .response();

            String remaining1 = response1.getHeader("X-RateLimit-Remaining");

            // Make second request
            given()
                .when()
                .get("/v1/ip")
                .then()
                .statusCode(200);

            // Make third request to check remaining tokens
            Response response3 = given()
                .when()
                .get("/v1/ip")
                .then()
                .statusCode(200)
                .extract()
                .response();

            String remaining3 = response3.getHeader("X-RateLimit-Remaining");

            // Should have consumed 2 more tokens
            assertThat(Integer.parseInt(remaining3))
                .isEqualTo(Integer.parseInt(remaining1) - 2);
        }

        @Test
        @DisplayName("Different endpoints should share the same rate limit pool")
        void testSharedRateLimitPool() {
            // Get initial remaining tokens
            Response response1 = given()
                .when()
                .get("/v1/ip")
                .then()
                .statusCode(200)
                .extract()
                .response();

            String remaining1 = response1.getHeader("X-RateLimit-Remaining");

            // Make request to different endpoint
            given()
                .when()
                .get("/v1/whoami")
                .then()
                .statusCode(200);

            // Check remaining tokens after second request
            Response response3 = given()
                .when()
                .get("/v1/ip")
                .then()
                .statusCode(200)
                .extract()
                .response();

            String remaining3 = response3.getHeader("X-RateLimit-Remaining");

            // Should have consumed tokens (shared rate limit pool)
            assertThat(Integer.parseInt(remaining3))
                .isLessThan(Integer.parseInt(remaining1));
        }
    }
}