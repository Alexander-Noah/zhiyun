package org.example.backend.service;

import java.util.Map;

public interface IotDeviceService {
    Map<String, Object> listDevices(Map<String, String> query);

    Map<String, Object> listLabDevices(Long labId);

    Map<String, Object> getLabOverview(Long labId);

    Map<String, Object> createDevice(Map<String, Object> payload);

    Map<String, Object> updateDevice(Long id, Map<String, Object> payload);

    void deleteDevice(Long id);

    Map<String, Object> control(Map<String, Object> payload);

    Map<String, Object> listCommandLogs(Map<String, String> query);

    Map<String, Object> getEnvironment(Long labId);
}
