package org.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceTransferRecordEntity {
    private Long id;
    private Long deviceId;
    private String deviceCode;
    private String deviceName;
    private String transferType;
    private Long fromLabId;
    private String fromLabName;
    private Long toLabId;
    private String toLabName;
    private Long fromOwnerUserId;
    private String fromOwnerName;
    private Long toOwnerUserId;
    private String toOwnerName;
    private String fromLocation;
    private String toLocation;
    private String reason;
    private String operatorName;
    private String transferAt;
    private LocalDateTime createdAt;
}
