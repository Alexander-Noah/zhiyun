package org.example.backend.service;

import java.util.Map;

public interface TongyiAgentService {
    Map<String, Object> getConfig();

    Map<String, Object> updateConfig(Map<String, Object> payload);

    boolean isConfigured();

    String callAgent(String question, String sessionId);
}
