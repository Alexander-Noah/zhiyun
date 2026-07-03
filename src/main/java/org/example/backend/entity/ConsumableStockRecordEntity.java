package org.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConsumableStockRecordEntity {
    private Long id;
    private Long consumableId;
    private String consumableName;
    private String category;
    private String type;
    private Integer quantity;
    private String unit;
    private Integer beforeStock;
    private Integer afterStock;
    private String operator;
    private String source;
    private String reason;
    private String remark;
    private String time;
    private LocalDateTime createdAt;
}
