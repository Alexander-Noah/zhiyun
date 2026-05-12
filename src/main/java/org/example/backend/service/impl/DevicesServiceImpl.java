package org.example.backend.service.impl;

import org.example.backend.entity.DevicesEntity;
import org.example.backend.mapper.DevicesMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.DevicesService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class DevicesServiceImpl implements DevicesService {
    private static final String STATUS_NORMAL = "\u6b63\u5e38";
    private static final String STATUS_FAULT = "\u6545\u969c";
    private static final String STATUS_DELETED = "\u5df2\u5220\u9664";
    private static final String HEALTH_GOOD = "\u826f\u597d";

    private final DevicesMapper devicesMapper;
    private final BusinessLoopService businessLoopService;

    public DevicesServiceImpl(DevicesMapper devicesMapper, BusinessLoopService businessLoopService) {
        this.devicesMapper = devicesMapper;
        this.businessLoopService = businessLoopService;
    }

    @Override
    public List<DevicesEntity> getDevices() {
        List<DevicesEntity> devices = devicesMapper.getDevices();
        return devices == null ? List.of() : devices;
    }

    @Override
    public DevicesEntity InserterDevices(DevicesEntity devicesEntity) {
        normalizeDevice(devicesEntity);
        devicesMapper.InserterDevices(devicesEntity);
        DevicesEntity savedDevice = devicesEntity.getId() == null ? devicesEntity : devicesMapper.getDevicesById(devicesEntity.getId());
        businessLoopService.recordEvent("device", "create", textOrDefault(savedDevice.getDeviceName(), "device"), savedDevice.getStatus(), Map.of(
                "deviceId", savedDevice.getId() == null ? 0L : savedDevice.getId(),
                "lab", textOrEmpty(savedDevice.getLabName())
        ));
        return savedDevice;
    }

    @Override
    public DevicesEntity getDevicesById(Long id) {
        return devicesMapper.getDevicesById(id);
    }

    @Override
    public DevicesEntity updateDevices(Long id, DevicesEntity devices) {
        DevicesEntity oldDevice = devicesMapper.getDevicesById(id);
        normalizeDevice(devices);
        if("已盘点".equals(devices.getStatus())){
            devices.setInventoryDate(LocalDate.now().toString());
        }
        devices.setUsageHours(oldDevice.getUsageHours());
        int updatedCount = devicesMapper.updateDevices(id, devices);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("device not found");
        }

        DevicesEntity savedDevice = devicesMapper.getDevicesById(id);
        DevicesEntity eventDevice = savedDevice == null ? devices : savedDevice;
        businessLoopService.recordEvent("device", "update", textOrDefault(eventDevice.getDeviceName(), String.valueOf(id)), eventDevice.getStatus(), Map.of(
                "deviceId", id,
                "lab", textOrEmpty(eventDevice.getLabName()),
                "health", textOrEmpty(eventDevice.getHealth()),
                "online", Boolean.TRUE.equals(eventDevice.getOnline())
        ));
        return savedDevice;
    }

    @Override
    public List<DevicesEntity> updateDevices(List<DevicesEntity> devices) {
        if (devices == null || devices.isEmpty()) {
            return getDevices();
        }

        for (DevicesEntity device : devices) {
            if (device.getId() == null) {
                InserterDevices(device);
            } else {
                updateDevices(device.getId(), device);
            }
        }

        return getDevices();
    }

    @Override
    public void deleteDevices(Long id) {
        DevicesEntity device = devicesMapper.getDevicesById(id);
        devicesMapper.deleteDevices(id);
        businessLoopService.recordEvent("device", "delete", device == null ? String.valueOf(id) : device.getDeviceName(), STATUS_DELETED, Map.of(
                "deviceId", id
        ));
    }

    private void normalizeDevice(DevicesEntity device) {
        if (device.getStatus() == null || device.getStatus().isBlank()) {
            device.setStatus(STATUS_NORMAL);
        }
        if (device.getHealth() == null || device.getHealth().isBlank()) {
            device.setHealth(HEALTH_GOOD);
        }
        if (device.getOnline() == null) {
            device.setOnline(!STATUS_FAULT.equals(device.getStatus()));
        }
        if (device.getUsageHours() == null) {
            device.setUsageHours(0);
        }
    }

    private String textOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String textOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    @Override
    public List<DevicesEntity> getDevicesByLabId(Integer labId) {
        if(labId == null){
            return List.of();
        }
        List<DevicesEntity> devices = devicesMapper.getDevicesByLabId(labId);
        if(devices == null){
            return List.of();
        }
        return devices;
    }
}
