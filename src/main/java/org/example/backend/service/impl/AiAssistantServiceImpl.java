package org.example.backend.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.service.AiAssistantService;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.TongyiAgentService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiAssistantServiceImpl implements AiAssistantService {
    private final TongyiAgentService tongyiAgentService;
    private final BusinessLoopService businessLoopService;

    public AiAssistantServiceImpl(TongyiAgentService tongyiAgentService, BusinessLoopService businessLoopService) {
        this.tongyiAgentService = tongyiAgentService;
        this.businessLoopService = businessLoopService;
    }

    @Override
    public String generateAnswer(String question, String scene) {
        return generateAnswer(question, scene, List.of());
    }

    @Override
    public String generateAnswer(String question, String scene, List<Map<String, Object>> images) {
        if (!tongyiAgentService.isConfigured()) {
            recordAiEvent(scene, question, "\u672a\u914d\u7f6e");
            return "\u901a\u4e49\u667a\u80fd\u4f53\u5c1a\u672a\u5728\u540e\u7aef\u914d\u7f6e\uff0c\u8bf7\u5728\u670d\u52a1\u5668\u73af\u5883\u53d8\u91cf\u6216 application.yaml \u4e2d\u914d\u7f6e TONGYI_API_KEY \u548c TONGYI_AGENT_APP_ID\u3002";
        }

        try {
            String answer = tongyiAgentService.callAgent(question, scene, images == null ? List.of() : images);
            recordAiEvent(scene, question, "\u5df2\u56de\u590d");
            return answer;
        } catch (Exception exception) {
            log.warn("Tongyi agent call failed", exception);
            recordAiEvent(scene, question, "\u8c03\u7528\u5931\u8d25");
            if (images != null && !images.isEmpty()) {
                return "图片识别调用失败，请确认通义视觉模型已启用，或在 application.yaml / 环境变量中配置 TONGYI_VISION_MODEL。";
            }
            return "\u901a\u4e49\u667a\u80fd\u4f53\u8c03\u7528\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u5b66\u6821\u670d\u52a1\u5668\u7f51\u7edc\u3001\u516c\u53f8 API Key\u3001\u667a\u80fd\u4f53 APP_ID \u548c\u963f\u91cc\u4e91\u767e\u70bc\u670d\u52a1\u72b6\u6001\u3002";
        }
    }

    private void recordAiEvent(String scene, String question, String status) {
        businessLoopService.recordEvent("ai-assistant", "consult", firstNonBlank(scene, "\u667a\u80fd\u95ee\u7b54"), status, Map.of(
                "scene", firstNonBlank(scene, "default"),
                "questionLength", question == null ? 0 : question.length()
        ));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
