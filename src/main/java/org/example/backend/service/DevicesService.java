package org.example.backend.service;

import org.example.backend.entity.DevicesEntity;
import org.example.backend.entity.DeviceInventoryRecordEntity;
import org.example.backend.entity.DeviceTransferRecordEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface DevicesService {
    List<DevicesEntity> getDevices();

    DevicePageResult pageDevices(DevicePageQuery query);

    Map<String, Object> getDeviceStats();

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

    DeviceImportResult importDevices(MultipartFile file);

    byte[] exportDevices(DevicePageQuery query);

    class DevicePageQuery {
        private int pageNum = 1;
        private int pageSize = 10;
        private String keyword;
        private Long labId;
        private String labName;
        private String category;
        private String status;

        public int getPageNum() {
            return pageNum;
        }

        public void setPageNum(int pageNum) {
            this.pageNum = pageNum;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }

        public Long getLabId() {
            return labId;
        }

        public void setLabId(Long labId) {
            this.labId = labId;
        }

        public String getLabName() {
            return labName;
        }

        public void setLabName(String labName) {
            this.labName = labName;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    class DevicePageResult {
        private List<DevicesEntity> records;
        private long total;

        public DevicePageResult(List<DevicesEntity> records, long total) {
            this.records = records;
            this.total = total;
        }

        public List<DevicesEntity> getRecords() {
            return records;
        }

        public void setRecords(List<DevicesEntity> records) {
            this.records = records;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }
    }

    class DeviceImportResult {
        private boolean success;
        private String message;
        private int count;

        public DeviceImportResult(boolean success, String message, int count) {
            this.success = success;
            this.message = message;
            this.count = count;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

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
