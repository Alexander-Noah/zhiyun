package org.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiAssistantUserConfigEntity {
    private Long id;
    private Integer userId;
    private Boolean enabled;
    private String baseUrl;
    private String apiKey;
    private String model;
    private String visionModel;
    private String systemPrompt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
