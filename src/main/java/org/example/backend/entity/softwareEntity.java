package org.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class softwareEntity {
    private Long id;
    private Long labId;
    private String lab;
    private String labName;
    private String name;
    private String version;
    private String type;
    private String license;
    private String status;
    private String tagType;
    private String softwareName;
    private String softwareVersion;
    private String installPath;
    private String licenseInfo;
    private LocalDateTime installTime;
    private String remark;
}
