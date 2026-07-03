package org.example.backend.config;

import org.example.backend.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.example.backend.websocket.AiAssistantWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.Map;

@Configuration
@EnableWebSocket
public class AiAssistantWebSocketConfig implements WebSocketConfigurer {
    private static final int AI_ASSISTANT_MESSAGE_BUFFER_SIZE = 20 * 1024 * 1024;

    private final AiAssistantWebSocketHandler aiAssistantWebSocketHandler;
    private final JwtService jwtService;
    private final String[] allowedOriginPatterns;

    public AiAssistantWebSocketConfig(
            AiAssistantWebSocketHandler aiAssistantWebSocketHandler,
            JwtService jwtService,
            @Value("${smart-lab.security.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*,tauri://localhost,http://tauri.localhost}") String allowedOriginPatterns
    ) {
        this.aiAssistantWebSocketHandler = aiAssistantWebSocketHandler;
        this.jwtService = jwtService;
        this.allowedOriginPatterns = splitCsv(allowedOriginPatterns);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(aiAssistantWebSocketHandler, "/ai-assistant/ws", "/ws/ai-assistant")
                .addInterceptors(new JwtHandshakeInterceptor(jwtService))
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(AI_ASSISTANT_MESSAGE_BUFFER_SIZE);
        container.setMaxBinaryMessageBufferSize(AI_ASSISTANT_MESSAGE_BUFFER_SIZE);
        return container;
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

            String subprotocolToken = resolveTokenFromSubprotocol(request);
            if (!subprotocolToken.isBlank()) {
                return subprotocolToken;
            }

            return "";
        }

        private String resolveTokenFromSubprotocol(ServerHttpRequest request) {
            String header = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
            if (header == null || header.isBlank()) {
                return "";
            }
            return Arrays.stream(header.split(","))
                    .map(String::trim)
                    .filter(protocol -> protocol.startsWith("smart-lab.jwt."))
                    .map(protocol -> protocol.substring("smart-lab.jwt.".length()).trim())
                    .findFirst()
                    .orElse("");
        }
    }

    private static String[] splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toArray(String[]::new);
    }
}
