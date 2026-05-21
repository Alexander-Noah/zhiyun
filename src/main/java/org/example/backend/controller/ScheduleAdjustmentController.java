package org.example.backend.controller;

import org.example.backend.result.Result;
import org.example.backend.entity.ScheduleAdjustmentEntity;
import org.example.backend.service.ScheduleAdjustmentService;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
public class ScheduleAdjustmentController {
    private final ScheduleAdjustmentService scheduleAdjustmentService;

    public ScheduleAdjustmentController(ScheduleAdjustmentService scheduleAdjustmentService) {
        this.scheduleAdjustmentService = scheduleAdjustmentService;
    }

    @GetMapping("/schedule-adjustments")
    public Result listAdjustments() {
        return Result.success("获取调课申请列表成功", scheduleAdjustmentService.listAdjustments());
    }

    @PostMapping("/schedule-adjustments")
    public Result createAdjustment(@RequestBody ScheduleAdjustmentEntity adjustment) {
        return Result.success("新增调课申请成功", scheduleAdjustmentService.createAdjustment(adjustment));
    }

    @PutMapping("/schedule-adjustments/{id}")
    public Result updateAdjustment(@PathVariable Long id, @RequestBody ScheduleAdjustmentEntity adjustment) {
        return Result.success("更新调课申请成功", scheduleAdjustmentService.updateAdjustment(id, adjustment));
    }

    @PostMapping("/schedule-adjustments/{id}/approve")
    public Result approveAdjustment(@PathVariable Long id) {
        return Result.success("通过调课申请成功", scheduleAdjustmentService.approveAdjustment(id));
    }

    @PostMapping("/schedule-adjustments/{id}/complete")
    public Result completeAdjustment(@PathVariable Long id) {
        return Result.success("完成调课同步成功", scheduleAdjustmentService.completeAdjustment(id));
    }
}
