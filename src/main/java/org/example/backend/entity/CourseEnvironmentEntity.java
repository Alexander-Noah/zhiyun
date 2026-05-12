package org.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseEnvironmentEntity {
    private Integer id;
    private String course;
    private String courseName;
    private Integer teacherUserId;
    private String teacher;
    private String teacherName;
    private String teacherUsername;
    private String className;
    private String useTime;
    private String labType;
    private String software;
    private String softwareRequirements;
    private String systemRequirements;
    private String specialRequirements;
    private String status;
    private String processStatus;
    private Long assignedLabId;
    private String assignedLabName;
    private String assignedLabCode;
    private String confirmStatus;
    private String tagType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
