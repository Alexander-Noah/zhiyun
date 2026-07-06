package org.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StudentClientEntity {
    private Long id;
    private Long labId;
    private String studentDeviceId;
    private String hostName;
    private String ipAddress;
    private String status;
    private String token;
    private LocalDateTime lastHeartbeatTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
