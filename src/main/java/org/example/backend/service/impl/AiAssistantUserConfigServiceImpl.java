package org.example.backend.service.impl;

import org.example.backend.entity.AiAssistantUserConfigEntity;
import org.example.backend.mapper.AiAssistantUserConfigMapper;
import org.example.backend.service.AiAssistantUserConfigService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiAssistantUserConfigServiceImpl implements AiAssistantUserConfigService {
    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String DEFAULT_MODEL = "qwen-plus";
    private static final String DEFAULT_SYSTEM_PROMPT = "你是实验室协同管控平台的企业级 AI 助手，回答应专业、简洁、可执行。";

    private final AiAssistantUserConfigMapper configMapper;
    private final RestClient restClient;

    public AiAssistantUserConfigServiceImpl(AiAssistantUserConfigMapper configMapper) {
        this.configMapper = configMapper;
        this.restClient = RestClient.create();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getConfig(Integer userId) {
        requireUserId(userId);
        return toSafeResponse(resolveConfig(userId));
    }

    @Override
    @Transactional
    public Map<String, Object> saveConfig(Integer userId, Map<String, Object> payload) {
        requireUserId(userId);
        Map<String, Object> values = payload == null ? Map.of() : payload;
        AiAssistantUserConfigEntity current = configMapper.getByUserId(userId);

        AiAssistantUserConfigEntity config = new AiAssistantUserConfigEntity();
        config.setUserId(userId);
        config.setEnabled(booleanValue(values.get("enabled"), current != null && Boolean.TRUE.equals(current.getEnabled())));
        config.setBaseUrl(firstNonBlank(stringValue(values.get("baseUrl")), stringValue(values.get("base_url")), current == null ? DEFAULT_BASE_URL : current.getBaseUrl()));
        config.setModel(firstNonBlank(stringValue(values.get("model")), current == null ? DEFAULT_MODEL : current.getModel()));
        config.setSystemPrompt(firstNonBlank(stringValue(values.get("systemPrompt")), stringValue(values.get("system_prompt")), current == null ? DEFAULT_SYSTEM_PROMPT : current.getSystemPrompt()));

        String nextApiKey = firstNonBlank(stringValue(values.get("apiKey")), stringValue(values.get("api_key")));
        if (nextApiKey.isBlank() || "******".equals(nextApiKey)) {
            nextApiKey = current == null ? "" : firstNonBlank(current.getApiKey());
        }
        config.setApiKey(nextApiKey);

        configMapper.upsertConfig(config);
        return getConfig(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> testConnection(Integer userId) {
        requireUserId(userId);
        AiAssistantUserConfigEntity config = resolveConfig(userId);
        requireEnabledConfig(config);

        String answer = requestChatCompletion(config, "请只回复 OK，用于验证当前接口连接是否可用。");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connected", true);
        result.put("baseUrl", normalizeChatCompletionsUrl(config.getBaseUrl()).replaceAll("/chat/completions$", ""));
        result.put("model", firstNonBlank(config.getModel(), DEFAULT_MODEL));
        result.put("responsePreview", answer.length() > 80 ? answer.substring(0, 80) : answer);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public String chat(Integer userId, String question) {
        requireUserId(userId);
        AiAssistantUserConfigEntity config = resolveConfig(userId);
        requireEnabledConfig(config);
        return requestChatCompletion(config, question);
    }

    private void requireEnabledConfig(AiAssistantUserConfigEntity config) {
        if (!Boolean.TRUE.equals(config.getEnabled()) || firstNonBlank(config.getApiKey()).isBlank()) {
            throw new IllegalArgumentException("请先在智能辅助页面保存 AI 接口配置");
        }
    }

    private String requestChatCompletion(AiAssistantUserConfigEntity config, String question) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", firstNonBlank(config.getSystemPrompt(), DEFAULT_SYSTEM_PROMPT)),
                Map.of("role", "user", "content", firstNonBlank(question, "你好"))
        ));
        body.put("temperature", 0.7);

        Map<?, ?> response;
        try {
            response = restClient.post()
                    .uri(normalizeChatCompletionsUrl(config.getBaseUrl()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException exception) {
            throw new IllegalArgumentException(buildApiErrorMessage(exception));
        } catch (RestClientException exception) {
            throw new IllegalArgumentException("第三方 AI 接口连接失败，请检查 API 地址、Key、模型名称或服务器网络：" + exception.getMessage());
        }

        String answer = extractText(response);
        if (answer.isBlank()) {
            throw new IllegalArgumentException("AI 接口未返回可展示内容");
        }
        return answer;
    }

    private AiAssistantUserConfigEntity resolveConfig(Integer userId) {
        AiAssistantUserConfigEntity stored = configMapper.getByUserId(userId);
        if (stored != null) {
            return stored;
        }

        AiAssistantUserConfigEntity defaults = new AiAssistantUserConfigEntity();
        defaults.setUserId(userId);
        defaults.setEnabled(false);
        defaults.setBaseUrl(DEFAULT_BASE_URL);
        defaults.setApiKey("");
        defaults.setModel(DEFAULT_MODEL);
        defaults.setSystemPrompt(DEFAULT_SYSTEM_PROMPT);
        return defaults;
    }

    private Map<String, Object> toSafeResponse(AiAssistantUserConfigEntity config) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        response.put("baseUrl", firstNonBlank(config.getBaseUrl(), DEFAULT_BASE_URL));
        response.put("model", firstNonBlank(config.getModel(), DEFAULT_MODEL));
        response.put("systemPrompt", firstNonBlank(config.getSystemPrompt(), DEFAULT_SYSTEM_PROMPT));
        response.put("apiKeyConfigured", !firstNonBlank(config.getApiKey()).isBlank());
        return response;
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<?, ?> response) {
        Object choices = response == null ? null : response.get("choices");
        if (!(choices instanceof List<?> choiceList) || choiceList.isEmpty() || !(choiceList.get(0) instanceof Map<?, ?> choice)) {
            return "";
        }

        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> messageMap)) {
            return "";
        }

        Object content = messageMap.get("content");
        if (content instanceof List<?> contentList) {
            return contentList.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map((item) -> firstNonBlank(stringValue(item.get("text")), stringValue(item.get("content"))))
                    .filter((item) -> !item.isBlank())
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
        }
        return stringValue(content).trim();
    }

    private String normalizeChatCompletionsUrl(String baseUrl) {
        String url = firstNonBlank(baseUrl, DEFAULT_BASE_URL).replaceAll("/+$", "");
        return url.endsWith("/chat/completions") ? url : url + "/chat/completions";
    }

    private String buildApiErrorMessage(RestClientResponseException exception) {
        String responseBody = firstNonBlank(exception.getResponseBodyAsString());
        String message = extractJsonString(responseBody, "message");
        if (message.isBlank()) {
            message = extractJsonString(responseBody, "msg");
        }
        if (message.isBlank()) {
            message = responseBody;
        }
        if (message.length() > 240) {
            message = message.substring(0, 240);
        }
        return firstNonBlank(
                message,
                "第三方 AI 接口返回 " + exception.getStatusCode().value() + "，请检查 API 地址、Key、模型名称或图片能力"
        );
    }

    private String extractJsonString(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return "";
        }
        String pattern = "\"" + fieldName + "\"\\s*:\\s*\"";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (!matcher.find()) {
            return "";
        }
        StringBuilder value = new StringBuilder();
        for (int index = matcher.end(); index < json.length(); index++) {
            char current = json.charAt(index);
            if (current == '"' && (index == 0 || json.charAt(index - 1) != '\\')) {
                break;
            }
            value.append(current);
        }
        return value.toString()
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .trim();
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private void requireUserId(Integer userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("登录状态无效，请重新登录");
        }
    }
}
