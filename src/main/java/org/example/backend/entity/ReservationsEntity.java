package org.example.backend.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
public class ReservationsEntity {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private Long id;
    private Long applicantUserId;
    @JsonAlias("applicant")
    private String applicantName;
    private String department;
    private String contact;
    private Long labId;
    @JsonAlias("date")
    private LocalDate reservationDate;
    private String timeRange;
    private String scene;
    @JsonAlias("attendees")
    private Integer attendeeCount;
    private String reason;
    private String status;
    private Integer conflictFlag;
    @JsonAlias("reviewer")
    private String reviewerName;
    @JsonAlias("note")
    private String reviewRemark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @JsonAlias("lab")
    private String labName;
    private String labCode;

    public String getApplicant() {
        return applicantName;
    }

    public String getLab() {
        if (labName != null && !labName.isBlank()) {
            if (labCode == null || labCode.isBlank() || labName.startsWith(labCode)) {
                return labName;
            }
            return labCode + " " + labName;
        }
        return labId == null ? "" : String.valueOf(labId);
    }

    public String getDate() {
        return reservationDate == null ? "" : reservationDate.format(DATE_FORMATTER);
    }

    public Integer getAttendees() {
        return attendeeCount;
    }

    public String getTagType() {
        if ("已通过".equals(status)) return "success";
        if ("已完成".equals(status)) return "success";
        if ("冲突".equals(status)) return "danger";
        if ("已驳回".equals(status) || "已取消".equals(status) || "已过期".equals(status)) return "info";
        return "warning";
    }

    public Boolean getConflict() {
        return conflictFlag != null && conflictFlag == 1;
    }

    public void setConflict(Boolean conflict) {
        this.conflictFlag = Boolean.TRUE.equals(conflict) ? 1 : 0;
    }

    public String getReviewer() {
        return reviewerName == null || reviewerName.isBlank() ? "待审核" : reviewerName;
    }

    public String getSubmittedAt() {
        return createdAt == null ? "" : createdAt.format(TIME_FORMATTER);
    }

    public String getReviewedAt() {
        if (updatedAt == null || reviewerName == null || "待审核".equals(reviewerName)) {
            return "-";
        }
        return updatedAt.format(TIME_FORMATTER);
    }

    public String getNote() {
        return reviewRemark;
    }
}
