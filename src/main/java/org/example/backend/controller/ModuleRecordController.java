package org.example.backend.controller;

import lombok.Data;
import org.example.backend.result.Result;
import org.example.backend.service.ModuleRecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/modules/{moduleName}")
public class ModuleRecordController {
    private final ModuleRecordService moduleRecordService;

    public ModuleRecordController(ModuleRecordService moduleRecordService) {
        this.moduleRecordService = moduleRecordService;
    }

    @GetMapping
    public Result listModuleRecords(@PathVariable String moduleName) {
        return Result.success("获取模块数据成功", moduleRecordService.listModuleRecords(moduleName));
    }

    @PutMapping("/batch")
    public Result replaceModuleRecords(@PathVariable String moduleName, @RequestBody ModuleRecordBatchRequest request) {
        List<Map<String, Object>> records = request == null || request.getRecords() == null
                ? Collections.emptyList()
                : request.getRecords();

        return Result.success("保存模块数据成功", moduleRecordService.replaceModuleRecords(moduleName, records));
    }

    @PostMapping("/reset")
    public Result resetModuleRecords(@PathVariable String moduleName) {
        return Result.success("重置模块数据成功", moduleRecordService.resetModuleRecords(moduleName));
    }

    @Data
    public static class ModuleRecordBatchRequest {
        private String resource;
        private List<Map<String, Object>> records;
    }
}
