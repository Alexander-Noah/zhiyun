package org.example.backend.controller;

import org.example.backend.result.Result;
import org.example.backend.service.DeviceStatusService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@CrossOrigin
@RestController
public class DeviceStatusController {
    private final DeviceStatusService deviceStatusService;

    public DeviceStatusController(DeviceStatusService deviceStatusService) {
        this.deviceStatusService = deviceStatusService;
    }

    @GetMapping("/device-status/labs/{labId:\\d+}")
    public Result listLabRuntimeStatus(@PathVariable Long labId, @RequestParam(required = false) Map<String, String> query) {
        return Result.success("获取实验室设备运行状态成功", deviceStatusService.listLabRuntimeStatus(labId, safeQuery(query)));
    }

    @GetMapping("/device-status/devices/{deviceId:\\d+}")
    public Result getDeviceRuntimeStatus(@PathVariable Long deviceId) {
        return Result.success("获取设备运行状态成功", deviceStatusService.getDeviceRuntimeStatus(deviceId));
    }

    @GetMapping("/device-status/events")
    public Result listEvents(@RequestParam(required = false) Map<String, String> query) {
        return Result.success("获取设备状态事件成功", deviceStatusService.listEvents(safeQuery(query)));
    }

    @PostMapping("/device-status/report")
    public Result reportRuntimeStatus(@RequestBody(required = false) Map<String, Object> payload) {
        return Result.success("设备运行状态上报成功", deviceStatusService.reportRuntimeStatus(payload == null ? Map.of() : payload));
    }

    @GetMapping("/device-status/summary")
    public Result getSummary(@RequestParam(required = false) Map<String, String> query) {
        return Result.success("获取设备运行状态汇总成功", deviceStatusService.getSummary(safeQuery(query)));
    }

    private Map<String, String> safeQuery(Map<String, String> query) {
        return query == null ? Map.of() : query;
    }
}
