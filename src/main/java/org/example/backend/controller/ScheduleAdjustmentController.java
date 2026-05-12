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
        return Result.success("list schedule adjustments success", scheduleAdjustmentService.listAdjustments());
    }

    @PostMapping("/schedule-adjustments")
    public Result createAdjustment(@RequestBody ScheduleAdjustmentEntity adjustment) {
        return Result.success("create schedule adjustment success", scheduleAdjustmentService.createAdjustment(adjustment));
    }

    @PutMapping("/schedule-adjustments/{id}")
    public Result updateAdjustment(@PathVariable Long id, @RequestBody ScheduleAdjustmentEntity adjustment) {
        return Result.success("update schedule adjustment success", scheduleAdjustmentService.updateAdjustment(id, adjustment));
    }

    @PostMapping("/schedule-adjustments/{id}/approve")
    public Result approveAdjustment(@PathVariable Long id) {
        return Result.success("approve schedule adjustment success", scheduleAdjustmentService.approveAdjustment(id));
    }

    @PostMapping("/schedule-adjustments/{id}/complete")
    public Result completeAdjustment(@PathVariable Long id) {
        return Result.success("complete schedule adjustment success", scheduleAdjustmentService.completeAdjustment(id));
    }
}
