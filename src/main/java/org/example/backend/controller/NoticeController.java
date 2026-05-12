package org.example.backend.controller;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.result.Result;
import org.example.backend.entity.NoticeEntity;
import org.example.backend.service.NoticeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
        return Result.success("list notices success", noticeService.listNotices());
    }

    @PostMapping
    public Result createNotice(@RequestBody NoticeEntity notice) {
        return Result.success("create notice success", noticeService.createNotice(notice));
    }

    @PutMapping("/{id}")
    public Result updateNotice(@PathVariable Long id, @RequestBody NoticeEntity notice) {
        return Result.success("update notice success", noticeService.updateNotice(id, notice));
    }

    @DeleteMapping("/{id}")
    public Result deleteNotice(@PathVariable Long id) {
        return Result.success("delete notice success", noticeService.deleteNotice(id));
    }

    @PutMapping("/batch")
    public Result replaceNotices(@RequestBody NoticeBatchRequest request) {
        List<NoticeEntity> notices = request == null || request.getRecords() == null
                ? Collections.emptyList()
                : request.getRecords();
        log.info("replace notices: {}", notices.size());
        return Result.success("replace notices success", noticeService.replaceNotices(notices));
    }

    @PostMapping("/reset")
    public Result resetNotices() {
        return Result.success("reset notices success", noticeService.resetNotices());
    }

    @Data
    public static class NoticeBatchRequest {
        private String resource;
        private List<NoticeEntity> records;
    }
}
