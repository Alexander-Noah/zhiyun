package org.example.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.entity.DeviceInventoryRecordEntity;
import org.example.backend.entity.DeviceTransferRecordEntity;
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
        return Result.success("获取设备列表成功", devicesService.getDevices());
    }

    @PostMapping("/devices")
    public Result InserterDevices(@RequestBody DevicesEntity devices) {
        return Result.success("新增设备成功", devicesService.InserterDevices(devices));
    }

    @GetMapping("/devices/{id:\\d+}")
    public Result getDevicesById(@PathVariable Long id) {
        return Result.success("获取设备详情成功", devicesService.getDevicesById(id));
    }

    @GetMapping("/device-inventory-records")
    public Result listInventoryRecords(@RequestParam(required = false) Long deviceId) {
        return Result.success("获取设备盘点记录成功", devicesService.listInventoryRecords(deviceId));
    }

    @GetMapping("/devices/{id:\\d+}/inventory-records")
    public Result listDeviceInventoryRecords(@PathVariable Long id) {
        return Result.success("获取设备盘点记录成功", devicesService.listInventoryRecords(id));
    }

    @PostMapping("/devices/{id:\\d+}/inventory")
    public Result recordDeviceInventory(@PathVariable Long id, @RequestBody DeviceInventoryRecordEntity record) {
        return Result.success("保存设备盘点记录成功", devicesService.recordDeviceInventory(id, record));
    }

    @GetMapping("/device-transfer-records")
    public Result listTransferRecords(@RequestParam(required = false) Long deviceId) {
        return Result.success("获取设备调拨记录成功", devicesService.listTransferRecords(deviceId));
    }

    @GetMapping("/devices/{id:\\d+}/transfer-records")
    public Result listDeviceTransferRecords(@PathVariable Long id) {
        return Result.success("获取设备调拨记录成功", devicesService.listTransferRecords(id));
    }

    @PostMapping("/devices/{id:\\d+}/transfer")
    public Result transferDevice(@PathVariable Long id, @RequestBody DeviceTransferRecordEntity record) {
        return Result.success("保存设备调拨成功", devicesService.transferDevice(id, record));
    }

    @PutMapping("/devices/{id:\\d+}")
    public Result updateDevices(@PathVariable Long id, @RequestBody DevicesEntity devices) {
        return Result.success("更新设备成功", devicesService.updateDevices(id, devices));
    }

    @DeleteMapping("/devices/{id:\\d+}")
    public Result DeleteDevices(@PathVariable Long id) {
        devicesService.deleteDevices(id);
        return Result.success("删除设备成功");
    }

    @PutMapping("/devices/batch")
    public Result updateDevices(@RequestBody DeviceBatchRequest request) {
        List<DevicesEntity> devices = request == null || request.getRecords() == null
                ? Collections.emptyList()
                : request.getRecords();
        return Result.success("批量更新设备成功", devicesService.updateDevices(devices));
    }

    @PostMapping("/devices/reset")
    public Result resetDevices() {
        return Result.success("重置设备数据成功", devicesService.getDevices());
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
