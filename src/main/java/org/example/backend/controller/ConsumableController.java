package org.example.backend.controller;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.result.Result;
import org.example.backend.entity.ConsumableEntity;
import org.example.backend.entity.ConsumableStockRecordEntity;
import org.example.backend.service.ConsumableService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/consumables")
public class ConsumableController {
    private final ConsumableService consumableService;

    public ConsumableController(ConsumableService consumableService) {
        this.consumableService = consumableService;
    }

    @GetMapping
    public Result listConsumables() {
        return Result.success("获取耗材列表成功", consumableService.listConsumables());
    }

    @GetMapping("/stock-records")
    public Result listStockRecords(@RequestParam(required = false) Long consumableId) {
        return Result.success("获取耗材出入库记录成功", consumableService.listStockRecords(consumableId));
    }

    @PostMapping
    public Result createConsumable(@RequestBody ConsumableEntity consumable) {
        return Result.success("新增耗材成功", consumableService.createConsumable(consumable));
    }

    @PostMapping("/{id:\\d+}/movement")
    public Result recordConsumableMovement(@PathVariable Long id, @RequestBody ConsumableStockRecordEntity record) {
        return Result.success("保存耗材出入库记录成功", consumableService.recordConsumableMovement(id, record));
    }

    @PutMapping("/{id}")
    public Result updateConsumable(@PathVariable Long id, @RequestBody ConsumableEntity consumable) {
        return Result.success("更新耗材成功", consumableService.updateConsumable(id, consumable));
    }

    @DeleteMapping("/{id}")
    public Result deleteConsumable(@PathVariable Long id) {
        return Result.success("删除耗材成功", consumableService.deleteConsumable(id));
    }

    @PutMapping("/batch")
    public Result replaceConsumables(@RequestBody ConsumableBatchRequest request) {
        List<ConsumableEntity> consumables = request == null || request.getRecords() == null
                ? Collections.emptyList()
                : request.getRecords();
        log.info("replace consumables: {}", consumables.size());
        return Result.success("批量保存耗材成功", consumableService.replaceConsumables(consumables));
    }

    @PostMapping("/reset")
    public Result resetConsumables() {
        return Result.success("重置耗材数据成功", consumableService.resetConsumables());
    }

    @Data
    public static class ConsumableBatchRequest {
        private String resource;
        private List<ConsumableEntity> records;
    }
}
