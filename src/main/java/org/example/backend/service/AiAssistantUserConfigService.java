package org.example.backend.service;

import java.util.Map;

public interface AiAssistantUserConfigService {
    Map<String, Object> getConfig(Integer userId);

    Map<String, Object> saveConfig(Integer userId, Map<String, Object> payload);

    Map<String, Object> testConnection(Integer userId);

    String chat(Integer userId, String question);
}
