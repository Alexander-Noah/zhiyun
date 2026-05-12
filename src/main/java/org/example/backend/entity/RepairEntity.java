package org.example.backend.entity;

import lombok.Data;

@Data
public class RepairEntity {
    private String id;
    private String ticket;
    private String reporter;
    private String contact;
    private String lab;
    private String device;
    private String faultType;
    private String priority;
    private String status;
    private String tagType;
    private String assignee;
    private String submittedAt;
    private String deadline;
    private Integer progress;
    private String description;
    private String result;
    private String lastUpdate;
}
