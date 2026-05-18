package org.example.backend.service;

import java.util.List;
import java.util.Map;

public interface AiConversationService {
    List<Map<String, Object>> listConversations(Integer userId);

    List<Map<String, Object>> saveConversations(Integer userId, List<Map<String, Object>> conversations);
}
