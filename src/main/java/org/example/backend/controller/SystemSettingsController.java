package org.example.backend.controller;

import org.example.backend.result.Result;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.ModuleRecordService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin
@RestController
public class SystemSettingsController {
    private static final String MODULE_NAME = "system-settings";
    private static final String RECORD_ID = "system-settings";

    private final ModuleRecordService moduleRecordService;
    private final BusinessLoopService businessLoopService;

    public SystemSettingsController(ModuleRecordService moduleRecordService, BusinessLoopService businessLoopService) {
        this.moduleRecordService = moduleRecordService;
        this.businessLoopService = businessLoopService;
    }

    @GetMapping("/system-settings")
    public Result getSettings() {
        List<Map<String, Object>> records = moduleRecordService.listModuleRecords(MODULE_NAME);
        if (records != null && !records.isEmpty()) {
            return Result.success("get system settings success", records.get(0));
        }
        return Result.success("get default system settings success", defaultSettings());
    }

    @PutMapping("/system-settings")
    public Result saveSettings(@RequestBody Map<String, Object> settings) {
        Map<String, Object> nextSettings = new LinkedHashMap<>(settings == null ? Map.of() : settings);
        nextSettings.put("id", RECORD_ID);
        moduleRecordService.replaceModuleRecords(MODULE_NAME, List.of(nextSettings));
        businessLoopService.recordEvent("system-settings", "update", "\u5e73\u53f0\u53c2\u6570", "\u5df2\u4fdd\u5b58", Map.of("sectionCount", Math.max(0, nextSettings.size() - 1)));
        return Result.success("save system settings success", nextSettings);
    }

    @PostMapping("/system-settings/reset")
    public Result resetSettings() {
        Map<String, Object> settings = defaultSettings();
        moduleRecordService.replaceModuleRecords(MODULE_NAME, List.of(settings));
        businessLoopService.recordEvent("system-settings", "reset", "\u5e73\u53f0\u53c2\u6570", "\u5df2\u6062\u590d\u9ed8\u8ba4", Map.of("sectionCount", Math.max(0, settings.size() - 1)));
        return Result.success("reset system settings success", settings);
    }

    private Map<String, Object> defaultSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("id", RECORD_ID);
        settings.put("basicConfig", Map.of(
                "platformName", "智云实验室协同管控平台",
                "noticeTarget", "实验室管理员",
                "defaultHome", "按角色首页",
                "autoBackup", true,
                "dataRetention", 180
        ));
        settings.put("loginPolicy", Map.of(
                "singleSignOn", true,
                "forcePasswordReset", false,
                "loginAudit", true,
                "sessionTimeout", 45,
                "captchaEnabled", true
        ));
        settings.put("aiSettings", Map.of(
                "provider", "通义智能体",
                "baseUrl", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "timeout", 30,
                "autoSuggestion", true,
                "fallbackReply", "服务异常时返回平台内置运维建议。"
        ));
        return settings;
    }
}
