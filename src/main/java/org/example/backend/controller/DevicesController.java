package org.example.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.result.Result;
import org.example.backend.entity.DevicesEntity;
import org.example.backend.service.DevicesService;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@CrossOrigin
@RestController
@Slf4j
public class DevicesController {
    private final DevicesService devicesService;

    public DevicesController(DevicesService devicesService) {
        this.devicesService = devicesService;
    }

    @GetMapping("/devices")
    public Result getDevices() {
        return Result.success("list devices success", devicesService.getDevices());
    }

    @PostMapping("/devices")
    public Result InserterDevices(@RequestBody DevicesEntity devices) {
        return Result.success("create device success", devicesService.InserterDevices(devices));
    }

    @GetMapping("/devices/{id:\\d+}")
    public Result getDevicesById(@PathVariable Long id) {
        return Result.success("get device success", devicesService.getDevicesById(id));
    }

    @PutMapping("/devices/{id:\\d+}")
    public Result updateDevices(@PathVariable Long id, @RequestBody DevicesEntity devices) {
        return Result.success("update device success", devicesService.updateDevices(id, devices));
    }

    @DeleteMapping("/devices/{id:\\d+}")
    public Result DeleteDevices(@PathVariable Long id) {
        devicesService.deleteDevices(id);
        return Result.success("delete device success");
    }

    @PutMapping("/devices/batch")
    public Result updateDevices(@RequestBody DeviceBatchRequest request) {
        List<DevicesEntity> devices = request == null || request.getRecords() == null
                ? Collections.emptyList()
                : request.getRecords();
        return Result.success("batch update device success", devicesService.updateDevices(devices));
    }

    @PostMapping("/devices/reset")
    public Result resetDevices() {
        return Result.success("reset devices success", devicesService.getDevices());
    }

    public static class DeviceBatchRequest {
        private String resource;
        private List<DevicesEntity> records;

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        public List<DevicesEntity> getRecords() {
            return records;
        }

        public void setRecords(List<DevicesEntity> records) {
            this.records = records;
        }
    }
}
