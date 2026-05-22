package org.example.backend.controller;

import org.example.backend.result.Result;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.ModuleRecordService;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
public class ActivationCodeController {
    private static final String MODULE_NAME = "activation-codes";
    private static final Pattern MAC_PATTERN = Pattern.compile("^[0-9A-F]{12}$");
    private static final char[] CODE_CHARS = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ModuleRecordService moduleRecordService;
    private final BusinessLoopService businessLoopService;

    public ActivationCodeController(ModuleRecordService moduleRecordService, BusinessLoopService businessLoopService) {
        this.moduleRecordService = moduleRecordService;
        this.businessLoopService = businessLoopService;
    }

    @GetMapping({"/activation-codes", "/admin/activation-codes"})
    public Result listActivationCodes() {
        return Result.success("获取激活码列表成功", sortedRecords());
    }

    @PostMapping({"/activation-codes/generate", "/admin/activation-codes/generate"})
    public Result generateActivationCode(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> request = payload == null ? Map.of() : payload;
        int deviceLimit = intValue(request.get("deviceLimit"), 1);

        if (deviceLimit < 1) {
            return Result.error("设备绑定数量至少为 1 台");
        }
        if (deviceLimit > 10000) {
            return Result.error("单个激活码最多允许绑定 10000 台设备");
        }

        List<Map<String, Object>> records = new ArrayList<>(moduleRecordService.listModuleRecords(MODULE_NAME));
        Set<String> existingCodes = records.stream()
                .map((record) -> stringValue(record.get("code")))
                .collect(Collectors.toSet());
        String code = generateUniqueCode(existingCodes);
        String now = nowText();

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", code);
        record.put("code", code);
        record.put("deviceLimit", deviceLimit);
        record.put("boundDevices", List.of());
        record.put("boundCount", 0);
        record.put("labId", "");
        record.put("labCode", "");
        record.put("labName", "");
        record.put("labBoundAt", "");
        record.put("owner", stringValue(request.get("owner")));
        record.put("remark", stringValue(request.get("remark")));
        record.put("expiresAt", stringValue(request.get("expiresAt")));
        record.put("status", "active");
        record.put("createdAt", now);
        record.put("updatedAt", now);

        records.add(0, record);
        moduleRecordService.replaceModuleRecords(MODULE_NAME, records);
        businessLoopService.recordEvent("activation-code", "generate", code, "已生成", Map.of("deviceLimit", deviceLimit));
        return Result.success("生成激活码成功", record);
    }

    @PostMapping({"/activation-codes/bind-lab", "/admin/activation-codes/bind-lab"})
    public Result bindActivationCodeToLab(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> request = payload == null ? Map.of() : payload;
        String code = normalizeActivationCode(stringValue(request.get("code")));
        String labId = stringValue(request.get("labId"));
        String labCode = stringValue(request.get("labCode"));
        String labName = stringValue(request.get("labName"));

        if (code.isBlank()) {
            return Result.error("请填写激活码");
        }
        if (labId.isBlank()) {
            return Result.error("请选择需要绑定的实验室");
        }

        List<Map<String, Object>> records = new ArrayList<>(moduleRecordService.listModuleRecords(MODULE_NAME));
        boolean labAlreadyBound = records.stream().anyMatch((record) ->
                labId.equals(stringValue(record.get("labId")))
                        && "active".equals(record.get("status"))
                        && !code.equals(normalizeActivationCode(stringValue(record.get("code")))));
        if (labAlreadyBound) {
            return Result.error("该实验室已绑定激活码，请先取消后再绑定新的激活码");
        }

        for (Map<String, Object> record : records) {
            if (!code.equals(normalizeActivationCode(stringValue(record.get("code"))))) continue;

            if (!"active".equals(record.get("status"))) {
                return Result.error("激活码已停用，无法绑定实验室");
            }
            if (isExpired(stringValue(record.get("expiresAt")))) {
                return Result.error("激活码已过期，无法绑定实验室");
            }

            String existingLabId = stringValue(record.get("labId"));
            if (!existingLabId.isBlank() && !existingLabId.equals(labId)) {
                return Result.error("该激活码已绑定其他实验室");
            }

            String now = nowText();
            record.put("labId", labId);
            record.put("labCode", labCode);
            record.put("labName", labName);
            record.put("labBoundAt", stringValue(record.get("labBoundAt")).isBlank() ? now : record.get("labBoundAt"));
            record.put("updatedAt", now);
            record.put("deviceLimit", intValue(record.get("deviceLimit"), legacyDeviceLimit(record)));
            record.put("boundCount", boundDevices(record).size());

            moduleRecordService.replaceModuleRecords(MODULE_NAME, records);
            businessLoopService.recordEvent("activation-code", "bind-lab", code, "已绑定实验室", Map.of("labId", labId, "labName", labName));
            return Result.success("实验室绑定成功", record);
        }

        return Result.error("激活码不存在");
    }

    @PostMapping({"/activation-codes/{code}/unbind-lab", "/admin/activation-codes/{code}/unbind-lab"})
    public Result unbindActivationCodeFromLab(@PathVariable String code) {
        String normalizedCode = normalizeActivationCode(code);
        List<Map<String, Object>> records = new ArrayList<>(moduleRecordService.listModuleRecords(MODULE_NAME));
        for (Map<String, Object> record : records) {
            if (!normalizedCode.equals(normalizeActivationCode(stringValue(record.get("code"))))) continue;

            String labId = stringValue(record.get("labId"));
            String labName = stringValue(record.get("labName"));
            record.put("labId", "");
            record.put("labCode", "");
            record.put("labName", "");
            record.put("labBoundAt", "");
            record.put("updatedAt", nowText());
            record.put("deviceLimit", intValue(record.get("deviceLimit"), legacyDeviceLimit(record)));
            record.put("boundCount", boundDevices(record).size());

            moduleRecordService.replaceModuleRecords(MODULE_NAME, records);
            businessLoopService.recordEvent("activation-code", "unbind-lab", normalizedCode, "已取消实验室绑定", Map.of("labId", labId, "labName", labName));
            return Result.success("已取消实验室绑定", record);
        }

        return Result.error("激活码不存在");
    }

    @PostMapping({"/activation-codes/{code}/disable", "/admin/activation-codes/{code}/disable"})
    public Result disableActivationCode(@PathVariable String code) {
        List<Map<String, Object>> records = new ArrayList<>(moduleRecordService.listModuleRecords(MODULE_NAME));
        Map<String, Object> target = null;

        for (Map<String, Object> record : records) {
            if (code.equals(record.get("code"))) {
                record.put("status", "disabled");
                record.put("updatedAt", nowText());
                target = record;
                break;
            }
        }

        if (target == null) {
            return Result.error("激活码不存在");
        }

        moduleRecordService.replaceModuleRecords(MODULE_NAME, records);
        businessLoopService.recordEvent("activation-code", "disable", code, "已停用", Map.of("boundCount", target.get("boundCount")));
        return Result.success("停用激活码成功", target);
    }

    @PostMapping({"/activation-codes/verify", "/admin/activation-codes/verify"})
    public Result verifyActivationCode(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> request = payload == null ? Map.of() : payload;
        String code = normalizeActivationCode(stringValue(request.get("code")));
        String macAddress = normalizeMacAddress(stringValue(request.get("macAddress")));
        String hostName = stringValue(request.get("hostName"));

        if (macAddress.isBlank()) {
            return Result.error("无法获取有效设备 MAC 地址");
        }

        List<Map<String, Object>> records = new ArrayList<>(moduleRecordService.listModuleRecords(MODULE_NAME));
        for (Map<String, Object> record : records) {
            if (!code.equals(normalizeActivationCode(stringValue(record.get("code"))))) continue;

            boolean active = "active".equals(record.get("status"));
            boolean expired = isExpired(stringValue(record.get("expiresAt")));
            boolean labBound = !stringValue(record.get("labId")).isBlank();
            List<Map<String, Object>> boundDevices = boundDevices(record);
            boolean alreadyBound = boundDevices.stream().anyMatch((device) -> macAddress.equals(device.get("macAddress")));
            int deviceLimit = intValue(record.get("deviceLimit"), legacyDeviceLimit(record));
            boolean quotaAvailable = alreadyBound || boundDevices.size() < deviceLimit;

            Map<String, Object> result = new LinkedHashMap<>(record);
            result.put("valid", active && !expired && labBound && quotaAvailable);
            result.put("alreadyBound", alreadyBound);
            result.put("expired", expired);
            result.put("labBound", labBound);

            if (!active || expired || !labBound || !quotaAvailable) {
                result.put("deviceLimit", deviceLimit);
                result.put("boundDevices", boundDevices);
                result.put("boundCount", boundDevices.size());
                return Result.success("校验激活码完成", result);
            }

            if (!alreadyBound) {
                Map<String, Object> device = new LinkedHashMap<>();
                device.put("macAddress", macAddress);
                device.put("hostName", hostName);
                device.put("activatedAt", nowText());
                boundDevices.add(device);
                record.put("boundDevices", boundDevices);
            }

            record.put("deviceLimit", deviceLimit);
            record.put("boundCount", boundDevices.size());
            record.put("updatedAt", nowText());
            moduleRecordService.replaceModuleRecords(MODULE_NAME, records);

            result = new LinkedHashMap<>(record);
            result.put("valid", true);
            result.put("alreadyBound", alreadyBound);
            result.put("expired", false);
            result.put("labBound", true);
            result.put("remainingQuota", Math.max(deviceLimit - boundDevices.size(), 0));
            businessLoopService.recordEvent("activation-code", "activate", code, "设备已激活", Map.of("macAddress", macAddress));
            return Result.success("校验激活码完成", result);
        }

        return Result.error("激活码不存在");
    }

    private List<Map<String, Object>> sortedRecords() {
        List<Map<String, Object>> records = new ArrayList<>(moduleRecordService.listModuleRecords(MODULE_NAME));
        records.forEach((record) -> {
            List<Map<String, Object>> boundDevices = boundDevices(record);
            record.put("deviceLimit", intValue(record.get("deviceLimit"), legacyDeviceLimit(record)));
            record.put("boundCount", boundDevices.size());
            record.put("boundDevices", boundDevices);
        });
        records.sort(Comparator.comparing((Map<String, Object> record) -> stringValue(record.get("createdAt"))).reversed());
        return records;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> boundDevices(Map<String, Object> record) {
        Object rawDevices = record.get("boundDevices");
        if (rawDevices instanceof List<?> items) {
            List<Map<String, Object>> devices = new ArrayList<>();
            for (Object item : items) {
                if (item instanceof Map<?, ?> rawItem) {
                    Map<String, Object> device = new LinkedHashMap<>();
                    rawItem.forEach((key, value) -> device.put(String.valueOf(key), value));
                    if (!stringValue(device.get("macAddress")).isBlank()) {
                        devices.add(device);
                    }
                }
            }
            return devices;
        }

        String legacyMacAddress = stringValue(record.get("macAddress"));
        if (legacyMacAddress.isBlank()) {
            return new ArrayList<>();
        }

        Map<String, Object> legacyDevice = new LinkedHashMap<>();
        legacyDevice.put("macAddress", legacyMacAddress);
        legacyDevice.put("hostName", stringValue(record.get("hostName")));
        legacyDevice.put("activatedAt", stringValue(record.get("createdAt")));
        return new ArrayList<>(List.of(legacyDevice));
    }

    private int legacyDeviceLimit(Map<String, Object> record) {
        return stringValue(record.get("macAddress")).isBlank() ? 1 : Math.max(1, boundDevices(record).size());
    }

    private String generateUniqueCode(Set<String> existingCodes) {
        String code;
        do {
            code = randomGroup() + "-" + randomGroup() + "-" + randomGroup();
        } while (existingCodes.contains(code));
        return code;
    }

    private String randomGroup() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            builder.append(CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)]);
        }
        return builder.toString();
    }

    private String normalizeActivationCode(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeMacAddress(String value) {
        String cleaned = value == null ? "" : value.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
        if (!MAC_PATTERN.matcher(cleaned).matches()) {
            return "";
        }
        return cleaned.replaceAll("(.{2})(?!$)", "$1:");
    }

    private boolean isExpired(String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank()) return false;
        try {
            return LocalDateTime.parse(expiresAt, DATE_TIME_FORMATTER).isBefore(LocalDateTime.now());
        } catch (Exception ignored) {
            return false;
        }
    }

    private String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(stringValue(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
