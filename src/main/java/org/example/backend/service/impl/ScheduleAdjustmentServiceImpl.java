package org.example.backend.service.impl;

import org.example.backend.entity.NoticeEntity;
import org.example.backend.entity.ScheduleAdjustmentEntity;
import org.example.backend.mapper.NoticeMapper;
import org.example.backend.mapper.ScheduleAdjustmentMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.ScheduleAdjustmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ScheduleAdjustmentServiceImpl implements ScheduleAdjustmentService {
    private final ScheduleAdjustmentMapper scheduleAdjustmentMapper;
    private final NoticeMapper noticeMapper;
    private final BusinessLoopService businessLoopService;

    public ScheduleAdjustmentServiceImpl(
            ScheduleAdjustmentMapper scheduleAdjustmentMapper,
            NoticeMapper noticeMapper,
            BusinessLoopService businessLoopService
    ) {
        this.scheduleAdjustmentMapper = scheduleAdjustmentMapper;
        this.noticeMapper = noticeMapper;
        this.businessLoopService = businessLoopService;
    }

    @Override
    public List<ScheduleAdjustmentEntity> listAdjustments() {
        List<ScheduleAdjustmentEntity> adjustments = scheduleAdjustmentMapper.listAdjustments();
        return adjustments == null ? List.of() : adjustments;
    }

    @Override
    @Transactional
    public ScheduleAdjustmentEntity createAdjustment(ScheduleAdjustmentEntity adjustment) {
        normalizeAdjustment(adjustment);
        scheduleAdjustmentMapper.insertAdjustment(adjustment);
        ScheduleAdjustmentEntity savedAdjustment = scheduleAdjustmentMapper.getAdjustment(adjustment.getId());
        publishNotice(savedAdjustment, "调课申请已提交", "教师已提交调课申请，等待教务或实验室管理员审核。", "labAdmin");
        recordEvent("submit", savedAdjustment);
        return savedAdjustment;
    }

    @Override
    @Transactional
    public ScheduleAdjustmentEntity updateAdjustment(Long id, ScheduleAdjustmentEntity adjustment) {
        normalizeAdjustment(adjustment);
        int updatedCount = scheduleAdjustmentMapper.updateAdjustment(id, adjustment);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("schedule adjustment not found");
        }
        ScheduleAdjustmentEntity savedAdjustment = scheduleAdjustmentMapper.getAdjustment(id);
        recordEvent("update", savedAdjustment);
        return savedAdjustment;
    }

    @Override
    @Transactional
    public ScheduleAdjustmentEntity approveAdjustment(Long id) {
        return patchStatus(id, "已通过", "success", 3, "调课申请已通过", "调课申请已审核通过，等待同步课表和实验室安排。");
    }

    @Override
    @Transactional
    public ScheduleAdjustmentEntity completeAdjustment(Long id) {
        return patchStatus(id, "已调整", "primary", 4, "调课已同步", "调课申请已执行完成，教师课表和实验室安排已同步。");
    }

    private ScheduleAdjustmentEntity patchStatus(Long id, String status, String tagType, Integer flowStep, String noticeTitle, String noticeContent) {
        int updatedCount = scheduleAdjustmentMapper.patchAdjustment(id, status, tagType, flowStep);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("schedule adjustment not found");
        }
        ScheduleAdjustmentEntity savedAdjustment = scheduleAdjustmentMapper.getAdjustment(id);
        publishNotice(savedAdjustment, noticeTitle, noticeContent, "teacher");
        recordEvent(status.contains("调整") ? "complete" : "approve", savedAdjustment);
        return savedAdjustment;
    }

    private void normalizeAdjustment(ScheduleAdjustmentEntity adjustment) {
        if (adjustment.getCourse() == null || adjustment.getCourse().isBlank()) {
            adjustment.setCourse("未命名课程");
        }
        if (adjustment.getOriginalTime() == null || adjustment.getOriginalTime().isBlank()) {
            adjustment.setOriginalTime("待确认");
        }
        if (adjustment.getTargetTime() == null || adjustment.getTargetTime().isBlank()) {
            adjustment.setTargetTime("待确认");
        }
        if (adjustment.getReason() == null || adjustment.getReason().isBlank()) {
            adjustment.setReason("教学安排调整");
        }
        if (adjustment.getStatus() == null || adjustment.getStatus().isBlank()) {
            adjustment.setStatus("审核中");
        }
        if (adjustment.getTagType() == null || adjustment.getTagType().isBlank()) {
            adjustment.setTagType("warning");
        }
        if (adjustment.getFlowStep() == null) {
            adjustment.setFlowStep(2);
        }
        if (adjustment.getReviewer() == null || adjustment.getReviewer().isBlank()) {
            adjustment.setReviewer("教务处");
        }
        if (adjustment.getTeacherName() == null || adjustment.getTeacherName().isBlank()) {
            adjustment.setTeacherName("任课教师");
        }
    }

    private void publishNotice(ScheduleAdjustmentEntity adjustment, String title, String content, String targetRole) {
        if (adjustment == null) {
            return;
        }
        NoticeEntity notice = new NoticeEntity();
        notice.setTitle(title + "：" + adjustment.getCourse());
        notice.setNoticeType("调课申请");
        notice.setType("调课申请");
        notice.setTargetRole(targetRole);
        notice.setTarget(targetRole);
        notice.setContent(content + " 原时间：" + adjustment.getOriginalTime() + "，目标时间：" + adjustment.getTargetTime() + "。");
        notice.setPublishStatus("已发布");
        notice.setStatus("已发布");
        notice.setPublishTime(LocalDateTime.now());
        noticeMapper.insertNotice(notice);
    }

    private void recordEvent(String action, ScheduleAdjustmentEntity adjustment) {
        if (adjustment == null) {
            return;
        }
        businessLoopService.recordEvent("schedule-adjustment", action, adjustment.getCourse(), adjustment.getStatus(), Map.of(
                "id", adjustment.getId(),
                "originalTime", adjustment.getOriginalTime(),
                "targetTime", adjustment.getTargetTime(),
                "reviewer", adjustment.getReviewer()
        ));
    }
}
