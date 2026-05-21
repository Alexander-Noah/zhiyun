package org.example.backend.controller;

import lombok.Data;
import org.example.backend.result.Result;
import org.example.backend.entity.RepairEntity;
import org.example.backend.service.RepairService;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@CrossOrigin
@RestController
public class RepairController {
    private final RepairService repairService;

    public RepairController(RepairService repairService) {
        this.repairService = repairService;
    }

    @GetMapping("/repairs")
    public Result listRepairs() {
        return Result.success("获取报修列表成功", repairService.listRepairs());
    }

    @PostMapping("/repairs")
    public Result createRepair(@RequestBody RepairEntity repair) {
        return Result.success("新增报修成功", repairService.createRepair(repair));
    }

    @PutMapping("/repairs/{id}")
    public Result updateRepair(@PathVariable String id, @RequestBody RepairEntity repair) {
        return Result.success("更新报修成功", repairService.updateRepair(id, repair));
    }

    @DeleteMapping("/repairs/{id}")
    public Result deleteRepair(@PathVariable String id) {
        repairService.deleteRepair(id);
        return Result.success("删除报修成功");
    }

    @PutMapping("/repairs/batch")
    public Result replaceRepairs(@RequestBody RepairBatchRequest request) {
        List<RepairEntity> repairs = request == null || request.getRecords() == null
                ? Collections.emptyList()
                : request.getRecords();
        return Result.success("批量保存报修成功", repairService.replaceRepairs(repairs));
    }

    @PostMapping("/repairs/reset")
    public Result resetRepairs() {
        return Result.success("重置报修数据成功", repairService.resetRepairs());
    }

    @PostMapping("/repairs/{id}/assign")
    public Result assignRepair(@PathVariable String id, @RequestBody(required = false) RepairEntity repair) {
        return Result.success("派单成功", repairService.patchRepair(id, repair, "处理中", 35, "已派单，维修人员正在处理。"));
    }

    @PostMapping("/repairs/{id}/submit-acceptance")
    public Result submitAcceptance(@PathVariable String id, @RequestBody(required = false) RepairEntity repair) {
        return Result.success("提交验收成功", repairService.patchRepair(id, repair, "待验收", 86, "维修处理完成，等待实验室管理员验收。"));
    }

    @PostMapping("/repairs/{id}/complete")
    public Result completeRepair(@PathVariable String id, @RequestBody(required = false) RepairEntity repair) {
        return Result.success("完成报修成功", repairService.patchRepair(id, repair, "已完成", 100, "管理员验收通过，故障已恢复。"));
    }

    @Data
    public static class RepairBatchRequest {
        private String resource;
        private List<RepairEntity> records;
    }
}
