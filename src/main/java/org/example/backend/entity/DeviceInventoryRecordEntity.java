package org.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceInventoryRecordEntity {
    private Long id;
    private Long deviceId;
    private String deviceCode;
    private String deviceName;
    private String inspectorName;
    private String inspectedAt;
    private String resultStatus;
    private Boolean normal;
    private Boolean missing;
    private Boolean damaged;
    private String remark;
    private LocalDateTime createdAt;
}
