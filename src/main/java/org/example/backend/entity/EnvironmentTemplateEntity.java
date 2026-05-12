package org.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EnvironmentTemplateEntity {
    private Long id;
    private String name;
    private String course;
    private String os;
    private String softwareList;
    private String labs;
    private String status;
    private String tagType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
