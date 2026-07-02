package org.example.backend.entity;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.LocalDateTime;

public class DevicesEntity {
    private Long id;

    @JsonAlias({"assetNo", "asset_no", "code"})
    private String deviceCode;

    @JsonAlias({"deviceName", "device_name", "name"})
    private String deviceName;

    private String category;
    private Long labId;

    @JsonAlias({"labName", "lab_name", "lab"})
    private String labName;

    @JsonAlias({"roomNo", "room_no", "location"})
    private String location;

    private Long ownerUserId;

    @JsonAlias({"owner", "ownerName"})
    private String ownerUsername;

    private Integer quantity;
    private String unit;
    // Asset lifecycle status only. Runtime online/health data belongs to device_runtime_status.
    private String status;
    // Legacy compatibility field. Keep returning it for old /devices consumers during migration.
    private String health;
    // Legacy compatibility field. Keep returning it for old /devices consumers during migration.
    private Boolean online;

    @JsonAlias({"specification", "specs"})
    private String specs;

    @JsonAlias({"standardRequirement", "standard_requirement"})
    private String standardRequirement;

    private String remark;
    private String sourceType;
    private String purchaseDate;
    private String inventoryDate;
    private String warrantyDate;
    private Integer usageHours;
    private String maintenance;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public void setDeviceCode(String deviceCode) {
        this.deviceCode = deviceCode;
    }

    public String getAssetNo() {
        return deviceCode;
    }

    public void setAssetNo(String assetNo) {
        this.deviceCode = assetNo;
    }

    public String getCode() {
        return deviceCode;
    }

    public void setCode(String code) {
        this.deviceCode = code;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getName() {
        return deviceName;
    }

    public void setName(String name) {
        this.deviceName = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

    public String getLab() {
        return labName;
    }

    public void setLab(String lab) {
        this.labName = lab;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getRoomNo() {
        return location;
    }

    public void setRoomNo(String roomNo) {
        this.location = roomNo;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getHealth() {
        return health;
    }

    public void setHealth(String health) {
        this.health = health;
    }

    public Boolean getOnline() {
        return online;
    }

    public void setOnline(Boolean online) {
        this.online = online;
    }

    public String getSpecs() {
        return specs;
    }

    public void setSpecs(String specs) {
        this.specs = specs;
    }

    public String getSpecification() {
        return specs;
    }

    public void setSpecification(String specification) {
        this.specs = specification;
    }

    public String getStandardRequirement() {
        return standardRequirement;
    }

    public void setStandardRequirement(String standardRequirement) {
        this.standardRequirement = standardRequirement;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(String purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public String getInventoryDate() {
        return inventoryDate;
    }

    public void setInventoryDate(String inventoryDate) {
        this.inventoryDate = inventoryDate;
    }

    public String getWarrantyDate() {
        return warrantyDate;
    }

    public void setWarrantyDate(String warrantyDate) {
        this.warrantyDate = warrantyDate;
    }

    public Integer getUsageHours() {
        return usageHours;
    }

    public void setUsageHours(Integer usageHours) {
        this.usageHours = usageHours;
    }

    public String getMaintenance() {
        return maintenance;
    }

    public void setMaintenance(String maintenance) {
        this.maintenance = maintenance;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
