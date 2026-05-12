package org.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoticeEntity {
    private Long id;
    private String title;
    private String type;
    private String noticeType;
    private String target;
    private String targetRole;
    private String content;
    private String status;
    private String publishStatus;
    private LocalDateTime publishTime;
    private Long createdBy;
    private String tagType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
