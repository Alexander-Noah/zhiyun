package org.example.backend.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.service.TongyiAgentService;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class TongyiAgentServiceImpl implements TongyiAgentService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Path CONFIG_PATH = Path.of("data", "tongyi-api-config.json");
    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String DEFAULT_MODEL = "qwen-plus";

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Environment environment;
    private Map<String, Object> fileConfig;

    public TongyiAgentServiceImpl(ObjectMapper objectMapper, Environment environment) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
        this.environment = environment;
        this.fileConfig = loadFileConfig();
    }

    @Override
    public synchronized Map<String, Object> getConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configured", isConfigured());
        data.put("base_url", getBaseUrl());
        data.put("model", getModel());
        data.put("api_key_configured", !getApiKey().isBlank());
        return data;
    }

    @Override
    public synchronized Map<String, Object> updateConfig(Map<String, Object> payload) {
        putIfPresent(payload, "api_key");
        putIfPresent(payload, "base_url");
        putIfPresent(payload, "model");
        putIfPresent(payload, "enabled");
        saveFileConfig();
        return getConfig();
    }

    @Override
    public synchronized boolean isConfigured() {
        return getEnabled() && !getApiKey().isBlank();
    }

    @Override
    public String callAgent(String question, String sessionId) {
        if (!isConfigured()) {
            throw new IllegalStateException("Tongyi API key is not configured");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", "你是实验室协同管控平台的企业级AI助手，回答应专业、简洁、可执行。"),
                Map.of("role", "user", "content", question == null ? "" : question)
        ));
        body.put("temperature", 0.7);

        String responseBody = restClient.post()
                .uri(trimTrailingSlash(getBaseUrl()) + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("Tongyi API returned empty response");
        }

        try {
            Map<String, Object> response = objectMapper.readValue(responseBody, MAP_TYPE);
            return extractText(response);
        } catch (Exception exception) {
            log.warn("Failed to parse Tongyi API response", exception);
            throw new IllegalStateException("Failed to parse Tongyi API response", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        Object choices = response.get("choices");
        if (choices instanceof List<?> choiceList && !choiceList.isEmpty() && choiceList.get(0) instanceof Map<?, ?> choice) {
            Object message = ((Map<String, Object>) choice).get("message");
            if (message instanceof Map<?, ?> messageMap) {
                Object content = ((Map<String, Object>) messageMap).get("content");
                if (content != null && !String.valueOf(content).isBlank()) {
                    return String.valueOf(content);
                }
            }
        }
        throw new IllegalStateException("Tongyi API response does not contain displayable text");
    }

    private Map<String, Object> loadFileConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                return objectMapper.readValue(Files.readString(CONFIG_PATH), MAP_TYPE);
            }
        } catch (Exception exception) {
            log.warn("Failed to load Tongyi API config file", exception);
        }

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("enabled", true);
        defaults.put("base_url", DEFAULT_BASE_URL);
        defaults.put("model", DEFAULT_MODEL);
        defaults.put("api_key", "");
        return defaults;
    }

    private void putIfPresent(Map<String, Object> payload, String key) {
        if (!payload.containsKey(key)) {
            return;
        }
        Object value = payload.get(key);
        if ("api_key".equals(key) && "******".equals(String.valueOf(value))) {
            return;
        }
        fileConfig.put(key, value);
    }

    private void saveFileConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, objectMapper.writeValueAsString(fileConfig));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save Tongyi API config", exception);
        }
    }

    private boolean getEnabled() {
        String value = getProperty("tongyi.agent.enabled", "TONGYI_AGENT_ENABLED", asString(fileConfig.get("enabled"), "true"));
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private String getBaseUrl() {
        return getProperty("tongyi.agent.base-url", "TONGYI_AGENT_BASE_URL", asString(fileConfig.get("base_url"), DEFAULT_BASE_URL));
    }

    private String getModel() {
        return getProperty("tongyi.agent.model", "TONGYI_MODEL", asString(fileConfig.get("model"), DEFAULT_MODEL));
    }

    private String getApiKey() {
        String value = environment.getProperty("tongyi.agent.api-key");
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getenv("TONGYI_API_KEY");
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getenv("DASHSCOPE_API_KEY");
        if (value != null && !value.isBlank()) {
            return value;
        }
        return asString(fileConfig.get("api_key"), "");
    }

    private String getProperty(String propertyName, String envName, String defaultValue) {
        String value = environment.getProperty(propertyName);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getenv(envName);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_BASE_URL;
        }
        return value.replaceAll("/+$", "");
    }

    private String asString(Object value, String defaultValue) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return String.valueOf(value);
    }
}
