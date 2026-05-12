package org.example.backend.controller;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.result.Result;
import org.example.backend.entity.ConsumableEntity;
import org.example.backend.service.ConsumableService;
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
@RequestMapping("/consumables")
public class ConsumableController {
    private final ConsumableService consumableService;

    public ConsumableController(ConsumableService consumableService) {
        this.consumableService = consumableService;
    }

    @GetMapping
    public Result listConsumables() {
        return Result.success("list consumables success", consumableService.listConsumables());
    }

    @PostMapping
    public Result createConsumable(@RequestBody ConsumableEntity consumable) {
        return Result.success("create consumable success", consumableService.createConsumable(consumable));
    }

    @PutMapping("/{id}")
    public Result updateConsumable(@PathVariable Long id, @RequestBody ConsumableEntity consumable) {
        return Result.success("update consumable success", consumableService.updateConsumable(id, consumable));
    }

    @DeleteMapping("/{id}")
    public Result deleteConsumable(@PathVariable Long id) {
        return Result.success("delete consumable success", consumableService.deleteConsumable(id));
    }

    @PutMapping("/batch")
    public Result replaceConsumables(@RequestBody ConsumableBatchRequest request) {
        List<ConsumableEntity> consumables = request == null || request.getRecords() == null
                ? Collections.emptyList()
                : request.getRecords();
        log.info("replace consumables: {}", consumables.size());
        return Result.success("replace consumables success", consumableService.replaceConsumables(consumables));
    }

    @PostMapping("/reset")
    public Result resetConsumables() {
        return Result.success("reset consumables success", consumableService.resetConsumables());
    }

    @Data
    public static class ConsumableBatchRequest {
        private String resource;
        private List<ConsumableEntity> records;
    }
}
