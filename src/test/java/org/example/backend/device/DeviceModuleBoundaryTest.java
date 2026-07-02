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
                () -> assertFalse(service.contains("getDevices()"))
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

    private String readSource(String... parts) throws IOException {
        Path path = Path.of("src", "main", "java", "org", "example", "backend");
        for (String part : parts) {
            path = path.resolve(part);
        }
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path);
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
