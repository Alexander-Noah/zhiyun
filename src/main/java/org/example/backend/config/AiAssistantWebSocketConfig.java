package org.example.backend.config;

import org.example.backend.security.JwtService;
import org.example.backend.websocket.AiAssistantWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

@Configuration
@EnableWebSocket
public class AiAssistantWebSocketConfig implements WebSocketConfigurer {
    private final AiAssistantWebSocketHandler aiAssistantWebSocketHandler;
    private final JwtService jwtService;

    public AiAssistantWebSocketConfig(AiAssistantWebSocketHandler aiAssistantWebSocketHandler, JwtService jwtService) {
        this.aiAssistantWebSocketHandler = aiAssistantWebSocketHandler;
        this.jwtService = jwtService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(aiAssistantWebSocketHandler, "/ai-assistant/ws", "/ws/ai-assistant")
                .addInterceptors(new JwtHandshakeInterceptor(jwtService))
                .setAllowedOrigins("*");
    }

    private static class JwtHandshakeInterceptor implements HandshakeInterceptor {
        private final JwtService jwtService;

        private JwtHandshakeInterceptor(JwtService jwtService) {
            this.jwtService = jwtService;
        }

        @Override
        public boolean beforeHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Map<String, Object> attributes
        ) {
            String token = resolveToken(request);
            if (token.isBlank()) {
                return false;
            }

            try {
                JwtService.JwtClaims claims = jwtService.validateToken(token);
                attributes.put("authUsername", claims.username());
                attributes.put("authRole", claims.roleCode());
                attributes.put("authUserId", claims.userId());
                return true;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        @Override
        public void afterHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Exception exception
        ) {
            // No-op.
        }

        private String resolveToken(ServerHttpRequest request) {
            String authorization = request.getHeaders().getFirst("Authorization");
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return authorization.substring(7).trim();
            }

            if (request instanceof ServletServerHttpRequest servletRequest) {
                String token = servletRequest.getServletRequest().getParameter("token");
                return token == null ? "" : token.trim();
            }

            String query = Optional.ofNullable(request.getURI().getRawQuery()).orElse("");
            return Arrays.stream(query.split("&"))
                    .map(parameter -> parameter.split("=", 2))
                    .filter(parts -> parts.length == 2 && "token".equals(parts[0]))
                    .map(parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8))
                    .findFirst()
                    .orElse("");
        }
    }
}
