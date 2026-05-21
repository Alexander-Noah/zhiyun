package org.example.backend.websocket;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.service.AiAssistantService;
import org.example.backend.service.SpeechRecognitionService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.vosk.Recognizer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class AiAssistantWebSocketHandler extends AbstractWebSocketHandler {
    private static final TypeReference<Map<String, Object>> MESSAGE_TYPE = new TypeReference<>() {
    };

    private final AiAssistantService aiAssistantService;
    private final SpeechRecognitionService speechRecognitionService;
    private final ObjectMapper objectMapper;

    public AiAssistantWebSocketHandler(
            AiAssistantService aiAssistantService,
            SpeechRecognitionService speechRecognitionService,
            ObjectMapper objectMapper
    ) {
        this.aiAssistantService = aiAssistantService;
        this.speechRecognitionService = speechRecognitionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.getAttributes().put("sessionId", UUID.randomUUID().toString());
        log.info("AI assistant websocket connected session={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> payload = readMessage(message.getPayload());
        String type = asString(payload.get("type"));
        String state = asString(payload.get("state"));

        if ("hello".equals(type)) {
            sendJson(session, Map.of(
                    "type", "hello",
                    "session_id", session.getAttributes().get("sessionId")
            ));
            return;
        }

        if ("abort".equals(type)) {
            closeRecognizer(session);
            sendJson(session, Map.of("type", "tts", "state", "stop"));
            return;
        }

        if ("listen".equals(type) && "start".equals(state)) {
            startSpeechRecognition(session);
            return;
        }

        if ("listen".equals(type) && "stop".equals(state)) {
            stopSpeechRecognition(session);
            return;
        }

        if ("listen".equals(type) && "detect".equals(state)) {
            String question = asString(payload.get("text"));
            sendAnswer(session, question, asString(session.getAttributes().get("sessionId")), asImageList(payload.get("images")));
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        Object recognizerValue = session.getAttributes().get("recognizer");
        if (!(recognizerValue instanceof Recognizer recognizer)) {
            return;
        }

        ByteBuffer payload = message.getPayload();
        byte[] bytes = new byte[payload.remaining()];
        payload.get(bytes);
        if (recognizer.acceptWaveForm(bytes, bytes.length)) {
            appendTranscriptSegment(session, speechRecognitionService.getResultText(recognizer));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeRecognizer(session);
        log.info("AI assistant websocket closed session={} status={}", session.getId(), status);
    }

    private void startSpeechRecognition(WebSocketSession session) throws Exception {
        closeRecognizer(session);
        if (!speechRecognitionService.isAvailable()) {
            sendJson(session, Map.of(
                    "type", "stt",
                    "state", "error",
                    "text", "语音识别服务暂不可用，请检查后端 Vosk 模型路径配置"
            ));
            return;
        }
        session.getAttributes().put("recognizer", speechRecognitionService.createRecognizer());
        session.getAttributes().put("transcriptSegments", new ArrayList<String>());
        sendJson(session, Map.of("type", "listen", "state", "start"));
    }

    private void stopSpeechRecognition(WebSocketSession session) throws Exception {
        Object recognizerValue = session.getAttributes().remove("recognizer");
        @SuppressWarnings("unchecked")
        List<String> transcriptSegments = (List<String>) session.getAttributes().remove("transcriptSegments");
        if (!(recognizerValue instanceof Recognizer recognizer)) {
            sendJson(session, Map.of("type", "stt", "state", "empty", "text", ""));
            return;
        }

        try (recognizer) {
            String partialTranscript = speechRecognitionService.getPartialText(recognizer);
            appendTranscriptSegment(transcriptSegments, speechRecognitionService.getFinalText(recognizer));
            String transcript = joinTranscript(transcriptSegments);
            if (transcript.isBlank()) {
                transcript = partialTranscript;
            }
            sendJson(session, Map.of("type", "stt", "state", "final", "text", transcript));
            if (transcript.isBlank()) {
                sendJson(session, Map.of("type", "text", "text", "未识别到有效语音内容，请重新输入。"));
                return;
            }
        }
    }

    private void closeRecognizer(WebSocketSession session) {
        Object recognizerValue = session.getAttributes().remove("recognizer");
        if (recognizerValue instanceof Recognizer recognizer) {
            recognizer.close();
        }
        session.getAttributes().remove("transcriptSegments");
    }

    private void sendAnswer(
            WebSocketSession session,
            String question,
            String agentSessionId,
            List<Map<String, Object>> images
    ) throws Exception {
        String answer = aiAssistantService.generateAnswer(question, agentSessionId, images);
        sendJson(session, Map.of("type", "tts", "state", "start"));
        sendJson(session, Map.of("type", "tts", "state", "sentence_start", "text", answer));
        sendJson(session, Map.of("type", "tts", "state", "stop"));
    }

    private Map<String, Object> readMessage(String payload) {
        try {
            return objectMapper.readValue(payload, MESSAGE_TYPE);
        } catch (Exception exception) {
            return Map.of(
                    "type", "listen",
                    "state", "detect",
                    "text", payload
            );
        }
    }

    private void sendJson(WebSocketSession session, Map<String, Object> payload) throws Exception {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asImageList(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }

        List<Map<String, Object>> images = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> map) {
                images.add((Map<String, Object>) map);
            }
        }
        return images;
    }

    @SuppressWarnings("unchecked")
    private void appendTranscriptSegment(WebSocketSession session, String text) {
        List<String> transcriptSegments = (List<String>) session.getAttributes().get("transcriptSegments");
        appendTranscriptSegment(transcriptSegments, text);
    }

    private void appendTranscriptSegment(List<String> transcriptSegments, String text) {
        if (transcriptSegments == null || text == null || text.isBlank()) {
            return;
        }
        transcriptSegments.add(normalizeTranscript(text));
    }

    private String joinTranscript(List<String> transcriptSegments) {
        if (transcriptSegments == null || transcriptSegments.isEmpty()) {
            return "";
        }
        return normalizeTranscript(String.join(" ", transcriptSegments));
    }

    private String normalizeTranscript(String text) {
        return text
                .trim()
                .replaceAll("\\s+", " ")
                .replaceAll("(?<=[\\p{IsHan}])\\s+(?=[\\p{IsHan}])", "");
    }
}
