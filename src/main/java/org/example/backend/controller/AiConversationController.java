package org.example.backend.controller;

import lombok.Data;
import org.example.backend.result.Result;
import org.example.backend.security.JwtAuthenticationFilter;
import org.example.backend.service.AiConversationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
public class AiConversationController {
    private final AiConversationService aiConversationService;

    public AiConversationController(AiConversationService aiConversationService) {
        this.aiConversationService = aiConversationService;
    }

    @GetMapping("/ai-assistant/conversations")
    public Result listConversations(
            @RequestAttribute(value = JwtAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE, required = false) Integer userId
    ) {
        return Result.success("获取 AI 会话记录成功", aiConversationService.listConversations(userId));
    }

    @PutMapping("/ai-assistant/conversations")
    public Result saveConversations(
            @RequestAttribute(value = JwtAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE, required = false) Integer userId,
            @RequestBody(required = false) ConversationBatchRequest request
    ) {
        List<Map<String, Object>> conversations = request == null || request.getConversations() == null
                ? Collections.emptyList()
                : request.getConversations();
        return Result.success("保存 AI 会话记录成功", aiConversationService.saveConversations(userId, conversations));
    }

    @Data
    public static class ConversationBatchRequest {
        private List<Map<String, Object>> conversations;
    }
}
