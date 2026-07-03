package org.example.backend.device;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceModuleBoundaryTest {
    @Test
    void deviceStatusHasDedicatedRuntimeApisBackedByRuntimeTables() throws IOException {
        String controller = readSource("controller", "DeviceStatusController.java");
        String service = readSource("service", "impl", "DeviceStatusServiceImpl.java");

        assertAll(
                () -> assertTrue(controller.contains("@GetMapping(\"/device-status/labs/{labId:\\\\d+}\"")),
                () -> assertTrue(controller.contains("@GetMapping(\"/device-status/devices/{deviceId:\\\\d+}\"")),
                () -> assertTrue(controller.contains("@GetMapping(\"/device-status/events\"")),
                () -> assertTrue(controller.contains("@PostMapping(\"/device-status/report\"")),
                () -> assertTrue(controller.contains("@GetMapping(\"/device-status/summary\"")),
                () -> assertTrue(service.contains("device_runtime_status")),
                () -> assertTrue(service.contains("device_status_event")),
                () -> assertFalse(service.contains("from device d"))
        );
    }

    @Test
    void iotApisUseIotTablesInsteadOfAssetDevicesForLabDevices() throws IOException {
        String controller = readSource("controller", "IotHardwareController.java");
        String service = readSource("service", "impl", "IotDeviceServiceImpl.java");
        String hardwareService = readSource("service", "impl", "IotHardwareServiceImpl.java");
        String hardwareInterface = readSource("service", "IotHardwareService.java");

        assertAll(
                () -> assertTrue(controller.contains("@GetMapping(\"/iot/devices\")")),
                () -> assertTrue(controller.contains("@GetMapping(\"/iot/labs/{labId:\\\\d+}/overview\")")),
                () -> assertTrue(controller.contains("@PostMapping(\"/iot/devices\")")),
                () -> assertTrue(controller.contains("@PutMapping(\"/iot/devices/{id:\\\\d+}\")")),
                () -> assertTrue(controller.contains("@DeleteMapping(\"/iot/devices/{id:\\\\d+}\")")),
                () -> assertTrue(controller.contains("@PostMapping(\"/iot/control\")")),
                () -> assertTrue(controller.contains("@GetMapping(\"/iot/command-logs\")")),
                () -> assertTrue(controller.contains("@GetMapping(\"/iot/environment/{labId:\\\\d+}\"")),
                () -> assertTrue(controller.contains("iotDeviceService.listLabDevices(labId)")),
                () -> assertTrue(service.contains("iot_device")),
                () -> assertTrue(service.contains("iot_command_log")),
                () -> assertFalse(service.contains("DevicesMapper")),
                () -> assertFalse(service.contains("getDevices()")),
                () -> assertFalse(hardwareService.contains("DevicesMapper")),
                () -> assertFalse(hardwareService.contains("devicesMapper.getDevices()")),
                () -> assertFalse(hardwareInterface.contains("getLabDevices("))
        );
    }

    @Test
    void globalExceptionFallbackIncludesRequestIdInsteadOfGenericRetryCopy() throws IOException {
        String handler = readSource("config", "GlobalExceptionHandler.java");

        assertAll(
                () -> assertFalse(handler.contains("服务器异常，请稍后重试")),
                () -> assertTrue(handler.contains("系统处理失败，请联系管理员")),
                () -> assertTrue(handler.contains("请求编号"))
        );
    }

    @Test
    void aiAssistantCustomApiHasManualConnectionTestEndpointAndBusinessErrors() throws IOException {
        String controller = readSource("controller", "AiAssistantConfigController.java");
        String contract = readSource("service", "AiAssistantUserConfigService.java");
        String service = readSource("service", "impl", "AiAssistantUserConfigServiceImpl.java");

        assertAll(
                () -> assertTrue(controller.contains("@PostMapping(\"/ai-assistant/user-config/test\")")),
                () -> assertTrue(contract.contains("Map<String, Object> testConnection(Integer userId);")),
                () -> assertTrue(service.contains("public Map<String, Object> testConnection(Integer userId)")),
                () -> assertTrue(service.contains("RestClientException")),
                () -> assertTrue(service.contains("第三方 AI 接口连接失败")),
                () -> assertFalse(service.contains("throw new IllegalStateException(\"AI 接口未返回可展示内容\")"))
        );
    }

    @Test
    void labAdminCanAccessSplitDeviceStatusAndIotApis() {
        Object policy = newPolicy();

        assertAll(
                () -> assertTrue(isAllowed(policy, "GET", "/device-status/labs/2", "labAdmin")),
                () -> assertTrue(isAllowed(policy, "POST", "/device-status/report", "labAdmin")),
                () -> assertTrue(isAllowed(policy, "GET", "/iot/labs/2/devices", "labAdmin")),
                () -> assertTrue(isAllowed(policy, "POST", "/iot/control", "labAdmin")),
                () -> assertFalse(isAllowed(policy, "GET", "/device-status/labs/2", "teacher")),
                () -> assertFalse(isAllowed(policy, "POST", "/iot/control", "teacher"))
        );
    }

    @Test
    void deviceInventoryOnlyUpdatesInventoryDateAndKeepsRuntimeStateOutOfAssets() throws IOException {
        String service = readSource("service", "impl", "DevicesServiceImpl.java");
        String mapper = readSource("mapper", "DevicesMapper.java");
        String mapperXml = readResource("mapper", "DevicesMapper.xml");
        String inventoryUpdateSql = between(mapperXml, "<update id=\"updateInventoryState\">", "</update>");

        assertAll(
                () -> assertTrue(service.contains("updateInventoryState(deviceId, inventoryDate)")),
                () -> assertFalse(service.contains("record.getResultStatus(), device.getHealth(), device.getOnline()")),
                () -> assertTrue(mapper.contains("@Param(\"inventoryDate\") String inventoryDate")),
                () -> assertFalse(mapper.contains("@Param(\"status\") String status,\n            @Param(\"health\") String health,\n            @Param(\"online\") Boolean online,\n            @Param(\"inventoryDate\") String inventoryDate")),
                () -> assertFalse(inventoryUpdateSql.contains("status =")),
                () -> assertFalse(inventoryUpdateSql.contains("health =")),
                () -> assertFalse(inventoryUpdateSql.contains("online =")),
                () -> assertTrue(inventoryUpdateSql.contains("inventory_date = #{inventoryDate}"))
        );
    }

    @Test
    void repairSyncUpdatesOnlyAssetMaintenanceStateAndNeverRuntimeHeartbeatFields() throws IOException {
        String service = readSource("service", "impl", "BusinessLoopServiceImpl.java");
        String mapper = readSource("mapper", "DevicesMapper.java");
        String mapperXml = readResource("mapper", "DevicesMapper.xml");
        String repairUpdateSql = mapperXml.contains("<update id=\"updateDeviceAssetMaintenanceStateByNameOrCode\">")
                ? between(mapperXml, "<update id=\"updateDeviceAssetMaintenanceStateByNameOrCode\">", "</update>")
                : between(mapperXml, "<update id=\"updateDeviceRuntimeStateByNameOrCode\">", "</update>");

        assertAll(
                () -> assertTrue(service.contains("updateDeviceAssetMaintenanceStateByNameOrCode(")),
                () -> assertFalse(service.contains("updateDeviceRuntimeStateByNameOrCode(")),
                () -> assertFalse(service.contains("boolean online")),
                () -> assertFalse(service.contains("\"health\", health")),
                () -> assertTrue(mapper.contains("int updateDeviceAssetMaintenanceStateByNameOrCode(")),
                () -> assertFalse(mapper.contains("int updateDeviceRuntimeStateByNameOrCode(")),
                () -> assertTrue(mapperXml.contains("<update id=\"updateDeviceAssetMaintenanceStateByNameOrCode\">")),
                () -> assertFalse(repairUpdateSql.contains("health =")),
                () -> assertFalse(repairUpdateSql.contains("online ="))
        );
    }

    @Test
    void deviceAssetStatsIncludeIdleLifecycleStatus() throws IOException {
        String service = readSource("service", "impl", "DevicesServiceImpl.java");
        String mapperXml = readResource("mapper", "DevicesMapper.xml");
        String statsSql = between(mapperXml, "<select id=\"getDeviceStats\" resultType=\"map\">", "</select>");

        assertAll(
                () -> assertTrue(service.contains("normalized.put(\"idle\"")),
                () -> assertTrue(statsSql.contains(" as idle")),
                () -> assertTrue(statsSql.contains("status = '闲置'"))
        );
    }

    @Test
    void dashboardShortcutKeepsAssetAndRuntimeConceptsSeparated() throws IOException {
        String service = readSource("service", "impl", "DashboardServiceImpl.java");

        assertAll(
                () -> assertFalse(service.contains("资产台账与设备状态")),
                () -> assertTrue(service.contains("资产台账与生命周期管理"))
        );
    }

    @Test
    void runtimeReportCanResolveTerminalByIdOrToken() throws IOException {
        String service = readSource("service", "impl", "DeviceStatusServiceImpl.java");

        assertAll(
                () -> assertTrue(service.contains("stringValue(request.get(\"terminalToken\"))")),
                () -> assertTrue(service.contains("terminal_token_hash = ?")),
                () -> assertTrue(service.contains("hashTerminalToken(")),
                () -> assertTrue(service.contains("MessageDigest.getInstance(\"SHA-256\")")),
                () -> assertTrue(service.contains("StandardCharsets.UTF_8"))
        );
    }

    @Test
    void runtimeReportMaintainsTerminalFirstAndLastSeenTime() throws IOException {
        String service = readSource("service", "impl", "DeviceStatusServiceImpl.java");

        assertAll(
                () -> assertTrue(service.contains("first_connected_at = coalesce(first_connected_at, ?)")),
                () -> assertTrue(service.contains("last_seen_at = ?"))
        );
    }

    @Test
    void activationCodeBindingPersistsLabCodeAndTerminalCredentialsToSplitTables() throws IOException {
        String controller = readSource("controller", "ActivationCodeController.java");

        assertAll(
                () -> assertTrue(controller.contains("JdbcTemplate jdbcTemplate")),
                () -> assertTrue(controller.contains("upsertLabActivationCode(")),
                () -> assertTrue(controller.contains("upsertLabTerminalBinding(")),
                () -> assertTrue(controller.contains("insert into lab_activation_code")),
                () -> assertTrue(controller.contains("insert into lab_terminal")),
                () -> assertTrue(controller.contains("terminal_token_hash")),
                () -> assertTrue(controller.contains("hashTerminalToken(")),
                () -> assertTrue(controller.contains("result.put(\"terminalId\"")),
                () -> assertTrue(controller.contains("result.put(\"terminalToken\""))
        );
    }

    private String readSource(String... parts) throws IOException {
        Path path = Path.of("src", "main", "java", "org", "example", "backend");
        for (String part : parts) {
            path = path.resolve(part);
        }
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path);
    }

    private String readResource(String... parts) throws IOException {
        Path path = Path.of("src", "main", "resources");
        for (String part : parts) {
            path = path.resolve(part);
        }
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path);
    }

    private String between(String value, String startMarker, String endMarker) {
        int start = value.indexOf(startMarker);
        assertTrue(start >= 0, startMarker + " should exist");
        int end = value.indexOf(endMarker, start);
        assertTrue(end > start, endMarker + " should exist after " + startMarker);
        return value.substring(start, end + endMarker.length());
    }

    private Object newPolicy() {
        try {
            return Class.forName("org.example.backend.security.SecurityAccessPolicy")
                    .getConstructor()
                    .newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private boolean isAllowed(Object policy, String method, String path, String roleCode) {
        try {
            return (Boolean) policy.getClass()
                    .getMethod("isAllowed", String.class, String.class, String.class)
                    .invoke(policy, method, path, roleCode);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
