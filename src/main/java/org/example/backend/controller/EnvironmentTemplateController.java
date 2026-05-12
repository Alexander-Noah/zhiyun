package org.example.backend.controller;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.result.Result;
import org.example.backend.entity.EnvironmentTemplateEntity;
import org.example.backend.service.EnvironmentTemplateService;
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
@RequestMapping("/environment-templates")
public class EnvironmentTemplateController {
    private final EnvironmentTemplateService environmentTemplateService;

    public EnvironmentTemplateController(EnvironmentTemplateService environmentTemplateService) {
        this.environmentTemplateService = environmentTemplateService;
    }

    @GetMapping
    public Result listTemplates() {
        log.info("获取环境模板列表");
        return Result.success("获取环境模板列表成功", environmentTemplateService.listTemplates());
    }

    @PostMapping
    public Result createTemplate(@RequestBody EnvironmentTemplateEntity template) {
        log.info("新增环境模板");
        return Result.success("新增环境模板成功", environmentTemplateService.createTemplate(template));
    }

    @PutMapping("/{id}")
    public Result updateTemplate(@PathVariable Long id, @RequestBody EnvironmentTemplateEntity template) {
        log.info("更新环境模板: {}", id);
        return Result.success("更新环境模板成功", environmentTemplateService.updateTemplate(id, template));
    }

    @DeleteMapping("/{id}")
    public Result deleteTemplate(@PathVariable Long id) {
        log.info("删除环境模板: {}", id);
        return Result.success("删除环境模板成功", environmentTemplateService.deleteTemplate(id));
    }

    @PutMapping("/batch")
    public Result replaceTemplates(@RequestBody EnvironmentTemplateBatchRequest request) {
        List<EnvironmentTemplateEntity> templates = request == null || request.getRecords() == null
                ? Collections.emptyList()
                : request.getRecords();

        log.info("批量保存环境模板: {} 条", templates.size());
        return Result.success("保存环境模板成功", environmentTemplateService.replaceTemplates(templates));
    }

    @PostMapping("/reset")
    public Result resetTemplates() {
        log.info("重置环境模板");
        return Result.success("重置环境模板成功", environmentTemplateService.resetTemplates());
    }

    @Data
    public static class EnvironmentTemplateBatchRequest {
        private String resource;
        private List<EnvironmentTemplateEntity> records;
    }
    @GetMapping("/teachers")
    public Result listTeachers() {
        log.info("获取教师列表");
        return Result.success("获取教师列表成功", environmentTemplateService.listTeachers());
    }
}
