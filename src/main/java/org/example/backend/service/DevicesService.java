package org.example.backend.service;

import org.example.backend.entity.DevicesEntity;

import java.util.List;

public interface DevicesService {
    List<DevicesEntity> getDevices();

    DevicesEntity InserterDevices(DevicesEntity devicesEntity);

    DevicesEntity getDevicesById(Long id);

    DevicesEntity updateDevices(Long id, DevicesEntity devices);

    List<DevicesEntity> updateDevices(List<DevicesEntity> devices);

    void deleteDevices(Long id);

    List<DevicesEntity> getDevicesByLabId(Integer labId);
}
