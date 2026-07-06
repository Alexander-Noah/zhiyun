package org.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TeacherHostEntity {
    private Long id;
    private Long labId;
    private String teacherDeviceId;
    private String hostIp;
    private Integer port;
    private String status;
    private String token;
    private LocalDateTime lastHeartbeatTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
