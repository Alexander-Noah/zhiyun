package org.example.backend.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.time.LocalDateTime;

public class DevicesEntity {
    private Long id;
    @JsonAlias("code")
    private String deviceCode;
    @JsonAlias("name")
    private String deviceName;
    private String category;
    private Long labId;
    @JsonAlias("lab")
    private String labName;
    private String location;
    private Long ownerUserId;
    @JsonAlias({"owner", "ownerName"})
    private String ownerUsername;
    private String status;
    private String health;
    private Boolean online;
    private String specs;
    private String purchaseDate;
    private String inventoryDate;
    @JsonAlias("warranty")
    private String warrantyDate;
    private Integer usageHours;
    private String maintenance;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DevicesEntity() {
    }

    public DevicesEntity(Long id, String deviceCode, String deviceName, String category, Long labId, String labName, String location, Long ownerUserId, String ownerUsername, String status, String health, Boolean online, String specs, String purchaseDate, String inventoryDate, String warrantyDate, Integer usageHours, String maintenance, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.deviceCode = deviceCode;
        this.deviceName = deviceName;
        this.category = category;
        this.labId = labId;
        this.labName = labName;
        this.location = location;
        this.ownerUserId = ownerUserId;
        this.ownerUsername = ownerUsername;
        this.status = status;
        this.health = health;
        this.online = online;
        this.specs = specs;
        this.purchaseDate = purchaseDate;
        this.inventoryDate = inventoryDate;
        this.warrantyDate = warrantyDate;
        this.usageHours = usageHours;
        this.maintenance = maintenance;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

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

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
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

    @Override
    public String toString() {
        return "DevicesEntity{" +
                "id=" + id +
                ", deviceCode='" + deviceCode + '\'' +
                ", deviceName='" + deviceName + '\'' +
                ", category='" + category + '\'' +
                ", labId=" + labId +
                ", labName='" + labName + '\'' +
                ", location='" + location + '\'' +
                ", ownerUserId=" + ownerUserId +
                ", ownerUsername='" + ownerUsername + '\'' +
                ", status='" + status + '\'' +
                ", health='" + health + '\'' +
                ", online=" + online +
                ", specs='" + specs + '\'' +
                ", purchaseDate='" + purchaseDate + '\'' +
                ", inventoryDate='" + inventoryDate + '\'' +
                ", warrantyDate='" + warrantyDate + '\'' +
                ", usageHours=" + usageHours +
                ", maintenance='" + maintenance + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
