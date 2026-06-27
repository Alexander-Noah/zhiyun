package org.example.backend.service.impl;

import org.example.backend.entity.NoticeEntity;
import org.example.backend.entity.NoticeRecipientEntity;
import org.example.backend.mapper.NoticeMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.NoticeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NoticeServiceImpl implements NoticeService {
    private final NoticeMapper noticeMapper;
    private final BusinessLoopService businessLoopService;

    public NoticeServiceImpl(NoticeMapper noticeMapper, BusinessLoopService businessLoopService) {
        this.noticeMapper = noticeMapper;
        this.businessLoopService = businessLoopService;
    }

    @Override
    public List<NoticeEntity> listNotices() {
        return noticeMapper.listNotices();
    }

    @Override
    @Transactional
    public NoticeEntity createNotice(NoticeEntity notice) {
        NoticeEntity normalizedNotice = normalizeNotice(notice);
        noticeMapper.insertNotice(normalizedNotice);
        NoticeEntity savedNotice = noticeMapper.getNotice(normalizedNotice.getId());
        refreshRecipientsIfPublished(savedNotice);
        recordNoticeEvent("create", savedNotice);
        return noticeMapper.getNotice(savedNotice.getId());
    }

    @Override
    @Transactional
    public NoticeEntity updateNotice(Long id, NoticeEntity notice) {
        noticeMapper.updateNotice(id, normalizeNotice(notice));
        NoticeEntity savedNotice = noticeMapper.getNotice(id);
        refreshRecipientsIfPublished(savedNotice);
        recordNoticeEvent("update", savedNotice);
        return noticeMapper.getNotice(id);
    }

    @Override
    @Transactional
    public List<NoticeEntity> deleteNotice(Long id) {
        NoticeEntity notice = noticeMapper.getNotice(id);
        noticeMapper.logicalDeleteNotice(id);
        businessLoopService.recordEvent("notice", "delete", notice == null ? String.valueOf(id) : notice.getTitle(), "已删除", Map.of("id", id));
        return noticeMapper.listNotices();
    }

    @Override
    @Transactional
    public List<NoticeEntity> replaceNotices(List<NoticeEntity> notices) {
        noticeMapper.deleteAllRecipients();
        noticeMapper.deleteAllNotices();
        for (NoticeEntity notice : notices == null ? Collections.<NoticeEntity>emptyList() : notices) {
            NoticeEntity normalizedNotice = normalizeNotice(notice);
            noticeMapper.insertNotice(normalizedNotice);
            refreshRecipientsIfPublished(noticeMapper.getNotice(normalizedNotice.getId()));
        }
        businessLoopService.recordEvent("notice", "batch-save", "通知公告", "已同步", Map.of("count", notices == null ? 0 : notices.size()));
        return noticeMapper.listNotices();
    }

    @Override
    @Transactional
    public List<NoticeEntity> resetNotices() {
        noticeMapper.deleteAllRecipients();
        noticeMapper.deleteAllNotices();
        for (NoticeEntity notice : getDefaultNotices()) {
            noticeMapper.insertNotice(normalizeNotice(notice));
            refreshRecipientsIfPublished(noticeMapper.getNotice(notice.getId()));
        }
        businessLoopService.recordEvent("notice", "reset", "通知公告", "已恢复初始公告", Map.of("count", getDefaultNotices().size()));
        return noticeMapper.listNotices();
    }

    @Override
    @Transactional
    public NoticeEntity publishNotice(Long id) {
        noticeMapper.publishNotice(id);
        NoticeEntity notice = noticeMapper.getNotice(id);
        refreshRecipientsIfPublished(notice);
        recordNoticeEvent("publish", notice);
        return noticeMapper.getNotice(id);
    }

    @Override
    @Transactional
    public NoticeEntity withdrawNotice(Long id) {
        noticeMapper.updateNoticeStatus(id, "已撤回");
        NoticeEntity notice = noticeMapper.getNotice(id);
        recordNoticeEvent("withdraw", notice);
        return notice;
    }

    @Override
    @Transactional
    public NoticeEntity archiveNotice(Long id) {
        noticeMapper.archiveNotice(id);
        NoticeEntity notice = noticeMapper.getNotice(id);
        recordNoticeEvent("archive", notice);
        return notice;
    }

    @Override
    public List<NoticeRecipientEntity> listRecipients(Long noticeId) {
        return noticeMapper.listRecipients(noticeId);
    }

    @Override
    @Transactional
    public List<NoticeRecipientEntity> listUserNotices(Integer userId, String roleCode, String readStatus) {
        syncUserRecipients(userId, roleCode);
        return noticeMapper.listUserNotices(userId, readStatus);
    }

    @Override
    @Transactional
    public NoticeRecipientEntity markRead(Long noticeId, Integer userId, String roleCode) {
        syncUserRecipients(userId, roleCode);
        noticeMapper.markRead(noticeId, userId);
        return noticeMapper.listUserNotices(userId, null).stream()
                .filter(item -> noticeId != null && noticeId.equals(item.getNoticeId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getNoticeStats(Integer userId, String roleCode) {
        return noticeMapper.getNoticeStats(userId);
    }

    @Override
    @Transactional
    public Map<String, Object> syncBusinessReminders() {
        List<NoticeEntity> notices = new ArrayList<>();

        for (Map<String, Object> item : noticeMapper.listLowStockConsumables()) {
            notices.add(buildBusinessNotice(
                    String.valueOf(item.get("name")) + "库存预警",
                    "库存预警",
                    "labAdmin",
                    "consumable",
                    String.valueOf(item.get("id")),
                    "当前库存 " + item.get("stock") + firstNonBlank(String.valueOf(item.get("unit")), "件")
                            + "，低于预警阈值 " + item.get("warnThreshold") + "，请及时补货。"
            ));
        }

        for (Map<String, Object> item : noticeMapper.listPendingReservations()) {
            notices.add(buildBusinessNotice(
                    "预约审批待处理：" + firstNonBlank(String.valueOf(item.get("applicantName")), "预约申请"),
                    "预约审批",
                    "labAdmin",
                    "reservation",
                    String.valueOf(item.get("id")),
                    "申请实验室：" + firstNonBlank(String.valueOf(item.get("labName")), "-")
                            + "，时间：" + item.get("reservationDate") + " " + item.get("timeRange")
                            + "，状态：" + item.get("status") + "。"
            ));
        }

        for (Map<String, Object> item : noticeMapper.listActiveRepairs()) {
            notices.add(buildBusinessNotice(
                    "设备维修待处理：" + firstNonBlank(String.valueOf(item.get("ticket")), "维修工单"),
                    "设备维修",
                    "maintenance",
                    "repair",
                    String.valueOf(item.get("id")),
                    "设备：" + firstNonBlank(String.valueOf(item.get("deviceName")), "-")
                            + "，故障类型：" + firstNonBlank(String.valueOf(item.get("faultType")), "-")
                            + "，状态：" + item.get("status") + "。"
            ));
        }

        int publishedCount = 0;
        for (NoticeEntity notice : notices) {
            if (noticeMapper.countNoticeBySource(notice.getSourceModule(), notice.getSourceId()) > 0) {
                continue;
            }
            noticeMapper.insertNotice(normalizeNotice(notice));
            refreshRecipientsIfPublished(noticeMapper.getNotice(notice.getId()));
            publishedCount++;
        }

        businessLoopService.recordEvent("notice", "sync-business-reminders", "业务提醒", "已同步", Map.of("count", publishedCount));
        return Map.of("created", publishedCount);
    }

    private void recordNoticeEvent(String action, NoticeEntity notice) {
        if (notice == null) {
            return;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", notice.getId());
        details.put("type", firstNonBlank(notice.getNoticeType(), notice.getType()));
        details.put("target", firstNonBlank(notice.getTargetRole(), notice.getTarget()));
        businessLoopService.recordEvent("notice", action, firstNonBlank(notice.getTitle(), "通知公告"), firstNonBlank(notice.getPublishStatus(), notice.getStatus()), details);
    }

    private void refreshRecipientsIfPublished(NoticeEntity notice) {
        if (notice == null || notice.getId() == null || !"已发布".equals(firstNonBlank(notice.getPublishStatus(), notice.getStatus()))) {
            return;
        }

        noticeMapper.deleteRecipientsByNotice(notice.getId());
        noticeMapper.insertRecipientsForRole(notice.getId(), normalizeTargetRole(firstNonBlank(notice.getTargetRole(), notice.getTarget())));
    }

    private void syncUserRecipients(Integer userId, String roleCode) {
        if (userId == null) {
            return;
        }

        noticeMapper.syncUserRecipients(userId, normalizeTargetRole(roleCode));
    }

    private NoticeEntity normalizeNotice(NoticeEntity notice) {
        NoticeEntity normalizedNotice = notice == null ? new NoticeEntity() : notice;
        if (isBlank(normalizedNotice.getTitle())) {
            normalizedNotice.setTitle("系统提醒");
        }
        if (isBlank(normalizedNotice.getNoticeType())) {
            normalizedNotice.setNoticeType(normalizedNotice.getType());
        }
        if (isBlank(normalizedNotice.getType())) {
            normalizedNotice.setType(normalizedNotice.getNoticeType());
        }
        if (isBlank(normalizedNotice.getType())) {
            normalizedNotice.setType("系统提醒");
            normalizedNotice.setNoticeType("系统提醒");
        }
        if (isBlank(normalizedNotice.getTargetRole())) {
            normalizedNotice.setTargetRole(normalizedNotice.getTarget());
        }
        if (isBlank(normalizedNotice.getTarget())) {
            normalizedNotice.setTarget(normalizedNotice.getTargetRole());
        }
        if (isBlank(normalizedNotice.getTarget())) {
            normalizedNotice.setTarget("labAdmin");
            normalizedNotice.setTargetRole("labAdmin");
        }
        normalizedNotice.setTargetRole(normalizeTargetRole(normalizedNotice.getTargetRole()));
        normalizedNotice.setTarget(normalizedNotice.getTargetRole());
        if (isBlank(normalizedNotice.getPriority())) {
            normalizedNotice.setPriority("普通");
        }
        if (isBlank(normalizedNotice.getPublishStatus())) {
            normalizedNotice.setPublishStatus(normalizedNotice.getStatus());
        }
        if (isBlank(normalizedNotice.getStatus())) {
            normalizedNotice.setStatus(normalizedNotice.getPublishStatus());
        }
        if (isBlank(normalizedNotice.getStatus())) {
            normalizedNotice.setStatus("草稿");
            normalizedNotice.setPublishStatus("草稿");
        }
        if (isBlank(normalizedNotice.getContent())) {
            normalizedNotice.setContent(normalizedNotice.getTitle());
        }
        if ("已发布".equals(firstNonBlank(normalizedNotice.getStatus(), normalizedNotice.getPublishStatus()))
                && normalizedNotice.getPublishTime() == null) {
            normalizedNotice.setPublishTime(LocalDateTime.now());
        }
        return normalizedNotice;
    }

    private NoticeEntity buildBusinessNotice(String title, String type, String targetRole, String sourceModule, String sourceId, String content) {
        NoticeEntity notice = new NoticeEntity();
        notice.setTitle(title);
        notice.setType(type);
        notice.setNoticeType(type);
        notice.setTarget(targetRole);
        notice.setTargetRole(targetRole);
        notice.setPriority("高");
        notice.setSourceModule(sourceModule);
        notice.setSourceId(sourceId);
        notice.setBusinessType(type);
        notice.setContent(content);
        notice.setStatus("已发布");
        notice.setPublishStatus("已发布");
        notice.setPublishTime(LocalDateTime.now());
        return notice;
    }

    private List<NoticeEntity> getDefaultNotices() {
        return List.of(
                buildNotice("课程环境确认提醒", "环境确认", "teacher", "请任课教师确认已配置课程环境。", "已发布"),
                buildNotice("设备维护提醒", "设备维护", "labAdmin", "请关注待处理设备维护工单。", "已发布"),
                buildNotice("库存预警提醒", "库存预警", "labAdmin", "请及时处理低库存耗材。", "草稿")
        );
    }

    private NoticeEntity buildNotice(String title, String type, String target, String content, String status) {
        NoticeEntity notice = new NoticeEntity();
        notice.setTitle(title);
        notice.setType(type);
        notice.setNoticeType(type);
        notice.setTarget(target);
        notice.setTargetRole(target);
        notice.setContent(content);
        notice.setStatus(status);
        notice.setPublishStatus(status);
        if ("已发布".equals(status)) {
            notice.setPublishTime(LocalDateTime.now());
        }
        return notice;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return "";
    }

    private String normalizeTargetRole(String value) {
        String text = firstNonBlank(value, "labAdmin");
        if ("all".equalsIgnoreCase(text) || text.contains("全部")) {
            return "all";
        }
        if ("systemAdmin".equalsIgnoreCase(text) || text.contains("系统") || text.contains("平台")) {
            return "systemAdmin";
        }
        if ("teacher".equalsIgnoreCase(text) || text.contains("教师") || text.contains("任课") || text.contains("业务使用方")) {
            return "teacher";
        }
        if ("maintenance".equalsIgnoreCase(text) || text.contains("维修") || text.contains("运维")) {
            return "maintenance";
        }
        if ("labAdmin".equalsIgnoreCase(text) || text.contains("实验室")) {
            return "labAdmin";
        }
        return text;
    }
}
