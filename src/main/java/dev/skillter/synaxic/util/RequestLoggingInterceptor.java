package dev.skillter.synaxic.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private final IpExtractor ipExtractor;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        MDC.put("clientIp", ipExtractor.extractClientIp(request));
        MDC.put("method", request.getMethod());
        MDC.put("path", request.getRequestURI());

        response.setHeader("X-Request-Id", requestId);

        long startTime = System.currentTimeMillis();
        request.setAttribute("startTime", startTime);

        log.info("Request started: {} {}", request.getMethod(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute("startTime");
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;

        log.info("Request completed: {} {} - Status: {} - Duration: {}ms",
                request.getMethod(), request.getRequestURI(), response.getStatus(), duration);

        MDC.clear();
    }
}