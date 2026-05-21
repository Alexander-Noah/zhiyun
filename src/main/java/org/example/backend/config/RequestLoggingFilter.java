package org.example.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final String REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = getOrCreateRequestId(request);
        long startTime = System.currentTimeMillis();
        MDC.put(REQUEST_ID, requestId);
        response.setHeader("X-Request-Id", requestId);

        try {
            log.info("request start method={} uri={} query={} ip={} userAgent={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    maskQuery(request.getQueryString()),
                    getClientIp(request),
                    limitLength(request.getHeader("User-Agent"), 160));

            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("request end method={} uri={} status={} durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration);
            MDC.remove(REQUEST_ID);
        }
    }

    private String getOrCreateRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        return requestId;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }

        return request.getRemoteAddr();
    }

    private String maskQuery(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }

        StringBuilder masked = new StringBuilder();
        String[] parts = query.split("&");
        for (int index = 0; index < parts.length; index++) {
            if (index > 0) {
                masked.append('&');
            }
            String part = parts[index];
            int equalsIndex = part.indexOf('=');
            String key = equalsIndex >= 0 ? part.substring(0, equalsIndex) : part;
            if (isSensitiveKey(key)) {
                masked.append(key).append("=******");
            } else {
                masked.append(limitLength(part, 120));
            }
        }
        return limitLength(masked.toString(), 500);
    }

    private boolean isSensitiveKey(String key) {
        String normalizedKey = key == null ? "" : key.toLowerCase();
        return normalizedKey.contains("token")
                || normalizedKey.contains("password")
                || normalizedKey.contains("secret")
                || normalizedKey.contains("key")
                || normalizedKey.contains("authorization");
    }

    private String limitLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
