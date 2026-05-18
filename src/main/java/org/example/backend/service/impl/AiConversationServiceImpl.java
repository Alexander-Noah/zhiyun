package org.example.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backend.entity.AiConversationEntity;
import org.example.backend.mapper.AiConversationMapper;
import org.example.backend.service.AiConversationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiConversationServiceImpl implements AiConversationService {
    private static final int MAX_HISTORY_COUNT = 30;
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_PREVIEW_LENGTH = 300;
    private static final TypeReference<List<Map<String, Object>>> MESSAGE_LIST_TYPE = new TypeReference<>() {
    };

    private final AiConversationMapper conversationMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiConversationServiceImpl(AiConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listConversations(Integer userId) {
        requireUserId(userId);
        return conversationMapper.listByUser(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<Map<String, Object>> saveConversations(Integer userId, List<Map<String, Object>> conversations) {
        requireUserId(userId);
        if (conversations == null || conversations.isEmpty()) {
            return listConversations(userId);
        }

        conversations.stream()
                .limit(MAX_HISTORY_COUNT)
                .map((conversation) -> toEntity(userId, conversation))
                .forEach(conversationMapper::upsertConversation);
        conversationMapper.trimUserConversations(userId, MAX_HISTORY_COUNT);
        return listConversations(userId);
    }

    private void requireUserId(Integer userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("登录状态无效，请重新登录");
        }
    }

    private AiConversationEntity toEntity(Integer userId, Map<String, Object> payload) {
        Map<String, Object> conversation = payload == null ? Map.of() : payload;
        String conversationId = trimToNull(stringValue(conversation.get("id")));
        if (conversationId == null) {
            conversationId = "conversation-" + System.currentTimeMillis();
        }

        Object messages = conversation.get("messages");
        List<?> messageList = messages instanceof List<?> list ? list : List.of();

        AiConversationEntity entity = new AiConversationEntity();
        entity.setUserId(userId);
        entity.setConversationId(limit(conversationId, 120));
        entity.setTitle(limit(firstNonBlank(stringValue(conversation.get("title")), "新的咨询"), MAX_TITLE_LENGTH));
        entity.setPreview(limit(firstNonBlank(stringValue(conversation.get("preview")), "暂无对话内容"), MAX_PREVIEW_LENGTH));
        entity.setUpdatedAtMillis(numberValue(conversation.get("updatedAt"), System.currentTimeMillis()));
        entity.setMessagesJson(writeMessages(messageList));
        return entity;
    }

    private Map<String, Object> toResponse(AiConversationEntity entity) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", entity.getConversationId());
        response.put("title", entity.getTitle());
        response.put("preview", entity.getPreview());
        response.put("updatedAt", entity.getUpdatedAtMillis());
        response.put("messages", readMessages(entity.getMessagesJson()));
        return response;
    }

    private String writeMessages(List<?> messages) {
        try {
            return objectMapper.writeValueAsString(messages == null ? List.of() : messages);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private List<Map<String, Object>> readMessages(String messagesJson) {
        if (messagesJson == null || messagesJson.isBlank()) {
            return List.of();
        }

        try {
            List<Map<String, Object>> messages = objectMapper.readValue(messagesJson, MESSAGE_LIST_TYPE);
            return messages == null ? List.of() : messages;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return "";
    }

    private long numberValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception exception) {
            return fallback;
        }
    }

    private String limit(String value, int maxLength) {
        String text = firstNonBlank(value);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
