package org.example.backend.controller;

import org.example.backend.result.Result;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.ModuleRecordService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
public class ActivationCodeController {
    private static final String MODULE_NAME = "activation-codes";
    private static final String AUDIT_MODULE_NAME = "activation-code-audit-logs";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_DISABLED = "disabled";
    private static final String STATUS_DELETED = "deleted";
    private static final String BINDING_ACTIVE = "ACTIVE";
    private static final String BINDING_UNBOUND = "UNBOUND";
    private static final String BINDING_REMOTE_UNBOUND = "REMOTE_UNBOUND";
    private static final String CLIENT_STATUS_VALID = "valid";
    private static final String CLIENT_STATUS_INVALID = "invalid";
    private static final String CLIENT_STATUS_DISABLED = "disabled";
    private static final String CLIENT_STATUS_EXPIRED = "expired";
    private static final String CLIENT_STATUS_REMOTE_UNBOUND = "remote_unbound";
    private static final String CLIENT_STATUS_QUOTA_EXCEEDED = "quota_exceeded";
    private static final Pattern MAC_PATTERN = Pattern.compile("^[0-9A-F]{12}$");
    private static final char[] CODE_CHARS = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ModuleRecordService moduleRecordService;
    private final BusinessLoopService businessLoopService;
    private final JdbcTemplate jdbcTemplate;

    public ActivationCodeController(
            ModuleRecordService moduleRecordService,
            BusinessLoopService businessLoopService,
            JdbcTemplate jdbcTemplate
    ) {
        this.moduleRecordService = moduleRecordService;
        this.businessLoopService = businessLoopService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping({"/activation-codes", "/admin/activation-codes"})
    public Result listActivationCodes(@RequestParam(required = false) Map<String, String> params) {
        Map<String, String> query = params == null ? Map.of() : params;
        List<Map<String, Object>> records = sortedRecords().stream()
                .filter((record) -> !booleanValue(record.get("deleted")))
                .filter((record) -> matchesKeyword(record, query.get("keyword")))
                .filter((record) -> matchesStatus(record, query.get("status")))
                .filter((record) -> matchesExpireStatus(record, query.get("expireStatus")))
                .toList();

        int total = records.size();
        int page = Math.max(intValue(query.get("page"), 1), 1);
        int size = Math.max(intValue(query.get("size"), total == 0 ? 10 : total), 1);
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("records", records.subList(fromIndex, toIndex));
        payload.put("total", total);
        payload.put("statistics", buildStatistics(sortedRecords()));
        return Result.success("获取激活码列表成功", payload);
    }

    @GetMapping("/activation-codes/lab-bindings")
    public Result listLabActivationBindings() {
        List<Map<String, Object>> records = sortedRecords().stream()
                .filter((record) -> !booleanValue(record.get("deleted")))
                .filter(this::hasLabBinding)
                .map(this::toLabActivationBinding)
                .toList();
        return Result.success("获取实验室激活码绑定成功", records);
    }

    @PostMapping({"/activation-codes", "/admin/activation-codes"})
    public Result createActivationCodes(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> request = payload == null ? Map.of() : payload;
        int deviceLimit = intValue(request.get("deviceLimit"), 1);
        int quantity = intValue(request.get("quantity"), 1);

        if (deviceLimit < 1) {
            return Result.error("设备绑定数量至少为 1 台");
        }
        if (quantity < 1) {
            return Result.error("生成数量至少为 1 个");
        }
        if (quantity > 100) {
            return Result.error("单次最多生成 100 个激活码");
        }
        if (deviceLimit > 10000) {
            return Result.error("单个激活码最多允许绑定 10000 台设备");
        }

        List<Map<String, Object>> records = new ArrayList<>(rawRecords());
        Set<String> existingCodes = records.stream()
                .map((record) -> normalizeActivationCode(stringValue(record.get("code"))))
                .collect(Collectors.toSet());
        List<Map<String, Object>> createdRecords = new ArrayList<>();
        List<Map<String, Object>> createdCodes = new ArrayList<>();
        String now = nowText();
        boolean enabled = !request.containsKey("enabled") || booleanValue(request.get("enabled"));

        for (int index = 0; index < quantity; index++) {
            String code = generateUniqueCode(existingCodes);
            existingCodes.add(normalizeActivationCode(code));

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", code);
            record.put("code", code);
            record.put("codeHash", hashCode(code));
            record.put("maskedCode", maskActivationCode(code));
            record.put("deviceLimit", deviceLimit);
            record.put("boundDevices", List.of());
            record.put("boundCount", 0);
            record.put("labId", "");
            record.put("labCode", "");
            record.put("labName", "");
            record.put("labBoundAt", "");
            record.put("ownerName", stringValue(firstPresent(request, "ownerName", "owner")));
            record.put("owner", stringValue(firstPresent(request, "ownerName", "owner")));
            record.put("remark", stringValue(request.get("remark")));
            record.put("expireAt", normalizeDateTime(stringValue(firstPresent(request, "expireAt", "expiresAt"))));
            record.put("expiresAt", normalizeDateTime(stringValue(firstPresent(request, "expireAt", "expiresAt"))));
            record.put("enabled", enabled);
            record.put("status", enabled ? STATUS_ACTIVE : STATUS_DISABLED);
            record.put("createdAt", now);
            record.put("updatedAt", now);
            record.put("lastCheckAt", "");
            record.put("deleted", false);

            records.add(0, record);
            createdRecords.add(normalizeRecordForResponse(record));

            Map<String, Object> codePayload = new LinkedHashMap<>();
            codePayload.put("id", code);
            codePayload.put("code", code);
            codePayload.put("maskedCode", maskActivationCode(code));
            createdCodes.add(codePayload);

            recordAudit("生成激活码", code, "", "生成激活码", "成功", Map.of("deviceLimit", deviceLimit));
        }

        moduleRecordService.replaceModuleRecords(MODULE_NAME, records);
        createdRecords.forEach(this::upsertLabActivationCode);
        businessLoopService.recordEvent("activation-code", "generate", String.valueOf(createdCodes.size()), "已生成", Map.of("quantity", quantity));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("codes", createdCodes);
        result.put("records", createdRecords);
        result.put("record", createdRecords.isEmpty() ? Map.of() : createdRecords.get(0));
        return Result.success("生成激活码成功", result);
    }

    @PostMapping({"/activation-codes/generate", "/admin/activation-codes/generate"})
    public Result generateActivationCode(@RequestBody(required = false) Map<String, Object> payload) {
        return createActivationCodes(payload);
    }

    @PostMapping("/activation-codes/bind-lab")
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
            return Result.error("请先选择实验室");
        }

        List<Map<String, Object>> records = new ArrayList<>(rawRecords());
        Map<String, Object> target = findRecord(records, code);
        if (target == null || booleanValue(target.get("deleted"))) {
            return Result.error("激活码不存在");
        }
        if (!isRecordEnabled(target)) {
            return Result.error("激活码已停用，无法绑定实验室");
        }
        if (isExpired(expireAt(target))) {
            return Result.error("激活码已过期，无法绑定实验室");
        }

        for (Map<String, Object> record : records) {
            if (sameRecord(record, target) || booleanValue(record.get("deleted"))) {
                continue;
            }
            if (isSameLabBinding(record, labId, labCode, labName) && isRecordEnabled(record)) {
                return Result.error("当前实验室已绑定其他激活码");
            }
        }

        String existingLabId = stringValue(target.get("labId"));
        if (!existingLabId.isBlank() && !existingLabId.equals(labId)) {
            return Result.error("激活码已绑定其他实验室");
        }

        String now = nowText();
        target.put("labId", labId);
        target.put("labCode", labCode);
        target.put("labName", labName);
        target.put("labBoundAt", firstNonBlank(stringValue(target.get("labBoundAt")), now));
        target.put("updatedAt", now);
        refreshRecordCounts(target);

        moduleRecordService.replaceModuleRecords(MODULE_NAME, records);
        upsertLabActivationCode(target);
        recordAudit("绑定实验室", stringValue(target.get("code")), "", labName, "成功", Map.of("labId", labId, "labCode", labCode));
        businessLoopService.recordEvent("activation-code", "bind-lab", stringValue(target.get("code")), "已绑定实验室", Map.of("labId", labId, "labCode", labCode));
        return Result.success("实验室激活码绑定成功", toLabActivationBinding(normalizeRecordForResponse(target)));
    }

    @PostMapping("/activation-codes/{code}/unbind-lab")
    public Result unbindActivationCodeFromLab(@PathVariable String code) {
        List<Map<String, Object>> records = new ArrayList<>(rawRecords());
        Map<String, Object> target = findRecord(records, code);
        if (target == null || booleanValue(target.get("deleted"))) {
            return Result.error("激活码不存在");
        }

        String labId = stringValue(target.get("labId"));
        String labCode = stringValue(target.get("labCode"));
        target.put("labId", "");
        target.put("labCode", "");
        target.put("labName", "");
        target.put("labBoundAt", "");
        target.put("updatedAt", nowText());
        refreshRecordCounts(target);

        moduleRecordService.replaceModuleRecords(MODULE_NAME, records);
        recordAudit("解绑实验室", stringValue(target.get("code")), "", labId, "成功", Map.of("labId", labId, "labCode", labCode));
        businessLoopService.recordEvent("activation-code", "unbind-lab", stringValue(target.get("code")), "已解绑实验室", Map.of("labId", labId, "labCode", labCode));
        return Result.success("实验室激活码绑定已取消", toLabActivationBinding(normalizeRecordForResponse(target)));
    }

    @PutMapping({"/activation-codes/{id}/status", "/admin/activation-codes/{id}/status"})
    public Result updateActivationCodeStatus(@PathVariable String id, @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> request = payload == null ? Map.of() : payload;
        boolean enabled = booleanValue(request.get("enabled"));
        String reason = stringValue(request.get("reason"));
        List<Map<String, Object>> records = new ArrayList<>(rawRecords());
        Map<String, Object> target = findRecord(records, id);

        if (target == null) {
            return Result.error("激活码不存在");
        }

        target.put("enabled", enabled);
        target.put("status", enabled ? STATUS_ACTIVE : STATUS_DISABLED);
        target.put("updatedAt", nowText());
        moduleRecordService.replaceModuleRecords(MODULE_NAME, records);
        recordAudit(enabled ? "启用" : "停用", stringValue(target.get("code")), "", reason, "成功", Map.of("enabled", enabled));
        businessLoopService.recordEvent("activation-code", enabled ? "enable" : "disable", stringValue(target.get("code")), enabled ? "已启用" : "已停用", Map.of("reason", reason));
        return Result.success(enabled ? "启用激活码成功" : "停用激活码成功", normalizeRecordForResponse(target));
    }

    @PostMapping({"/activation-codes/{code}/disable", "/admin/activation-codes/{code}/disable"})
    public Result disableActivationCode(@PathVariable String code) {
        return updateActivationCodeStatus(code, Map.of("enabled", false, "reason", "手动停用"));
    }

    @PutMapping({"/activation-codes/{id}/remark", "/admin/activation-codes/{id}/remark"})
    public Result updateActivationCodeRemark(@PathVariable String id, @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> request = payload == null ? Map.of() : payload;
        List<Map<String, Object>> records = new ArrayList<>(rawRecords());
        Map<String, Object> target = findRecord(records, id);

        if (target == null) {
            return Result.error("激活码不存在");
        }

        if (request.containsKey("remark")) {
            target.put("remark", stringValue(request.get("remark")));
        }
        if (request.containsKey("ownerName") || request.containsKey("owner")) {
            String ownerName = stringValue(firstPresent(request, "ownerName", "owner"));
            target.put("ownerName", ownerName);
            target.put("owner", ownerName);
        }
        if (request.containsKey("expireAt") || request.containsKey("expiresAt")) {
            String expireAt = normalizeDateTime(stringValue(firstPresent(request, "expireAt", "expiresAt")));
            target.put("expireAt", expireAt);
            target.put("expiresAt", expireAt);
        }
        target.put("updatedAt", nowText());

        moduleRecordService.replaceModuleRecords(MODULE_NAME, records);
        recordAudit("修改备注", stringValue(target.get("code")), "", stringValue(request.get("reason")), "成功", Map.of("remark", target.get("remark")));
        return Result.success("激活码信息已更新", normalizeRecordForResponse(target));
    }

    @GetMapping({"/activation-codes/{id}/bindings", "/admin/activation-codes/{id}/bindings"})
    public Result listActivationCodeBindings(@PathVariable String id) {
        Map<String, Object> target = findRecord(rawRecords(), id);
        if (target == null) {
            return Result.error("激活码不存在");
        }
        return Result.success("获取绑定设备成功", boundDevices(target));
    }

    @PostMapping({"/activation-codes/{id}/bindings/remote-bind", "/admin/activation-codes/{id}/bindings/remote-bind"})
    public Result remoteBindActivationDevice(@PathVariable String id, @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> request = payload == null ? Map.of() : payload;
        String machineCode = normalizeMachineCode(stringValue(firstPresent(request, "machineCode", "deviceFingerprint", "deviceId")));
        String macAddress = normalizeMacAddress(stringValue(request.get("macAddress")));
        String now = nowText();

        if (machineCode.isBlank()) {
            return Result.error("请填写设备标识");
        }

        List<Map<String, Object>> records = new ArrayList<>(rawRecords());
        Map<String, Object> target = findRecord(records, id);
        if (target == null) {
            return Result.error("激活码不存在");
        }
        if (!isRecordEnabled(target)) {
            return Result.error("激活码已停用，无法远程绑定");
        }
        if (isExpired(expireAt(target))) {
            return Result.error("激活码已过期，无法远程绑定");
        }

        List<Map<String, Object>> targetBindings = boundDevices(target);
        if (findActiveBinding(targetBindings, machineCode, macAddress) != null) {
            return Result.error("该设备已绑定到当前激活码");
        }

        Map<String, Object> conflictRecord = null;
        Map<String, Object> conflictBinding = null;
        for (Map<String, Object> record : records) {
            if (sameRecord(record, target)) continue;
            Map<String, Object> binding = findActiveBinding(boundDevices(record), machineCode, macAddress);
            if (binding != null) {
                conflictRecord = record;
                conflictBinding = binding;
                break;
            }
        }

        boolean force = booleanValue(firstPresent(request, "force", "migrate"));
        if (conflictRecord != null && !force) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("conflict", true);
            result.put("message", "该设备已绑定到其他激活码");
            result.put("activationCode", maskActivationCode(stringValue(conflictRecord.get("code"))));
            result.put("binding", conflictBinding);
            return Result.success("设备绑定存在冲突", result);
        }

        if (conflictRecord != null && conflictBinding != null) {
            markBindingUnbound(conflictBinding, BINDING_REMOTE_UNBOUND, "管理员迁移绑定", now);
            conflictRecord.put("boundDevices", boundDevices(conflictRecord));
            refreshRecordCounts(conflictRecord);
            recordAudit("远程解绑", stringValue(conflictRecord.get("code")), stringValue(conflictBinding.get("machineCode")), "管理员迁移绑定", "成功", Map.of("migratedTo", target.get("code")));
        }

        if (activeBindingCount(targetBindings) >= intValue(target.get("deviceLimit"), legacyDeviceLimit(target))) {
            return Result.error("设备额度已满，无法远程绑定");
        }

        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("id", "binding-" + UUID.randomUUID());
        binding.put("deviceId", machineCode);
        binding.put("machineCode", machineCode);
        binding.put("macAddress", macAddress);
        binding.put("hostname", stringValue(firstPresent(request, "hostname", "hostName")));
        binding.put("hostName", stringValue(firstPresent(request, "hostname", "hostName")));
        binding.put("clientSource", stringValue(request.get("clientSource")));
        binding.put("clientVersion", stringValue(request.get("clientVersion")));
        binding.put("ipAddress", stringValue(request.get("ipAddress")));
        binding.put("status", BINDING_ACTIVE);
        binding.put("boundAt", now);
        binding.put("activatedAt", now);
        binding.put("lastSeenAt", now);
        binding.put("remark", stringValue(request.get("remark")));
        binding.put("remoteBound", true);
        putTerminalCredentials(binding, machineCode);

        targetBindings.add(binding);
        target.put("boundDevices", targetBindings);
        target.put("updatedAt", now);
        refreshRecordCounts(target);

        moduleRecordService.replaceModuleRecords(MODULE_NAME, records);
        upsertLabActivationCode(target);
        upsertLabTerminalBinding(target, binding, now);
        recordAudit("远程绑定", stringValue(target.get("code")), machineCode, stringValue(request.get("remark")), "成功", Map.of("macAddress", macAddress));
        businessLoopService.recordEvent("activation-code", "remote-bind", stringValue(target.get("code")), "远程绑定设备", Map.of("machineCode", machineCode));
        return Result.success("远程绑定成功", Map.of("record", normalizeRecordForResponse(target), "binding", binding));
    }

    @PostMapping({"/activation-codes/{id}/bindings/{bindingId}/remote-unbind", "/admin/activation-codes/{id}/bindings/{bindingId}/remote-unbind"})
    public Result remoteUnbindActivationDevice(
            @PathVariable String id,
            @PathVariable String bindingId,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        Map<String, Object> request = payload == null ? Map.of() : payload;
        String reason = stringValue(request.get("reason"));
        if (reason.isBlank()) {
            return Result.error("请填写解绑原因");
        }

        List<Map<String, Object>> records = new ArrayList<>(rawRecords());
        Map<String, Object> target = findRecord(records, id);
        if (target == null) {
            return Result.error("激活码不存在");
        }

        List<Map<String, Object>> bindings = boundDevices(target);
        Map<String, Object> binding = findBindingById(bindings, bindingId);
        if (binding == null) {
            return Result.error("绑定设备不存在");
        }

        markBindingUnbound(binding, BINDING_REMOTE_UNBOUND, reason, nowText());
        target.put("boundDevices", bindings);
        target.put("updatedAt", nowText());
        refreshRecordCounts(target);

        moduleRecordService.replaceModuleRecords(MODULE_NAME, records);
        recordAudit("远程解绑", stringValue(target.get("code")), stringValue(binding.get("machineCode")), reason, "成功", Map.of("bindingId", bindingId));
        businessLoopService.recordEvent("activation-code", "remote-unbind", stringValue(target.get("code")), "远程解绑设备", Map.of("bindingId", bindingId));
        return Result.success("远程解绑成功，客户端下次校验将失效", Map.of("record", normalizeRecordForResponse(target), "binding", binding));
    }

    @PostMapping({"/activation-codes/batch", "/admin/activation-codes/batch"})
    public Result batchOperateActivationCodes(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> request = payload == null ? Map.of() : payload;
        List<String> ids = listValue(request.get("ids")).stream().map(String::valueOf).toList();
        String action = stringValue(request.get("action")).toUpperCase(Locale.ROOT);
        String reason = stringValue(request.get("reason"));

        if (ids.isEmpty()) {
            return Result.error("请选择需要批量操作的激活码");
        }
        if (action.isBlank()) {
            return Result.error("请选择批量操作类型");
        }

        List<Map<String, Object>> records = new ArrayList<>(rawRecords());
        int affected = 0;
        for (Map<String, Object> record : records) {
            if (!ids.contains(stringValue(record.get("id"))) && !ids.contains(stringValue(record.get("code")))) continue;
            affected++;
            switch (action) {
                case "ENABLE" -> {
                    record.put("enabled", true);
                    record.put("status", STATUS_ACTIVE);
                }
                case "DISABLE" -> {
                    record.put("enabled", false);
                    record.put("status", STATUS_DISABLED);
                }
                case "DELETE" -> {
                    record.put("deleted", true);
                    record.put("status", STATUS_DELETED);
                }
                case "EXTEND_EXPIRE_AT" -> {
                    String expireAt = normalizeDateTime(stringValue(firstPresent(request, "expireAt", "expiresAt")));
                    record.put("expireAt", expireAt);
                    record.put("expiresAt", expireAt);
                }
                case "UPDATE_OWNER" -> {
                    String ownerName = stringValue(firstPresent(request, "ownerName", "owner"));
                    record.put("ownerName", ownerName);
                    record.put("owner", ownerName);
                }
                default -> {
                    return Result.error("不支持的批量操作类型");
                }
            }
            record.put("updatedAt", nowText());
            recordAudit("批量操作", stringValue(record.get("code")), "", reason.isBlank() ? action : reason, "成功", Map.of("action", action));
        }

        moduleRecordService.replaceModuleRecords(MODULE_NAME, records);
        return Result.success("批量操作成功", Map.of("affected", affected, "records", sortedRecords()));
    }

    @GetMapping({"/activation-codes/audit-logs", "/admin/activation-codes/audit-logs"})
    public Result listActivationAuditLogs() {
        List<Map<String, Object>> logs = new ArrayList<>(moduleRecordService.listModuleRecords(AUDIT_MODULE_NAME));
        logs.sort(Comparator.comparing((Map<String, Object> log) -> stringValue(log.get("createdAt"))).reversed());
        return Result.success("获取审计日志成功", logs);
    }

    @PostMapping({"/activation-codes/verify", "/admin/activation-codes/verify"})
    public Result verifyActivationCode(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> request = payload == null ? Map.of() : payload;
        String code = normalizeActivationCode(stringValue(request.get("code")));
        String macAddress = normalizeMacAddress(stringValue(request.get("macAddress")));
        String machineCode = normalizeMachineCode(stringValue(firstPresent(request, "machineCode", "deviceFingerprint", "deviceId")));
        String hostname = stringValue(firstPresent(request, "hostname", "hostName"));
        String now = nowText();

        if (machineCode.isBlank()) {
            machineCode = macAddress;
        }
        if (machineCode.isBlank()) {
            return Result.success("校验激活码完成", verificationResult(false, CLIENT_STATUS_INVALID, "无法获取有效设备标识"));
        }

        List<Map<String, Object>> records = new ArrayList<>(rawRecords());
        Map<String, Object> target = findRecord(records, code);
        if (target == null || booleanValue(target.get("deleted"))) {
            return Result.success("校验激活码完成", verificationResult(false, CLIENT_STATUS_INVALID, "激活码不存在"));
        }
        if (!isRecordEnabled(target)) {
            return Result.success("校验激活码完成", verificationResult(false, CLIENT_STATUS_DISABLED, "激活码已停用"));
        }
        if (isExpired(expireAt(target))) {
            return Result.success("校验激活码完成", verificationResult(false, CLIENT_STATUS_EXPIRED, "激活码已过期"));
        }

        List<Map<String, Object>> bindings = boundDevices(target);
        Map<String, Object> binding = findBinding(bindings, machineCode, macAddress);
        if (binding != null && !BINDING_ACTIVE.equals(stringValue(binding.get("status")))) {
            return Result.success("校验激活码完成", verificationResult(false, CLIENT_STATUS_REMOTE_UNBOUND, "该设备已被管理员远程解绑，请重新激活"));
        }

        int deviceLimit = intValue(target.get("deviceLimit"), legacyDeviceLimit(target));
        if (binding == null && activeBindingCount(bindings) >= deviceLimit) {
            return Result.success("校验激活码完成", verificationResult(false, CLIENT_STATUS_QUOTA_EXCEEDED, "激活码设备额度已满"));
        }

        if (binding == null) {
            binding = new LinkedHashMap<>();
            binding.put("id", "binding-" + UUID.randomUUID());
            binding.put("deviceId", machineCode);
            binding.put("machineCode", machineCode);
            binding.put("macAddress", macAddress);
            binding.put("hostname", hostname);
            binding.put("hostName", hostname);
            binding.put("clientSource", stringValue(request.get("clientSource")));
            binding.put("clientVersion", stringValue(request.get("clientVersion")));
            binding.put("ipAddress", stringValue(request.get("ipAddress")));
            binding.put("status", BINDING_ACTIVE);
            binding.put("boundAt", now);
            binding.put("activatedAt", now);
            binding.put("remark", "");
            putTerminalCredentials(binding, machineCode);
            bindings.add(binding);
            recordAudit("设备激活", stringValue(target.get("code")), machineCode, "客户端校验绑定", "成功", Map.of("macAddress", macAddress));
        }

        putTerminalCredentials(binding, machineCode);
        binding.put("lastSeenAt", now);
        binding.put("clientVersion", firstNonBlank(stringValue(request.get("clientVersion")), stringValue(binding.get("clientVersion"))));
        binding.put("ipAddress", firstNonBlank(stringValue(request.get("ipAddress")), stringValue(binding.get("ipAddress"))));
        target.put("boundDevices", bindings);
        target.put("lastCheckAt", now);
        target.put("updatedAt", now);
        refreshRecordCounts(target);
        moduleRecordService.replaceModuleRecords(MODULE_NAME, records);
        upsertLabActivationCode(target);
        upsertLabTerminalBinding(target, binding, now);

        Map<String, Object> result = normalizeRecordForResponse(target);
        result.put("valid", true);
        result.put("status", CLIENT_STATUS_VALID);
        result.put("message", "激活码校验通过");
        result.put("macAddress", macAddress);
        result.put("machineCode", machineCode);
        result.put("terminalId", binding.get("terminalId"));
        result.put("terminalToken", binding.get("terminalToken"));
        result.put("binding", binding);
        result.put("remainingQuota", Math.max(deviceLimit - activeBindingCount(bindings), 0));
        return Result.success("校验激活码完成", result);
    }

    private List<Map<String, Object>> rawRecords() {
        return new ArrayList<>(moduleRecordService.listModuleRecords(MODULE_NAME));
    }

    private List<Map<String, Object>> sortedRecords() {
        List<Map<String, Object>> records = rawRecords().stream().map(this::normalizeRecordForResponse).collect(Collectors.toCollection(ArrayList::new));
        records.sort(Comparator.comparing((Map<String, Object> record) -> stringValue(record.get("createdAt"))).reversed());
        return records;
    }

    private Map<String, Object> normalizeRecordForResponse(Map<String, Object> source) {
        Map<String, Object> record = new LinkedHashMap<>(source);
        String code = stringValue(record.get("code"));
        String expireAt = expireAt(record);
        List<Map<String, Object>> bindings = boundDevices(record);
        int deviceLimit = intValue(record.get("deviceLimit"), legacyDeviceLimit(record));
        int boundCount = activeBindingCount(bindings);

        record.put("id", firstNonBlank(stringValue(record.get("id")), code));
        record.put("code", code);
        record.put("codeHash", firstNonBlank(stringValue(record.get("codeHash")), hashCode(code)));
        record.put("maskedCode", firstNonBlank(stringValue(record.get("maskedCode")), maskActivationCode(code)));
        record.put("deviceLimit", deviceLimit);
        record.put("boundDevices", bindings);
        record.put("boundCount", boundCount);
        record.put("availableCount", Math.max(deviceLimit - boundCount, 0));
        record.put("ownerName", firstNonBlank(stringValue(record.get("ownerName")), stringValue(record.get("owner"))));
        record.put("owner", firstNonBlank(stringValue(record.get("owner")), stringValue(record.get("ownerName"))));
        record.put("expireAt", expireAt);
        record.put("expiresAt", expireAt);
        record.put("enabled", isRecordEnabled(record));
        record.put("status", isRecordEnabled(record) ? STATUS_ACTIVE : STATUS_DISABLED);
        record.put("statusKey", resolveStatusKey(record));
        record.put("statusText", resolveStatusText(record));
        record.put("expireStatus", resolveExpireStatus(expireAt));
        record.put("deleted", booleanValue(record.get("deleted")));
        return record;
    }

    private Map<String, Object> toLabActivationBinding(Map<String, Object> source) {
        Map<String, Object> record = normalizeRecordForResponse(source);
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("id", record.get("id"));
        binding.put("code", record.get("code"));
        binding.put("maskedCode", record.get("maskedCode"));
        binding.put("deviceLimit", record.get("deviceLimit"));
        binding.put("boundDevices", record.get("boundDevices"));
        binding.put("boundCount", record.get("boundCount"));
        binding.put("availableCount", record.get("availableCount"));
        binding.put("labId", stringValue(record.get("labId")));
        binding.put("labCode", stringValue(record.get("labCode")));
        binding.put("labName", stringValue(record.get("labName")));
        binding.put("labBoundAt", stringValue(record.get("labBoundAt")));
        binding.put("enabled", record.get("enabled"));
        binding.put("status", record.get("status"));
        binding.put("statusKey", record.get("statusKey"));
        binding.put("statusText", record.get("statusText"));
        binding.put("expireAt", record.get("expireAt"));
        binding.put("expiresAt", record.get("expiresAt"));
        binding.put("expireStatus", record.get("expireStatus"));
        return binding;
    }

    private boolean hasLabBinding(Map<String, Object> record) {
        return !stringValue(record.get("labId")).isBlank()
                || !stringValue(record.get("labCode")).isBlank()
                || !stringValue(record.get("labName")).isBlank();
    }

    private boolean isSameLabBinding(Map<String, Object> record, String labId, String labCode, String labName) {
        String currentLabId = stringValue(record.get("labId"));
        if (!labId.isBlank() && !currentLabId.isBlank() && currentLabId.equals(labId)) {
            return true;
        }
        String currentLabCode = normalizeActivationCode(stringValue(record.get("labCode")));
        if (!labCode.isBlank() && !currentLabCode.isBlank() && currentLabCode.equals(normalizeActivationCode(labCode))) {
            return true;
        }
        String currentLabName = normalizeActivationCode(stringValue(record.get("labName")));
        return !labName.isBlank() && !currentLabName.isBlank() && currentLabName.equals(normalizeActivationCode(labName));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> boundDevices(Map<String, Object> record) {
        Object rawDevices = record.get("boundDevices");
        List<Map<String, Object>> devices = new ArrayList<>();
        if (rawDevices instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> rawItem) {
                    Map<String, Object> device = new LinkedHashMap<>();
                    rawItem.forEach((key, value) -> device.put(String.valueOf(key), value));
                    normalizeBinding(device);
                    if (!stringValue(device.get("machineCode")).isBlank() || !stringValue(device.get("macAddress")).isBlank()) {
                        devices.add(device);
                    }
                }
            }
            return devices;
        }

        String legacyMacAddress = normalizeMacAddress(stringValue(record.get("macAddress")));
        if (legacyMacAddress.isBlank()) {
            return devices;
        }

        Map<String, Object> legacyDevice = new LinkedHashMap<>();
        legacyDevice.put("id", legacyMacAddress);
        legacyDevice.put("deviceId", legacyMacAddress);
        legacyDevice.put("machineCode", legacyMacAddress);
        legacyDevice.put("macAddress", legacyMacAddress);
        legacyDevice.put("hostname", stringValue(firstPresent(record, "hostname", "hostName")));
        legacyDevice.put("hostName", stringValue(firstPresent(record, "hostname", "hostName")));
        legacyDevice.put("status", BINDING_ACTIVE);
        legacyDevice.put("boundAt", stringValue(firstPresent(record, "activatedAt", "createdAt")));
        legacyDevice.put("activatedAt", stringValue(firstPresent(record, "activatedAt", "createdAt")));
        normalizeBinding(legacyDevice);
        devices.add(legacyDevice);
        return devices;
    }

    private void normalizeBinding(Map<String, Object> device) {
        String machineCode = normalizeMachineCode(firstNonBlank(
                stringValue(device.get("machineCode")),
                stringValue(device.get("deviceFingerprint")),
                stringValue(device.get("deviceId")),
                stringValue(device.get("macAddress"))
        ));
        String macAddress = normalizeMacAddress(stringValue(device.get("macAddress")));
        String hostname = firstNonBlank(stringValue(device.get("hostname")), stringValue(device.get("hostName")));
        String id = firstNonBlank(stringValue(device.get("id")), machineCode, macAddress);

        device.put("id", id);
        device.put("deviceId", firstNonBlank(stringValue(device.get("deviceId")), machineCode));
        device.put("terminalId", stringValue(device.get("terminalId")));
        device.put("terminalToken", stringValue(device.get("terminalToken")));
        device.put("machineCode", machineCode);
        device.put("macAddress", macAddress);
        device.put("hostname", hostname);
        device.put("hostName", hostname);
        device.put("clientSource", stringValue(device.get("clientSource")));
        device.put("clientVersion", stringValue(device.get("clientVersion")));
        device.put("ipAddress", stringValue(device.get("ipAddress")));
        device.put("status", firstNonBlank(stringValue(device.get("status")), BINDING_ACTIVE));
        device.put("boundAt", firstNonBlank(stringValue(device.get("boundAt")), stringValue(device.get("activatedAt"))));
        device.put("activatedAt", firstNonBlank(stringValue(device.get("activatedAt")), stringValue(device.get("boundAt"))));
        device.put("lastSeenAt", stringValue(device.get("lastSeenAt")));
        device.put("unboundAt", stringValue(device.get("unboundAt")));
        device.put("unboundBy", stringValue(device.get("unboundBy")));
        device.put("unbindReason", stringValue(device.get("unbindReason")));
        device.put("remark", stringValue(device.get("remark")));
    }

    private void putTerminalCredentials(Map<String, Object> binding, String machineCode) {
        String terminalId = firstNonBlank(
                stringValue(binding.get("terminalId")),
                "terminal-" + UUID.randomUUID()
        );
        String terminalToken = firstNonBlank(
                stringValue(binding.get("terminalToken")),
                generateTerminalToken(machineCode)
        );
        binding.put("terminalId", terminalId);
        binding.put("terminalToken", terminalToken);
    }

    private Long upsertLabActivationCode(Map<String, Object> record) {
        Long labId = longValue(record.get("labId"));
        String code = stringValue(record.get("code"));
        String codeHash = firstNonBlank(stringValue(record.get("codeHash")), hashCode(code));
        if (labId == null || codeHash.isBlank()) {
            return null;
        }

        String expiresAt = expireAt(record);
        String status = isExpired(expiresAt) ? "expired" : (isRecordEnabled(record) ? "active" : "inactive");
        try {
            jdbcTemplate.update("""
                    insert into lab_activation_code (
                      lab_id, lab_code, lab_name, code_hash, masked_code, status, enabled,
                      terminal_quota, bound_terminal_count, expire_at, source_type, remark,
                      created_by, created_by_name, deleted
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    on duplicate key update
                      lab_id = values(lab_id),
                      lab_code = values(lab_code),
                      lab_name = values(lab_name),
                      masked_code = values(masked_code),
                      status = values(status),
                      enabled = values(enabled),
                      terminal_quota = values(terminal_quota),
                      bound_terminal_count = values(bound_terminal_count),
                      expire_at = values(expire_at),
                      source_type = values(source_type),
                      remark = values(remark),
                      created_by_name = values(created_by_name),
                      deleted = 0,
                      updated_at = current_timestamp
                    """,
                    labId,
                    stringValue(record.get("labCode")),
                    stringValue(record.get("labName")),
                    codeHash,
                    firstNonBlank(stringValue(record.get("maskedCode")), maskActivationCode(code)),
                    status,
                    isRecordEnabled(record) ? 1 : 0,
                    intValue(record.get("deviceLimit"), legacyDeviceLimit(record)),
                    activeBindingCount(boundDevices(record)),
                    parseDateTime(expiresAt),
                    "manual",
                    stringValue(record.get("remark")),
                    null,
                    firstNonBlank(stringValue(record.get("ownerName")), stringValue(record.get("owner")))
            );
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select id from lab_activation_code where code_hash = ? and deleted = 0 limit 1",
                    codeHash
            );
            return rows.isEmpty() ? null : longValue(rows.get(0).get("id"));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void upsertLabTerminalBinding(Map<String, Object> record, Map<String, Object> binding, String now) {
        Long labId = longValue(record.get("labId"));
        String terminalId = stringValue(binding.get("terminalId"));
        String terminalToken = stringValue(binding.get("terminalToken"));
        if (labId == null || terminalId.isBlank() || terminalToken.isBlank()) {
            return;
        }

        Long activationCodeId = upsertLabActivationCode(record);
        LocalDateTime boundAt = parseDateTime(firstNonBlank(stringValue(binding.get("boundAt")), stringValue(binding.get("activatedAt")), now));
        LocalDateTime lastSeenAt = parseDateTime(firstNonBlank(stringValue(binding.get("lastSeenAt")), now));
        String hostName = firstNonBlank(stringValue(binding.get("hostName")), stringValue(binding.get("hostname")));
        try {
            jdbcTemplate.update("""
                    insert into lab_terminal (
                      terminal_id, terminal_token_hash, lab_id, lab_code, lab_name, activation_code_id,
                      terminal_name, terminal_type, host_name, machine_code, mac_address, ip_address,
                      client_version, status, bound_at, first_connected_at, last_seen_at, source_type,
                      remark, deleted
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'active', ?, ?, ?, 'activation_code', ?, 0)
                    on duplicate key update
                      terminal_token_hash = values(terminal_token_hash),
                      lab_id = values(lab_id),
                      lab_code = values(lab_code),
                      lab_name = values(lab_name),
                      activation_code_id = values(activation_code_id),
                      terminal_name = values(terminal_name),
                      terminal_type = values(terminal_type),
                      host_name = values(host_name),
                      machine_code = values(machine_code),
                      mac_address = values(mac_address),
                      ip_address = values(ip_address),
                      client_version = values(client_version),
                      status = 'active',
                      first_connected_at = coalesce(first_connected_at, values(first_connected_at)),
                      last_seen_at = values(last_seen_at),
                      source_type = values(source_type),
                      remark = values(remark),
                      deleted = 0,
                      updated_at = current_timestamp
                    """,
                    terminalId,
                    hashTerminalToken(terminalToken),
                    labId,
                    stringValue(record.get("labCode")),
                    stringValue(record.get("labName")),
                    activationCodeId,
                    firstNonBlank(hostName, stringValue(binding.get("machineCode")), stringValue(binding.get("macAddress")), terminalId),
                    terminalTypeFromSource(stringValue(binding.get("clientSource"))),
                    hostName,
                    stringValue(binding.get("machineCode")),
                    stringValue(binding.get("macAddress")),
                    stringValue(binding.get("ipAddress")),
                    stringValue(binding.get("clientVersion")),
                    boundAt == null ? LocalDateTime.now() : boundAt,
                    boundAt == null ? LocalDateTime.now() : boundAt,
                    lastSeenAt == null ? LocalDateTime.now() : lastSeenAt,
                    stringValue(binding.get("remark"))
            );
        } catch (RuntimeException ignored) {
            // Keep legacy module_record behavior available while deployments migrate the new split tables.
        }
    }

    private String terminalTypeFromSource(String source) {
        String value = normalizeActivationCode(source);
        if (value.contains("teacher")) return "teacher_client";
        if (value.contains("probe")) return "probe_client";
        return "lab_client";
    }

    private String generateTerminalToken(String machineCode) {
        return "tt-" + normalizeMachineCode(machineCode) + "-" + UUID.randomUUID();
    }

    private Map<String, Object> buildStatistics(List<Map<String, Object>> allRecords) {
        List<Map<String, Object>> records = allRecords.stream().filter((record) -> !booleanValue(record.get("deleted"))).toList();
        int total = records.size();
        int enabled = (int) records.stream().filter((record) -> "enabled".equals(resolveStatusKey(record))).count();
        int disabled = (int) records.stream().filter((record) -> "disabled".equals(resolveStatusKey(record))).count();
        int totalLimit = records.stream().mapToInt((record) -> intValue(record.get("deviceLimit"), 0)).sum();
        int bound = records.stream().mapToInt((record) -> intValue(record.get("boundCount"), activeBindingCount(boundDevices(record)))).sum();
        LocalDate today = LocalDate.now();
        int todayBind = records.stream()
                .flatMap((record) -> boundDevices(record).stream())
                .mapToInt((device) -> isSameDay(stringValue(firstPresent(device, "boundAt", "activatedAt")), today) ? 1 : 0)
                .sum();
        int todayRemoteUnbind = records.stream()
                .flatMap((record) -> boundDevices(record).stream())
                .mapToInt((device) -> BINDING_REMOTE_UNBOUND.equals(stringValue(device.get("status"))) && isSameDay(stringValue(device.get("unboundAt")), today) ? 1 : 0)
                .sum();

        Map<String, Object> statistics = new LinkedHashMap<>();
        statistics.put("totalCodes", total);
        statistics.put("enabledCodes", enabled);
        statistics.put("disabledCodes", disabled);
        statistics.put("totalDeviceLimit", totalLimit);
        statistics.put("boundDevices", bound);
        statistics.put("availableDevices", Math.max(totalLimit - bound, 0));
        statistics.put("todayBindings", todayBind);
        statistics.put("todayRemoteUnbinds", todayRemoteUnbind);
        return statistics;
    }

    private Map<String, Object> verificationResult(boolean valid, String status, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", valid);
        result.put("status", status);
        result.put("message", message);
        return result;
    }

    private void refreshRecordCounts(Map<String, Object> record) {
        List<Map<String, Object>> devices = boundDevices(record);
        int boundCount = activeBindingCount(devices);
        int deviceLimit = intValue(record.get("deviceLimit"), legacyDeviceLimit(record));
        record.put("boundDevices", devices);
        record.put("deviceLimit", deviceLimit);
        record.put("boundCount", boundCount);
        record.put("availableCount", Math.max(deviceLimit - boundCount, 0));
    }

    private void markBindingUnbound(Map<String, Object> binding, String status, String reason, String now) {
        binding.put("status", status);
        binding.put("unboundAt", now);
        binding.put("unboundBy", "系统管理员");
        binding.put("unbindReason", reason);
        if (BINDING_UNBOUND.equals(status) || BINDING_REMOTE_UNBOUND.equals(status)) {
            binding.put("lastSeenAt", firstNonBlank(stringValue(binding.get("lastSeenAt")), now));
        }
    }

    private void recordAudit(String action, String code, String machineCode, String reason, String result, Map<String, Object> details) {
        List<Map<String, Object>> logs = new ArrayList<>(moduleRecordService.listModuleRecords(AUDIT_MODULE_NAME));
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("id", "audit-" + UUID.randomUUID());
        log.put("activationCodeId", code);
        log.put("activationCode", maskActivationCode(code));
        log.put("deviceId", machineCode);
        log.put("machineCode", machineCode);
        log.put("operatorName", "系统管理员");
        log.put("action", action);
        log.put("reason", reason);
        log.put("result", result);
        log.put("beforeData", "");
        log.put("afterData", details == null ? Map.of() : details);
        log.put("createdAt", nowText());
        logs.add(0, log);
        moduleRecordService.replaceModuleRecords(AUDIT_MODULE_NAME, logs.stream().limit(500).toList());
    }

    private boolean matchesKeyword(Map<String, Object> record, String keyword) {
        String searchText = normalizeActivationCode(keyword);
        if (searchText.isBlank()) return true;
        List<String> haystacks = new ArrayList<>();
        haystacks.add(stringValue(record.get("code")));
        haystacks.add(stringValue(record.get("maskedCode")));
        haystacks.add(stringValue(record.get("ownerName")));
        haystacks.add(stringValue(record.get("owner")));
        haystacks.add(stringValue(record.get("remark")));
        for (Map<String, Object> device : boundDevices(record)) {
            haystacks.add(stringValue(device.get("machineCode")));
            haystacks.add(stringValue(device.get("deviceId")));
            haystacks.add(stringValue(device.get("macAddress")));
            haystacks.add(stringValue(device.get("hostname")));
            haystacks.add(stringValue(device.get("hostName")));
        }
        return haystacks.stream().map(this::normalizeActivationCode).anyMatch((value) -> value.contains(searchText));
    }

    private boolean matchesStatus(Map<String, Object> record, String status) {
        String expected = normalizeStatus(status);
        if (expected.isBlank() || "all".equals(expected)) return true;
        return expected.equals(resolveStatusKey(record));
    }

    private boolean matchesExpireStatus(Map<String, Object> record, String expireStatus) {
        String expected = normalizeActivationCode(expireStatus).replace("_", "-");
        if (expected.isBlank() || "all".equals(expected)) return true;
        return expected.equals(resolveExpireStatus(expireAt(record)).replace("_", "-"));
    }

    private String resolveStatusKey(Map<String, Object> record) {
        if (!isRecordEnabled(record)) return "disabled";
        if (isExpired(expireAt(record))) return "expired";
        int deviceLimit = intValue(record.get("deviceLimit"), legacyDeviceLimit(record));
        if (deviceLimit > 0 && activeBindingCount(boundDevices(record)) >= deviceLimit) return "quota_full";
        return "enabled";
    }

    private String resolveStatusText(Map<String, Object> record) {
        return switch (resolveStatusKey(record)) {
            case "disabled" -> "停用";
            case "expired" -> "已过期";
            case "quota_full" -> "额度已满";
            default -> "启用";
        };
    }

    private String resolveExpireStatus(String expireAt) {
        if (expireAt == null || expireAt.isBlank()) return "long_term";
        LocalDateTime expires = parseDateTime(expireAt);
        if (expires == null) return "long_term";
        LocalDateTime now = LocalDateTime.now();
        if (expires.isBefore(now)) return "expired";
        if (expires.isBefore(now.plusDays(30))) return "expiring_soon";
        return "normal";
    }

    private String normalizeStatus(String value) {
        String status = normalizeActivationCode(value);
        return switch (status) {
            case "active", "enabled", "启用", "有效" -> "enabled";
            case "disabled", "停用", "已停用" -> "disabled";
            case "expired", "已过期" -> "expired";
            case "quota-full", "quota_full", "额度已满" -> "quota_full";
            default -> status;
        };
    }

    private Map<String, Object> findRecord(List<Map<String, Object>> records, String idOrCode) {
        String target = normalizeActivationCode(idOrCode);
        if (target.isBlank()) return null;
        for (Map<String, Object> record : records) {
            if (target.equals(normalizeActivationCode(stringValue(record.get("id"))))) return record;
            if (target.equals(normalizeActivationCode(stringValue(record.get("code"))))) return record;
            if (target.equals(normalizeActivationCode(stringValue(record.get("maskedCode"))))) return record;
        }
        return null;
    }

    private Map<String, Object> findActiveBinding(List<Map<String, Object>> bindings, String machineCode, String macAddress) {
        Map<String, Object> binding = findBinding(bindings, machineCode, macAddress);
        return binding != null && BINDING_ACTIVE.equals(stringValue(binding.get("status"))) ? binding : null;
    }

    private Map<String, Object> findBinding(List<Map<String, Object>> bindings, String machineCode, String macAddress) {
        String machine = normalizeMachineCode(machineCode);
        String mac = normalizeMacAddress(macAddress);
        for (Map<String, Object> binding : bindings) {
            String bindingMachine = normalizeMachineCode(stringValue(binding.get("machineCode")));
            String bindingDevice = normalizeMachineCode(stringValue(binding.get("deviceId")));
            String bindingMac = normalizeMacAddress(stringValue(binding.get("macAddress")));
            if (!machine.isBlank() && (machine.equals(bindingMachine) || machine.equals(bindingDevice))) return binding;
            if (!mac.isBlank() && mac.equals(bindingMac)) return binding;
        }
        return null;
    }

    private Map<String, Object> findBindingById(List<Map<String, Object>> bindings, String bindingId) {
        String target = normalizeMachineCode(bindingId);
        for (Map<String, Object> binding : bindings) {
            if (target.equals(normalizeMachineCode(stringValue(binding.get("id"))))) return binding;
            if (target.equals(normalizeMachineCode(stringValue(binding.get("machineCode"))))) return binding;
            if (target.equals(normalizeMachineCode(stringValue(binding.get("deviceId"))))) return binding;
            if (target.equals(normalizeMachineCode(stringValue(binding.get("macAddress"))))) return binding;
        }
        return null;
    }

    private int activeBindingCount(List<Map<String, Object>> devices) {
        return (int) devices.stream().filter((device) -> BINDING_ACTIVE.equals(stringValue(device.get("status")))).count();
    }

    private int legacyDeviceLimit(Map<String, Object> record) {
        return stringValue(record.get("macAddress")).isBlank() ? 1 : Math.max(1, activeBindingCount(boundDevices(record)));
    }

    private boolean isRecordEnabled(Map<String, Object> record) {
        if (booleanValue(record.get("deleted"))) return false;
        if (record.containsKey("enabled")) return booleanValue(record.get("enabled"));
        return !STATUS_DISABLED.equals(stringValue(record.get("status"))) && !STATUS_DELETED.equals(stringValue(record.get("status")));
    }

    private boolean sameRecord(Map<String, Object> left, Map<String, Object> right) {
        return normalizeActivationCode(stringValue(left.get("id"))).equals(normalizeActivationCode(stringValue(right.get("id"))))
                || normalizeActivationCode(stringValue(left.get("code"))).equals(normalizeActivationCode(stringValue(right.get("code"))));
    }

    private String expireAt(Map<String, Object> record) {
        return normalizeDateTime(firstNonBlank(stringValue(record.get("expireAt")), stringValue(record.get("expiresAt"))));
    }

    private String generateUniqueCode(Set<String> existingCodes) {
        String code;
        do {
            code = randomGroup() + "-" + randomGroup() + "-" + randomGroup();
        } while (existingCodes.contains(normalizeActivationCode(code)));
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

    private String normalizeMachineCode(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeMacAddress(String value) {
        String cleaned = value == null ? "" : value.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
        if (!MAC_PATTERN.matcher(cleaned).matches()) {
            return "";
        }
        return cleaned.replaceAll("(.{2})(?!$)", "$1:");
    }

    private String normalizeDateTime(String value) {
        if (value == null || value.isBlank()) return "";
        LocalDateTime dateTime = parseDateTime(value);
        return dateTime == null ? value.trim() : dateTime.format(ISO_FORMATTER);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        List<String> candidates = List.of(value.trim(), value.trim().replace(" ", "T"));
        for (String candidate : candidates) {
            try {
                return LocalDateTime.parse(candidate, ISO_FORMATTER);
            } catch (Exception ignored) {
            }
            try {
                return LocalDateTime.parse(candidate.replace("T", " "), DISPLAY_FORMATTER);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean isExpired(String expiresAt) {
        LocalDateTime dateTime = parseDateTime(expiresAt);
        return dateTime != null && dateTime.isBefore(LocalDateTime.now());
    }

    private boolean isSameDay(String value, LocalDate date) {
        LocalDateTime dateTime = parseDateTime(value);
        return dateTime != null && dateTime.toLocalDate().equals(date);
    }

    private String maskActivationCode(String code) {
        String value = stringValue(code);
        if (value.isBlank()) return "";
        if (value.length() <= 10) return value.substring(0, Math.min(2, value.length())) + "****" + value.substring(Math.max(value.length() - 2, 0));
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private String hashCode(String code) {
        if (code == null || code.isBlank()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizeActivationCode(code).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : hash) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private String hashTerminalToken(String terminalToken) {
        if (terminalToken == null || terminalToken.isBlank()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(terminalToken.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : hash) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private String nowText() {
        return LocalDateTime.now().format(ISO_FORMATTER);
    }

    private Object firstPresent(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) return source.get(key);
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
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

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            String text = stringValue(value);
            return text.isBlank() ? null : Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) return bool;
        String text = stringValue(value);
        if (text.isBlank()) return false;
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text) || "enabled".equalsIgnoreCase(text);
    }

    @SuppressWarnings("unchecked")
    private List<Object> listValue(Object value) {
        if (value instanceof List<?> list) return new ArrayList<>(list);
        if (value == null) return List.of();
        return List.of(value);
    }
}
