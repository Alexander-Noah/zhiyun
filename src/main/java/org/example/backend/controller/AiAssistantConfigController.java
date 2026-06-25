package org.example.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.backend.result.Result;
import org.example.backend.security.JwtAuthenticationFilter;
import org.example.backend.service.AiAssistantUserConfigService;
import org.example.backend.service.TongyiAgentService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@CrossOrigin
@RestController
public class AiAssistantConfigController {
    private final TongyiAgentService tongyiAgentService;
    private final AiAssistantUserConfigService userConfigService;

    public AiAssistantConfigController(
            TongyiAgentService tongyiAgentService,
            AiAssistantUserConfigService userConfigService
    ) {
        this.tongyiAgentService = tongyiAgentService;
        this.userConfigService = userConfigService;
    }

    @GetMapping({"/config", "/ai-assistant/config"})
    public Result getConfig(HttpServletRequest request) {
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

        return Result.success("获取 AI 助手配置成功", data);
    }

    @GetMapping("/ai-assistant/user-config")
    public Result getUserConfig(
            @RequestAttribute(value = JwtAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE, required = false) Integer userId
    ) {
        return Result.success("获取 AI 接口配置成功", userConfigService.getConfig(userId));
    }

    @PutMapping("/ai-assistant/user-config")
    public Result saveUserConfig(
            @RequestAttribute(value = JwtAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE, required = false) Integer userId,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        return Result.success("保存 AI 接口配置成功", userConfigService.saveConfig(userId, payload));
    }

    @PostMapping("/ai-assistant/chat")
    public Result chat(
            @RequestAttribute(value = JwtAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE, required = false) Integer userId,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        Map<String, Object> request = payload == null ? Collections.emptyMap() : payload;
        String question = String.valueOf(request.getOrDefault("question", ""));
        return Result.success("AI 回答成功", Map.of(
                "answer", userConfigService.chat(userId, question)
        ));
    }
}
