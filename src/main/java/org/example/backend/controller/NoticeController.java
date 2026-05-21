package org.example.backend.controller;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.result.Result;
import org.example.backend.entity.NoticeEntity;
import org.example.backend.security.JwtAuthenticationFilter;
import org.example.backend.service.NoticeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/notices")
public class NoticeController {
    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public Result listNotices() {
        return Result.success("获取通知列表成功", noticeService.listNotices());
    }

    @PostMapping
    public Result createNotice(@RequestBody NoticeEntity notice) {
        return Result.success("新增通知成功", noticeService.createNotice(notice));
    }

    @PutMapping("/{id}")
    public Result updateNotice(@PathVariable Long id, @RequestBody NoticeEntity notice) {
        return Result.success("更新通知成功", noticeService.updateNotice(id, notice));
    }

    @DeleteMapping("/{id}")
    public Result deleteNotice(@PathVariable Long id) {
        return Result.success("删除通知成功", noticeService.deleteNotice(id));
    }

    @PostMapping("/{id}/publish")
    public Result publishNotice(@PathVariable Long id) {
        return Result.success("发布通知成功", noticeService.publishNotice(id));
    }

    @PostMapping("/{id}/withdraw")
    public Result withdrawNotice(@PathVariable Long id) {
        return Result.success("撤回通知成功", noticeService.withdrawNotice(id));
    }

    @PostMapping("/{id}/archive")
    public Result archiveNotice(@PathVariable Long id) {
        return Result.success("归档通知成功", noticeService.archiveNotice(id));
    }

    @GetMapping("/{id}/recipients")
    public Result listRecipients(@PathVariable Long id) {
        return Result.success("获取通知接收人成功", noticeService.listRecipients(id));
    }

    @GetMapping("/user")
    public Result listMyNotices(
            @RequestAttribute(value = JwtAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE, required = false) Integer userId,
            @RequestAttribute(value = JwtAuthenticationFilter.AUTH_ROLE_ATTRIBUTE, required = false) String roleCode
    ) {
        return Result.success("获取我的通知成功", noticeService.listUserNotices(userId, roleCode, null));
    }

    @GetMapping("/user/unread")
    public Result listUnreadNotices(
            @RequestAttribute(value = JwtAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE, required = false) Integer userId,
            @RequestAttribute(value = JwtAuthenticationFilter.AUTH_ROLE_ATTRIBUTE, required = false) String roleCode
    ) {
        return Result.success("获取未读通知成功", noticeService.listUserNotices(userId, roleCode, "未读"));
    }

    @PostMapping("/{id}/read")
    public Result markRead(
            @PathVariable Long id,
            @RequestAttribute(value = JwtAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE, required = false) Integer userId,
            @RequestAttribute(value = JwtAuthenticationFilter.AUTH_ROLE_ATTRIBUTE, required = false) String roleCode
    ) {
        return Result.success("标记通知已读成功", noticeService.markRead(id, userId, roleCode));
    }

    @GetMapping("/stats")
    public Result getNoticeStats(
            @RequestAttribute(value = JwtAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE, required = false) Integer userId,
            @RequestAttribute(value = JwtAuthenticationFilter.AUTH_ROLE_ATTRIBUTE, required = false) String roleCode
    ) {
        return Result.success("获取通知统计成功", noticeService.getNoticeStats(userId, roleCode));
    }

    @PostMapping("/business-reminders/sync")
    public Result syncBusinessReminders() {
        return Result.success("同步业务提醒成功", noticeService.syncBusinessReminders());
    }

    @PutMapping("/batch")
    public Result replaceNotices(@RequestBody NoticeBatchRequest request) {
        List<NoticeEntity> notices = request == null || request.getRecords() == null
                ? Collections.emptyList()
                : request.getRecords();
        log.info("replace notices: {}", notices.size());
        return Result.success("批量保存通知成功", noticeService.replaceNotices(notices));
    }

    @PostMapping("/reset")
    public Result resetNotices() {
        return Result.success("重置通知数据成功", noticeService.resetNotices());
    }

    @Data
    public static class NoticeBatchRequest {
        private String resource;
        private List<NoticeEntity> records;
    }
}
