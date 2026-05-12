package org.example.backend.entity;

import lombok.Data;

@Data
public class AcademicScheduleCourse {
    private String courseName;
    private String teacherName;
    private String className;
    private String weekLabel;
    private String dayLabel;
    private String sectionLabel;
    private String timeRange;
    private String useTime;
    private String labName;
    private String rawText;
}
