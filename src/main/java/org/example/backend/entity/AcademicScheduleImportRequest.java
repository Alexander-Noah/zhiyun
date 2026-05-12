package org.example.backend.entity;

import lombok.Data;

@Data
public class AcademicScheduleImportRequest {
    private String username;
    private String password;
    private String randomCode;
    private String credentialKey;
    private Boolean saveCredential;
    private Boolean useSavedCredential;
    private String cookie;
    private String html;
    private String baseUrl;
    private String schedulePath;
    private String term;
    private String weekLabel;
    private String teacherName;
    private Integer teacherUserId;
    private String className;
    private String labType;
    private Boolean importToCourseEnvironments;
}
