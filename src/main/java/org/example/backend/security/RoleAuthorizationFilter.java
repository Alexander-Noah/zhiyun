package org.example.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
public class RoleAuthorizationFilter extends OncePerRequestFilter {
    private final SecurityAccessPolicy accessPolicy;

    public RoleAuthorizationFilter() {
        this(new SecurityAccessPolicy());
    }

    RoleAuthorizationFilter(SecurityAccessPolicy accessPolicy) {
        this.accessPolicy = accessPolicy;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String roleCode = stringAttribute(request, JwtAuthenticationFilter.AUTH_ROLE_ATTRIBUTE);
        if (roleCode.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            path = request.getRequestURI();
        }

        if (accessPolicy.isAllowed(request.getMethod(), path, roleCode)) {
            filterChain.doFilter(request, response);
            return;
        }

        writeForbidden(response);
    }

    private String stringAttribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":403,\"message\":\"Forbidden\",\"data\":{}}");
    }
}
