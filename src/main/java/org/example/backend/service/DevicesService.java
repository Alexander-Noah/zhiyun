package org.example.backend.service;

import org.example.backend.entity.DevicesEntity;
import org.example.backend.entity.DeviceInventoryRecordEntity;
import org.example.backend.entity.DeviceTransferRecordEntity;

import java.util.List;

public interface DevicesService {
    List<DevicesEntity> getDevices();

    DevicesEntity InserterDevices(DevicesEntity devicesEntity);

    DevicesEntity getDevicesById(Long id);

    DevicesEntity updateDevices(Long id, DevicesEntity devices);

    List<DevicesEntity> updateDevices(List<DevicesEntity> devices);

    void deleteDevices(Long id);

    List<DevicesEntity> getDevicesByLabId(Integer labId);

    List<DeviceInventoryRecordEntity> listInventoryRecords(Long deviceId);

    DeviceInventoryRecordResult recordDeviceInventory(Long deviceId, DeviceInventoryRecordEntity record);

    List<DeviceTransferRecordEntity> listTransferRecords(Long deviceId);

    DeviceTransferRecordResult transferDevice(Long deviceId, DeviceTransferRecordEntity record);

    class DeviceInventoryRecordResult {
        private DevicesEntity device;
        private DeviceInventoryRecordEntity record;
        private List<DeviceInventoryRecordEntity> records;

        public DeviceInventoryRecordResult(DevicesEntity device, DeviceInventoryRecordEntity record, List<DeviceInventoryRecordEntity> records) {
            this.device = device;
            this.record = record;
            this.records = records;
        }

        public DevicesEntity getDevice() {
            return device;
        }

        public void setDevice(DevicesEntity device) {
            this.device = device;
        }

        public DeviceInventoryRecordEntity getRecord() {
            return record;
        }

        public void setRecord(DeviceInventoryRecordEntity record) {
            this.record = record;
        }

        public List<DeviceInventoryRecordEntity> getRecords() {
            return records;
        }

        public void setRecords(List<DeviceInventoryRecordEntity> records) {
            this.records = records;
        }
    }

    class DeviceTransferRecordResult {
        private DevicesEntity device;
        private DeviceTransferRecordEntity record;
        private List<DeviceTransferRecordEntity> records;

        public DeviceTransferRecordResult(DevicesEntity device, DeviceTransferRecordEntity record, List<DeviceTransferRecordEntity> records) {
            this.device = device;
            this.record = record;
            this.records = records;
        }

        public DevicesEntity getDevice() {
            return device;
        }

        public void setDevice(DevicesEntity device) {
            this.device = device;
        }

        public DeviceTransferRecordEntity getRecord() {
            return record;
        }

        public void setRecord(DeviceTransferRecordEntity record) {
            this.record = record;
        }

        public List<DeviceTransferRecordEntity> getRecords() {
            return records;
        }

        public void setRecords(List<DeviceTransferRecordEntity> records) {
            this.records = records;
        }
    }
}
