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
        return Result.success("获取使用记录成功", usageRecordService.listUsageRecords());
    }

    @PutMapping("/usage-records/batch")
    public Result replaceUsageRecords(@RequestBody UsageRecordBatchRequest request) {
        List<UsageRecordEntity> records = request == null || request.getRecords() == null
                ? Collections.emptyList()
                : request.getRecords();
        return Result.success("批量保存使用记录成功", usageRecordService.replaceUsageRecords(records));
    }

    @PostMapping("/usage-records/{id}/review")
    public Result reviewUsageRecord(@PathVariable Long id) {
        return Result.success("复核使用记录成功", usageRecordService.updateStatus(id, "正常"));
    }

    @PostMapping("/usage-records/{id}/abnormal")
    public Result abnormalUsageRecord(@PathVariable Long id) {
        return Result.success("标记使用记录异常成功", usageRecordService.updateStatus(id, "异常"));
    }

    @PostMapping("/usage-records/{id}/archive")
    public Result archiveUsageRecord(@PathVariable Long id) {
        return Result.success("归档使用记录成功", usageRecordService.updateStatus(id, "已归档"));
    }

    @PostMapping("/usage-records/reset")
    public Result resetUsageRecords() {
        return Result.success("重置使用记录成功", usageRecordService.resetUsageRecords());
    }

    @Data
    public static class UsageRecordBatchRequest {
        private String resource;
        private List<UsageRecordEntity> records;
    }
}
