package org.example.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.backend.service.TongyiAgentService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@CrossOrigin
@RestController
public class AiAssistantConfigController {
    private final TongyiAgentService tongyiAgentService;

    public AiAssistantConfigController(TongyiAgentService tongyiAgentService) {
        this.tongyiAgentService = tongyiAgentService;
    }

    @GetMapping({"/config", "/ai-assistant/config"})
    public Map<String, Object> getConfig(HttpServletRequest request) {
        String host = request.getServerName();
        int port = request.getServerPort();
        String scheme = request.getScheme();
        String wsScheme = "https".equalsIgnoreCase(scheme) ? "wss" : "ws";
        String wsUrl = wsScheme + "://" + host + ":" + port + "/ai-assistant/ws";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("backend_url", scheme + "://" + host + ":" + port);
        data.put("ws_url", wsUrl);
        data.put("ws_proxy_url", wsUrl);
        data.put("agent_provider", "tongyi");
        data.put("tongyi_agent", tongyiAgentService.getConfig());

        return Map.of(
                "code", 0,
                "message", "config loaded",
                "data", data
        );
    }
}
