package org.example.backend.service;

import org.vosk.Recognizer;

public interface SpeechRecognitionService {
    boolean isAvailable();

    Recognizer createRecognizer();

    String getResultText(Recognizer recognizer);

    String getPartialText(Recognizer recognizer);

    String getFinalText(Recognizer recognizer);
}
