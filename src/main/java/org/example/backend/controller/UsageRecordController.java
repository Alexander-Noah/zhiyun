package org.example.backend.controller;

import lombok.Data;
import org.example.backend.result.Result;
import org.example.backend.entity.UsageRecordEntity;
import org.example.backend.service.UsageRecordService;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@CrossOrigin
@RestController
public class UsageRecordController {
    private final UsageRecordService usageRecordService;

    public UsageRecordController(UsageRecordService usageRecordService) {
        this.usageRecordService = usageRecordService;
    }

    @GetMapping("/usage-records")
    public Result listUsageRecords() {
        return Result.success("list usage records success", usageRecordService.listUsageRecords());
    }

    @PutMapping("/usage-records/batch")
    public Result replaceUsageRecords(@RequestBody UsageRecordBatchRequest request) {
        List<UsageRecordEntity> records = request == null || request.getRecords() == null
                ? Collections.emptyList()
                : request.getRecords();
        return Result.success("batch save usage records success", usageRecordService.replaceUsageRecords(records));
    }

    @PostMapping("/usage-records/{id}/review")
    public Result reviewUsageRecord(@PathVariable Long id) {
        return Result.success("review usage record success", usageRecordService.updateStatus(id, "正常"));
    }

    @PostMapping("/usage-records/{id}/abnormal")
    public Result abnormalUsageRecord(@PathVariable Long id) {
        return Result.success("mark usage record abnormal success", usageRecordService.updateStatus(id, "异常"));
    }

    @PostMapping("/usage-records/{id}/archive")
    public Result archiveUsageRecord(@PathVariable Long id) {
        return Result.success("archive usage record success", usageRecordService.updateStatus(id, "已归档"));
    }

    @PostMapping("/usage-records/reset")
    public Result resetUsageRecords() {
        return Result.success("reset usage records success", usageRecordService.resetUsageRecords());
    }

    @Data
    public static class UsageRecordBatchRequest {
        private String resource;
        private List<UsageRecordEntity> records;
    }
}
