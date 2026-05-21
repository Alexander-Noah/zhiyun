package org.example.backend.controller;

import org.example.backend.entity.ApprovalCountersignEntity;
import org.example.backend.result.Result;
import org.example.backend.service.ApprovalCountersignService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApprovalCountersignController {
    private final ApprovalCountersignService countersignService;

    public ApprovalCountersignController(ApprovalCountersignService countersignService) {
        this.countersignService = countersignService;
    }

    @GetMapping("/approval-countersigns")
    public Result listCountersigns(
            @RequestParam(value = "businessType", required = false) String businessType,
            @RequestParam(value = "businessId", required = false) String businessId,
            @RequestParam(value = "assigneeName", required = false) String assigneeName,
            @RequestParam(value = "status", required = false) String status
    ) {
        return Result.success("获取加签列表成功", countersignService.listCountersigns(businessType, businessId, assigneeName, status));
    }

    @PostMapping("/approval-countersigns")
    public Result createCountersign(@RequestBody ApprovalCountersignEntity countersign) {
        return Result.success("发起加签成功", countersignService.createCountersign(countersign));
    }

    @PostMapping("/approval-countersigns/{id:\\d+}/complete")
    public Result completeCountersign(@PathVariable Long id, @RequestBody(required = false) ApprovalCountersignEntity payload) {
        return Result.success("处理加签成功", countersignService.completeCountersign(id, payload));
    }

    @PostMapping("/approval-countersigns/{id:\\d+}/cancel")
    public Result cancelCountersign(@PathVariable Long id, @RequestBody(required = false) ApprovalCountersignEntity payload) {
        return Result.success("撤销加签成功", countersignService.cancelCountersign(id, payload));
    }
}
