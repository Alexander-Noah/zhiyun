package org.example.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StrictCorsFilter extends OncePerRequestFilter {
    private final CorsConfiguration corsConfiguration;

    public StrictCorsFilter(
            @Value("${smart-lab.security.cors.allowed-origins:}") String allowedOrigins,
            @Value("${smart-lab.security.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*,http://10.*.*.*:*,http://172.*.*.*:*,http://192.168.*.*:*}") String allowedOriginPatterns
    ) {
        this.corsConfiguration = new CorsConfiguration();
        this.corsConfiguration.setAllowedOrigins(splitCsv(allowedOrigins));
        this.corsConfiguration.setAllowedOriginPatterns(splitCsv(allowedOriginPatterns));
        this.corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        this.corsConfiguration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "X-Request-Id",
                "Accept",
                "Origin"
        ));
        this.corsConfiguration.setExposedHeaders(List.of("X-Request-Id"));
        this.corsConfiguration.setAllowCredentials(false);
        this.corsConfiguration.setMaxAge(1800L);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);

        if (origin == null || origin.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String allowedOrigin = corsConfiguration.checkOrigin(origin);
        if (allowedOrigin == null) {
            writeForbidden(response);
            return;
        }

        writeCorsHeaders(request, response, allowedOrigin);
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        filterChain.doFilter(request, response);
        writeCorsHeaders(request, response, allowedOrigin);
    }

    private void writeCorsHeaders(HttpServletRequest request, HttpServletResponse response, String allowedOrigin) {
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigin);
        response.setHeader(HttpHeaders.VARY, "Origin, Access-Control-Request-Method, Access-Control-Request-Headers");
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, String.join(",", corsConfiguration.getAllowedMethods()));
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, String.join(",", corsConfiguration.getAllowedHeaders()));
        response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, String.join(",", corsConfiguration.getExposedHeaders()));
        response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, String.valueOf(corsConfiguration.getMaxAge()));
        String requestedMethod = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
        if (requestedMethod != null && !requestedMethod.isBlank()) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, requestedMethod);
        }
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":403,\"message\":\"CORS origin is not allowed\",\"data\":{}}");
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }
}
