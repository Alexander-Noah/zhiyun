package org.example.backend.service.impl;

import org.example.backend.entity.NoticeEntity;
import org.example.backend.mapper.NoticeMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.NoticeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
        recordNoticeEvent("create", savedNotice);
        return savedNotice;
    }

    @Override
    @Transactional
    public NoticeEntity updateNotice(Long id, NoticeEntity notice) {
        noticeMapper.updateNotice(id, normalizeNotice(notice));
        NoticeEntity savedNotice = noticeMapper.getNotice(id);
        recordNoticeEvent("update", savedNotice);
        return savedNotice;
    }

    @Override
    @Transactional
    public List<NoticeEntity> deleteNotice(Long id) {
        NoticeEntity notice = noticeMapper.getNotice(id);
        noticeMapper.deleteNotice(id);
        businessLoopService.recordEvent("notice", "delete", notice == null ? String.valueOf(id) : notice.getTitle(), "\u5df2\u5220\u9664", Map.of("id", id));
        return noticeMapper.listNotices();
    }

    @Override
    @Transactional
    public List<NoticeEntity> replaceNotices(List<NoticeEntity> notices) {
        noticeMapper.deleteAllNotices();
        for (NoticeEntity notice : notices == null ? Collections.<NoticeEntity>emptyList() : notices) {
            noticeMapper.insertNotice(normalizeNotice(notice));
        }
        businessLoopService.recordEvent("notice", "batch-save", "\u901a\u77e5\u516c\u544a", "\u5df2\u540c\u6b65", Map.of("count", notices == null ? 0 : notices.size()));
        return noticeMapper.listNotices();
    }

    @Override
    @Transactional
    public List<NoticeEntity> resetNotices() {
        noticeMapper.deleteAllNotices();
        for (NoticeEntity notice : getDefaultNotices()) {
            noticeMapper.insertNotice(notice);
        }
        businessLoopService.recordEvent("notice", "reset", "\u901a\u77e5\u516c\u544a", "\u5df2\u6062\u590d\u521d\u59cb\u516c\u544a", Map.of("count", getDefaultNotices().size()));
        return noticeMapper.listNotices();
    }

    private void recordNoticeEvent(String action, NoticeEntity notice) {
        if (notice == null) {
            return;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", notice.getId());
        details.put("type", firstNonBlank(notice.getNoticeType(), notice.getType()));
        details.put("target", firstNonBlank(notice.getTargetRole(), notice.getTarget()));
        businessLoopService.recordEvent("notice", action, firstNonBlank(notice.getTitle(), "\u901a\u77e5\u516c\u544a"), firstNonBlank(notice.getPublishStatus(), notice.getStatus()), details);
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
        if ("已发布".equals(normalizedNotice.getStatus()) && normalizedNotice.getPublishTime() == null) {
            normalizedNotice.setPublishTime(LocalDateTime.now());
        }
        return normalizedNotice;
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
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
