package org.example.backend.controller;

import org.example.backend.result.Result;
import org.example.backend.service.IotDeviceService;
import org.example.backend.service.IotHardwareService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@CrossOrigin
@RestController
public class IotHardwareController {
    private final IotHardwareService iotHardwareService;
    private final IotDeviceService iotDeviceService;

    public IotHardwareController(IotHardwareService iotHardwareService, IotDeviceService iotDeviceService) {
        this.iotHardwareService = iotHardwareService;
        this.iotDeviceService = iotDeviceService;
    }

    @GetMapping("/iot/hardware")
    public Result getHardwareOverview() {
        return Result.success("获取物联网硬件概览成功", iotHardwareService.getOverview());
    }

    @GetMapping("/iot/hardware/devices")
    public Result listHardwareDevices() {
        return Result.success("获取物联网设备列表成功", iotHardwareService.listHardwareDevices());
    }

    @GetMapping("/iot/labs/{labId:\\d+}/devices")
    public Result listLabDevices(@PathVariable Long labId) {
        return Result.success("获取实验室物联设备成功", iotDeviceService.listLabDevices(labId));
    }

    @GetMapping("/iot/devices")
    public Result listIotDevices(@RequestParam(required = false) Map<String, String> query) {
        return Result.success("获取物联设备列表成功", iotDeviceService.listDevices(query == null ? Map.of() : query));
    }

    @GetMapping("/iot/labs/{labId:\\d+}/overview")
    public Result getLabIotOverview(@PathVariable Long labId) {
        return Result.success("获取实验室物联概览成功", iotDeviceService.getLabOverview(labId));
    }

    @PostMapping("/iot/devices")
    public Result createIotDevice(@RequestBody(required = false) Map<String, Object> payload) {
        return Result.success("新增物联设备成功", iotDeviceService.createDevice(payload == null ? Map.of() : payload));
    }

    @PutMapping("/iot/devices/{id:\\d+}")
    public Result updateIotDevice(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> payload) {
        return Result.success("更新物联设备成功", iotDeviceService.updateDevice(id, payload == null ? Map.of() : payload));
    }

    @DeleteMapping("/iot/devices/{id:\\d+}")
    public Result deleteIotDevice(@PathVariable Long id) {
        iotDeviceService.deleteDevice(id);
        return Result.success("删除物联设备成功");
    }

    @PostMapping("/iot/control")
    public Result controlIotDevice(@RequestBody(required = false) Map<String, Object> payload) {
        return Result.success("物联控制命令已记录", iotDeviceService.control(payload == null ? Map.of() : payload));
    }

    @GetMapping("/iot/command-logs")
    public Result listCommandLogs(@RequestParam(required = false) Map<String, String> query) {
        return Result.success("获取物联控制日志成功", iotDeviceService.listCommandLogs(query == null ? Map.of() : query));
    }

    @GetMapping("/iot/environment/{labId:\\d+}")
    public Result getEnvironment(@PathVariable Long labId) {
        return Result.success("获取实验室环境感知数据成功", iotDeviceService.getEnvironment(labId));
    }

    @GetMapping("/iot/labs/{labId:\\d+}/status")
    public Result getLabHardwareStatus(@PathVariable Long labId) {
        return Result.success("获取实验室物联网状态成功", iotHardwareService.getLabStatus(labId));
    }

    @PostMapping("/iot/labs/{labId:\\d+}/access")
    public Result executeAccessCommand(@PathVariable Long labId, @RequestBody(required = false) HardwareActionRequest request) {
        String action = request == null || request.getAction() == null ? "open" : request.getAction();
        Map<String, Object> payload = request == null || request.getPayload() == null ? Collections.emptyMap() : request.getPayload();
        return Result.success("执行门禁指令成功", iotHardwareService.executeAccessCommand(labId, action, payload));
    }

    @GetMapping("/iot/labs/{labId:\\d+}/camera")
    public Result getLabCamera(@PathVariable Long labId) {
        return Result.success("获取实验室摄像头成功", iotHardwareService.getLabCamera(labId));
    }

    @GetMapping("/iot/devices/{code}/status")
    public Result getDeviceStatus(@PathVariable String code) {
        return Result.success("获取物联网设备状态成功", iotHardwareService.getDeviceStatus(code));
    }

    @PostMapping("/iot/devices/{code}/commands/{action}")
    public Result executeDeviceCommand(
            @PathVariable String code,
            @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        return Result.success("执行物联网设备指令成功", iotHardwareService.executeDeviceCommand(
                code,
                action,
                payload == null ? Collections.emptyMap() : payload
        ));
    }

    @GetMapping("/iot/cameras/{code}/snapshot")
    public ResponseEntity<byte[]> getCameraSnapshot(@PathVariable String code) {
        IotHardwareService.CameraSnapshot snapshot = iotHardwareService.getCameraSnapshot(code);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_TYPE, snapshot.contentType() == null ? MediaType.IMAGE_JPEG_VALUE : snapshot.contentType())
                .body(snapshot.bytes());
    }

    public static class HardwareActionRequest {
        private String action;
        private Map<String, Object> payload;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public Map<String, Object> getPayload() {
            return payload;
        }

        public void setPayload(Map<String, Object> payload) {
            this.payload = payload;
        }
    }
}
