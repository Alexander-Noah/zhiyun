package org.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConsumableEntity {
    private Long id;
    private String name;
    private String category;
    private Integer stock;
    private String unit;
    private String location;
    private Integer warnThreshold;
    private String status;
    private String tagType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
