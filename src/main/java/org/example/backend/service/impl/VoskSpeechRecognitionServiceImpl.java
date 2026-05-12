package org.example.backend.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.service.SpeechRecognitionService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Service
public class VoskSpeechRecognitionServiceImpl implements SpeechRecognitionService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final float SAMPLE_RATE = 16000.0f;
    private static final String DEFAULT_MODEL_PATH = "../vosk-model-small-cn-0.22/vosk-model-small-cn-0.22";

    private final ObjectMapper objectMapper;
    private final Model model;

    public VoskSpeechRecognitionServiceImpl(ObjectMapper objectMapper, Environment environment) {
        this.objectMapper = objectMapper;
        this.model = loadModel(environment);
    }

    @Override
    public boolean isAvailable() {
        return model != null;
    }

    @Override
    public Recognizer createRecognizer() {
        if (model == null) {
            throw new IllegalStateException("Vosk speech model is not available");
        }
        try {
            return new Recognizer(model, SAMPLE_RATE);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create Vosk recognizer", exception);
        }
    }

    @Override
    public String getResultText(Recognizer recognizer) {
        if (recognizer == null) {
            return "";
        }
        return readText(recognizer.getResult(), "text", "Vosk result");
    }

    @Override
    public String getPartialText(Recognizer recognizer) {
        if (recognizer == null) {
            return "";
        }
        return readText(recognizer.getPartialResult(), "partial", "Vosk partial result");
    }

    @Override
    public String getFinalText(Recognizer recognizer) {
        if (recognizer == null) {
            return "";
        }
        return readText(recognizer.getFinalResult(), "text", "Vosk final result");
    }

    private String readText(String resultPayload, String textKey, String resultName) {
        try {
            Map<String, Object> result = objectMapper.readValue(resultPayload, MAP_TYPE);
            Object text = result.get(textKey);
            return text == null ? "" : String.valueOf(text).trim();
        } catch (Exception exception) {
            log.warn("Failed to parse {}", resultName, exception);
            return "";
        }
    }

    private Model loadModel(Environment environment) {
        String configuredPath = firstNonBlank(
                environment.getProperty("speech.vosk.model-path"),
                System.getenv("VOSK_MODEL_PATH"),
                DEFAULT_MODEL_PATH
        );
        Path modelPath = Path.of(configuredPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(modelPath)) {
            log.warn("Vosk model path does not exist: {}", modelPath);
            return null;
        }

        if (hasNonAscii(modelPath.toString())) {
            log.info("Vosk model path contains non-ASCII characters, loading from ASCII temp path");
            return loadFromAsciiTempPath(modelPath);
        }

        try {
            log.info("Loading Vosk model from {}", modelPath);
            return new Model(modelPath.toString());
        } catch (Exception exception) {
            log.warn("Failed to load Vosk model from {}, trying ASCII temp path", modelPath);
            log.debug("Vosk model load failure", exception);
            return loadFromAsciiTempPath(modelPath);
        }
    }

    private Model loadFromAsciiTempPath(Path sourcePath) {
        Path targetPath = Path.of(System.getProperty("java.io.tmpdir"), "smart-lab-vosk-model-cn-0.22").toAbsolutePath().normalize();
        try {
            copyDirectory(sourcePath, targetPath);
            log.info("Loading Vosk model from copied path {}", targetPath);
            return new Model(targetPath.toString());
        } catch (Exception exception) {
            log.warn("Failed to load Vosk model from copied path {}", targetPath, exception);
            return null;
        }
    }

    private void copyDirectory(Path sourcePath, Path targetPath) throws Exception {
        if (Files.exists(targetPath.resolve("am").resolve("final.mdl"))) {
            return;
        }
        Files.createDirectories(targetPath);
        try (Stream<Path> stream = Files.walk(sourcePath)) {
            for (Path source : stream.toList()) {
                Path target = targetPath.resolve(sourcePath.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return DEFAULT_MODEL_PATH;
    }

    private boolean hasNonAscii(String value) {
        return value != null && !value.chars().allMatch(character -> character < 128);
    }
}
