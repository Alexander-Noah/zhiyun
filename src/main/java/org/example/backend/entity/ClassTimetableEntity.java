package org.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ClassTimetableEntity {
    private Long id;
    private String semester;
    private String rowClassName;
    private String className;
    private String weekday;
    private String sectionCode;
    private String sectionText;
    private Integer startSection;
    private Integer endSection;
    private String courseName;
    private String teacher;
    private String weekRaw;
    private String weekText;
    private String weekExpanded;
    private String classroom;
    private LocalDateTime createTime;
}
