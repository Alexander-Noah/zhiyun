package org.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScheduleAdjustmentEntity {
    private Long id;
    private String course;
    private String originalTime;
    private String targetTime;
    private String reason;
    private String status;
    private String tagType;
    private Integer flowStep;
    private String reviewer;
    private String teacherName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
