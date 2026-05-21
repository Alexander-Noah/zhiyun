package org.example.backend.service;

import java.util.List;
import java.util.Map;

public interface AiAssistantService {
    String generateAnswer(String question, String scene);

    String generateAnswer(String question, String scene, List<Map<String, Object>> images);
}
