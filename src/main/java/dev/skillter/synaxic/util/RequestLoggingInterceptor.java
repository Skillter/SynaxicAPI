package dev.skillter.synaxic.util;

import dev.skillter.synaxic.security.ApiKeyAuthFilter;
import dev.skillter.synaxic.service.GeoIpService;
import dev.skillter.synaxic.service.MetricsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private final IpExtractor ipExtractor;
    private final GeoIpService geoIpService;
    private final MetricsService metricsService;

    private static final String UNKNOWN = "unknown";
    private static final String ANONYMOUS = "anonymous";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long startTime = System.currentTimeMillis();
        request.setAttribute("startTime", startTime);

        String requestId = UUID.randomUUID().toString();
        String clientIp = ipExtractor.extractClientIp(request);
        String country = geoIpService.getCountry(clientIp).orElse(UNKNOWN);

        MDC.put("requestId", requestId);
        MDC.put("clientIp", clientIp);
        MDC.put("geoCountry", country);
        MDC.put("method", request.getMethod());
        MDC.put("path", request.getRequestURI());

        response.setHeader("X-Request-Id", requestId);

        log.info("Request started: {} {}", request.getMethod(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute("startTime");
        long durationMillis = startTime != null ? System.currentTimeMillis() - startTime : 0;
        Duration duration = Duration.ofMillis(durationMillis);

        String method = request.getMethod();
        String path = request.getRequestURI();
        int status = response.getStatus();
        String apiKeyPrefix = (String) request.getAttribute(ApiKeyAuthFilter.API_KEY_PREFIX_ATTRIBUTE);
        String geoCountry = MDC.get("geoCountry");

        Object bestMatchPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String endpoint = bestMatchPattern != null ? bestMatchPattern.toString() : path;

        metricsService.recordResponseTime(endpoint, method, duration);
        metricsService.incrementApiRequest(
                endpoint,
                method,
                status,
                apiKeyPrefix != null ? apiKeyPrefix : ANONYMOUS,
                geoCountry != null ? geoCountry : UNKNOWN
        );

        log.info("Request completed: {} {} - Status: {} - Duration: {}ms", method, path, status, durationMillis);

        MDC.clear();
    }
}