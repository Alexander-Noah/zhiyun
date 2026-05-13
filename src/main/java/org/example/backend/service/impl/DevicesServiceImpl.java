package org.example.backend.service.impl;

import org.example.backend.entity.DevicesEntity;
import org.example.backend.entity.DeviceInventoryRecordEntity;
import org.example.backend.entity.DeviceTransferRecordEntity;
import org.example.backend.entity.LabEntity;
import org.example.backend.entity.UserEntity;
import org.example.backend.mapper.DeviceInventoryRecordMapper;
import org.example.backend.mapper.DeviceTransferRecordMapper;
import org.example.backend.mapper.DevicesMapper;
import org.example.backend.mapper.LabMapper;
import org.example.backend.mapper.UserMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.DevicesService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DevicesServiceImpl implements DevicesService {
    private static final String STATUS_NORMAL = "\u6b63\u5e38";
    private static final String STATUS_FAULT = "\u6545\u969c";
    private static final String STATUS_DELETED = "\u5df2\u5220\u9664";
    private static final String HEALTH_GOOD = "\u826f\u597d";
    private static final String HEALTH_RISK = "\u98ce\u9669";
    private static final String HEALTH_WARNING = "\u5173\u6ce8";
    private static final String STATUS_INVENTORIED = "\u5df2\u76d8\u70b9";
    private static final String STATUS_INVENTORY_EXCEPTION = "\u76d8\u70b9\u5f02\u5e38";
    private static final String STATUS_DEVICE_MISSING = "\u8bbe\u5907\u7f3a\u5931";
    private static final String STATUS_DEVICE_DAMAGED = "\u8bbe\u5907\u635f\u574f";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DevicesMapper devicesMapper;
    private final DeviceInventoryRecordMapper deviceInventoryRecordMapper;
    private final DeviceTransferRecordMapper deviceTransferRecordMapper;
    private final LabMapper labMapper;
    private final UserMapper userMapper;
    private final BusinessLoopService businessLoopService;

    public DevicesServiceImpl(
            DevicesMapper devicesMapper,
            DeviceInventoryRecordMapper deviceInventoryRecordMapper,
            DeviceTransferRecordMapper deviceTransferRecordMapper,
            LabMapper labMapper,
            UserMapper userMapper,
            BusinessLoopService businessLoopService
    ) {
        this.devicesMapper = devicesMapper;
        this.deviceInventoryRecordMapper = deviceInventoryRecordMapper;
        this.deviceTransferRecordMapper = deviceTransferRecordMapper;
        this.labMapper = labMapper;
        this.userMapper = userMapper;
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

    @Override
    public List<DeviceInventoryRecordEntity> listInventoryRecords(Long deviceId) {
        ensureInventoryRecordTable();
        List<DeviceInventoryRecordEntity> records = deviceInventoryRecordMapper.listRecords(deviceId);
        return records == null ? List.of() : records;
    }

    @Override
    public DeviceInventoryRecordResult recordDeviceInventory(Long deviceId, DeviceInventoryRecordEntity record) {
        if (deviceId == null) {
            throw new IllegalArgumentException("device id is required");
        }

        DevicesEntity device = devicesMapper.getDevicesById(deviceId);
        if (device == null) {
            throw new IllegalArgumentException("device not found");
        }

        ensureInventoryRecordTable();
        normalizeInventoryRecord(record, device);
        deviceInventoryRecordMapper.insertRecord(record);

        String nextStatus = record.getResultStatus();
        String nextHealth = resolveInventoryHealth(record, device.getHealth());
        Boolean nextOnline = resolveInventoryOnline(record, device.getOnline());
        String inventoryDate = extractInventoryDate(record.getInspectedAt());
        devicesMapper.updateInventoryState(deviceId, nextStatus, nextHealth, nextOnline, inventoryDate);

        DevicesEntity savedDevice = devicesMapper.getDevicesById(deviceId);
        List<DeviceInventoryRecordEntity> records = deviceInventoryRecordMapper.listRecordsByDeviceId(deviceId);
        businessLoopService.recordEvent("device", "inventory", textOrDefault(device.getDeviceName(), String.valueOf(deviceId)), nextStatus, Map.of(
                "deviceId", deviceId,
                "inspector", textOrEmpty(record.getInspectorName()),
                "normal", Boolean.TRUE.equals(record.getNormal()),
                "missing", Boolean.TRUE.equals(record.getMissing()),
                "damaged", Boolean.TRUE.equals(record.getDamaged())
        ));

        return new DeviceInventoryRecordResult(savedDevice, record, records == null ? List.of() : records);
    }

    @Override
    public List<DeviceTransferRecordEntity> listTransferRecords(Long deviceId) {
        ensureTransferRecordTable();
        List<DeviceTransferRecordEntity> records = deviceTransferRecordMapper.listRecords(deviceId);
        return records == null ? List.of() : records;
    }

    @Override
    @Transactional
    public DeviceTransferRecordResult transferDevice(Long deviceId, DeviceTransferRecordEntity record) {
        if (deviceId == null) {
            throw new IllegalArgumentException("device id is required");
        }

        DevicesEntity device = devicesMapper.getDevicesById(deviceId);
        if (device == null) {
            throw new IllegalArgumentException("device not found");
        }

        ensureTransferRecordTable();
        DeviceTransferRecordEntity transferRecord = normalizeTransferRecord(record, device);
        int updatedCount = devicesMapper.updateTransferState(
                deviceId,
                transferRecord.getToLabId(),
                transferRecord.getToOwnerUserId(),
                transferRecord.getToLocation()
        );
        if (updatedCount == 0) {
            throw new IllegalArgumentException("device not found");
        }

        deviceTransferRecordMapper.insertRecord(transferRecord);

        DevicesEntity savedDevice = devicesMapper.getDevicesById(deviceId);
        List<DeviceTransferRecordEntity> records = deviceTransferRecordMapper.listRecordsByDeviceId(deviceId);
        businessLoopService.recordEvent("device", "transfer", textOrDefault(device.getDeviceName(), String.valueOf(deviceId)), transferRecord.getTransferType(), Map.of(
                "deviceId", deviceId,
                "fromLab", textOrEmpty(transferRecord.getFromLabName()),
                "toLab", textOrEmpty(transferRecord.getToLabName()),
                "fromOwner", textOrEmpty(transferRecord.getFromOwnerName()),
                "toOwner", textOrEmpty(transferRecord.getToOwnerName()),
                "fromLocation", textOrEmpty(transferRecord.getFromLocation()),
                "toLocation", textOrEmpty(transferRecord.getToLocation())
        ));

        return new DeviceTransferRecordResult(savedDevice, transferRecord, records == null ? List.of() : records);
    }

    private void ensureInventoryRecordTable() {
        deviceInventoryRecordMapper.createTableIfNotExists();
    }

    private void ensureTransferRecordTable() {
        deviceTransferRecordMapper.createTableIfNotExists();
    }

    private DeviceTransferRecordEntity normalizeTransferRecord(DeviceTransferRecordEntity record, DevicesEntity device) {
        if (record == null) {
            throw new IllegalArgumentException("transfer record is required");
        }

        Long nextLabId = record.getToLabId() == null ? device.getLabId() : record.getToLabId();
        Long nextOwnerUserId = record.getToOwnerUserId() == null ? device.getOwnerUserId() : record.getToOwnerUserId();
        String nextLocation = textOrDefault(record.getToLocation(), device.getLocation());
        String nextLabName = resolveLabName(nextLabId, record.getToLabName(), device.getLabId(), device.getLabName());
        String nextOwnerName = resolveUserName(nextOwnerUserId, record.getToOwnerName(), device.getOwnerUserId(), device.getOwnerUsername());

        boolean labChanged = !Objects.equals(device.getLabId(), nextLabId);
        boolean ownerChanged = !Objects.equals(device.getOwnerUserId(), nextOwnerUserId);
        boolean locationChanged = !Objects.equals(textOrEmpty(device.getLocation()), textOrEmpty(nextLocation));
        if (!labChanged && !ownerChanged && !locationChanged) {
            throw new IllegalArgumentException("transfer content has not changed");
        }

        record.setDeviceId(device.getId());
        record.setDeviceCode(textOrDefault(record.getDeviceCode(), device.getDeviceCode()));
        record.setDeviceName(textOrDefault(record.getDeviceName(), device.getDeviceName()));
        record.setFromLabId(device.getLabId());
        record.setFromLabName(textOrEmpty(device.getLabName()));
        record.setToLabId(nextLabId);
        record.setToLabName(textOrEmpty(nextLabName));
        record.setFromOwnerUserId(device.getOwnerUserId());
        record.setFromOwnerName(textOrEmpty(device.getOwnerUsername()));
        record.setToOwnerUserId(nextOwnerUserId);
        record.setToOwnerName(textOrEmpty(nextOwnerName));
        record.setFromLocation(textOrEmpty(device.getLocation()));
        record.setToLocation(textOrEmpty(nextLocation));
        record.setTransferType(resolveTransferType(labChanged, ownerChanged, locationChanged));
        record.setReason(textOrEmpty(record.getReason()));
        record.setOperatorName(textOrDefault(record.getOperatorName(), "system"));
        record.setTransferAt(textOrDefault(record.getTransferAt(), LocalDateTime.now().format(DATE_TIME_FORMATTER)));
        return record;
    }

    private String resolveTransferType(boolean labChanged, boolean ownerChanged, boolean locationChanged) {
        int changedCount = (labChanged ? 1 : 0) + (ownerChanged ? 1 : 0) + (locationChanged ? 1 : 0);
        if (changedCount > 1) {
            return "综合调拨";
        }
        if (labChanged) {
            return "设备调拨";
        }
        if (ownerChanged) {
            return "责任人变更";
        }
        return "安装位置变更";
    }

    private String resolveLabName(Long labId, String requestedName, Long currentLabId, String currentLabName) {
        if (labId == null) {
            return textOrEmpty(requestedName);
        }
        if (Objects.equals(labId, currentLabId) && (requestedName == null || requestedName.isBlank())) {
            return textOrEmpty(currentLabName);
        }
        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName;
        }
        LabEntity lab = labMapper.getLabById(labId.intValue());
        return lab == null ? "" : textOrEmpty(lab.getLabName());
    }

    private String resolveUserName(Long userId, String requestedName, Long currentUserId, String currentUserName) {
        if (userId == null) {
            return textOrEmpty(requestedName);
        }
        if (Objects.equals(userId, currentUserId) && (requestedName == null || requestedName.isBlank())) {
            return textOrEmpty(currentUserName);
        }
        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName;
        }
        UserEntity user = userMapper.getUser(userId.intValue());
        if (user == null) {
            return "";
        }
        return textOrDefault(user.getRealName(), user.getUsername());
    }

    private void normalizeInventoryRecord(DeviceInventoryRecordEntity record, DevicesEntity device) {
        if (record == null) {
            throw new IllegalArgumentException("inventory record is required");
        }

        record.setDeviceId(device.getId());
        record.setDeviceCode(textOrDefault(record.getDeviceCode(), device.getDeviceCode()));
        record.setDeviceName(textOrDefault(record.getDeviceName(), device.getDeviceName()));
        record.setInspectorName(textOrDefault(record.getInspectorName(), textOrDefault(device.getOwnerUsername(), "实验室管理员")));
        record.setInspectedAt(textOrDefault(record.getInspectedAt(), LocalDateTime.now().format(DATE_TIME_FORMATTER)));
        record.setMissing(Boolean.TRUE.equals(record.getMissing()));
        record.setDamaged(Boolean.TRUE.equals(record.getDamaged()));

        String resultStatus = textOrEmpty(record.getResultStatus());
        boolean isMissing = Boolean.TRUE.equals(record.getMissing()) || STATUS_DEVICE_MISSING.equals(resultStatus);
        boolean isDamaged = Boolean.TRUE.equals(record.getDamaged()) || STATUS_DEVICE_DAMAGED.equals(resultStatus);
        boolean isNormal = Boolean.TRUE.equals(record.getNormal())
                && !isMissing
                && !isDamaged
                && !STATUS_INVENTORY_EXCEPTION.equals(resultStatus);

        if (isMissing) {
            resultStatus = STATUS_DEVICE_MISSING;
            isNormal = false;
        } else if (isDamaged) {
            resultStatus = STATUS_DEVICE_DAMAGED;
            isNormal = false;
        } else if (resultStatus.isBlank()) {
            resultStatus = isNormal ? STATUS_INVENTORIED : STATUS_INVENTORY_EXCEPTION;
        } else if (STATUS_INVENTORIED.equals(resultStatus) && !isNormal) {
            resultStatus = STATUS_INVENTORY_EXCEPTION;
        }

        record.setNormal(isNormal);
        record.setMissing(isMissing);
        record.setDamaged(isDamaged);
        record.setResultStatus(resultStatus);
        record.setRemark(textOrEmpty(record.getRemark()));
    }

    private String resolveInventoryHealth(DeviceInventoryRecordEntity record, String previousHealth) {
        if (Boolean.TRUE.equals(record.getMissing()) || Boolean.TRUE.equals(record.getDamaged())) {
            return HEALTH_RISK;
        }
        if (!Boolean.TRUE.equals(record.getNormal())) {
            return HEALTH_WARNING;
        }
        return textOrDefault(previousHealth, HEALTH_GOOD);
    }

    private Boolean resolveInventoryOnline(DeviceInventoryRecordEntity record, Boolean previousOnline) {
        if (Boolean.TRUE.equals(record.getMissing()) || Boolean.TRUE.equals(record.getDamaged())) {
            return false;
        }
        return previousOnline == null || previousOnline;
    }

    private String extractInventoryDate(String inspectedAt) {
        if (inspectedAt != null && inspectedAt.length() >= 10) {
            return inspectedAt.substring(0, 10);
        }
        return LocalDate.now().toString();
    }
}
