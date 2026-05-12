package org.example.backend.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
public class UsageRecordEntity {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long id;
    private String person;
    private String resource;
    private String scene;
    @JsonAlias("time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime useTime;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getTime() {
        return useTime == null ? "" : useTime.format(TIME_FORMATTER);
    }

    public String getTagType() {
        if ("正常".equals(status) || "已归档".equals(status)) return "success";
        if ("异常".equals(status)) return "danger";
        if ("待复核".equals(status)) return "warning";
        return "primary";
    }
}
