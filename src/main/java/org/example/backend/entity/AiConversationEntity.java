package org.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiConversationEntity {
    private Long id;
    private Integer userId;
    private String conversationId;
    private String title;
    private String preview;
    private Long updatedAtMillis;
    private String messagesJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
