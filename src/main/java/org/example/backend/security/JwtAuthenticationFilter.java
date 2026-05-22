package org.example.backend.security;

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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String AUTH_USERNAME_ATTRIBUTE = "authUsername";
    public static final String AUTH_ROLE_ATTRIBUTE = "authRole";
    public static final String AUTH_USER_ID_ATTRIBUTE = "authUserId";

    /**
     * 精确放行路径
     */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/auth/login",
            "/auth/logout",

            "/ai-assistant/ws",
            "/ws/ai-assistant",

            "/error",
            "/favicon.ico",
            "/actuator/health",
            "/host-status/report",
            "/host-status/latest",
            "/host-assets/report",
            "/host-assets",
            "/activation-codes/verify",
            "/admin/activation-codes/verify",

            // Springdoc / Swagger
            "/v3/api-docs",
            "/swagger-ui.html",
            "/swagger-ui/index.html"
    );

    /**
     * 前缀放行路径
     * 例如 /swagger-ui/swagger-initializer.js
     * 例如 /v3/api-docs/swagger-config
     */
    private static final String[] PUBLIC_PREFIXES = {
            "/files/avatar-proxy",
            "/public/",
            "/host-assets/",
            "/v3/api-docs/",
            "/swagger-ui/",
            "/webjars/"
    };

    private final JwtService jwtService;
    private final boolean enabled;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            @Value("${smart-lab.security.jwt.enabled:true}") boolean enabled
    ) {
        this.jwtService = jwtService;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (!enabled || shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);
        if (token.isBlank()) {
            writeUnauthorized(response, "Unauthorized or session expired");
            return;
        }

        try {
            JwtService.JwtClaims claims = jwtService.validateToken(token);
            request.setAttribute(AUTH_USERNAME_ATTRIBUTE, claims.username());
            request.setAttribute(AUTH_ROLE_ATTRIBUTE, claims.roleCode());
            request.setAttribute(AUTH_USER_ID_ATTRIBUTE, claims.userId());
            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException exception) {
            writeUnauthorized(response, "Invalid session, please login again");
        }
    }

    private boolean shouldSkip(HttpServletRequest request) {
        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getServletPath();

        // 兜底处理
        if (path == null || path.isBlank()) {
            path = request.getRequestURI();
        }

        // 精确匹配放行
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }

        // 前缀匹配放行
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return "";
        }

        if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "";
        }

        return authorization.substring(7).trim();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":401,\"message\":\"" + escapeJson(message) + "\",\"data\":{}}");
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (current < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) current));
                    } else {
                        escaped.append(current);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
