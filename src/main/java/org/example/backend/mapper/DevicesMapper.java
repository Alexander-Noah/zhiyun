package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.DevicesEntity;

import java.util.List;
import java.util.Map;

@Mapper
public interface DevicesMapper {
    List<DevicesEntity> getDevices();

    List<DevicesEntity> pageDevices(
            @Param("offset") int offset,
            @Param("pageSize") int pageSize,
            @Param("keyword") String keyword,
            @Param("labId") Long labId,
            @Param("labName") String labName,
            @Param("category") String category,
            @Param("status") String status
    );

    long countDevices(
            @Param("keyword") String keyword,
            @Param("labId") Long labId,
            @Param("labName") String labName,
            @Param("category") String category,
            @Param("status") String status
    );

    Map<String, Object> getDeviceStats();

    void InserterDevices(DevicesEntity devicesEntity);

    DevicesEntity getDevicesById(@Param("id") Long id);

    int updateDevices(@Param("id") Long id, @Param("devices") DevicesEntity devices);

    int updateDeviceAssetMaintenanceStateByNameOrCode(
            @Param("device") String device,
            @Param("status") String status,
            @Param("maintenance") String maintenance
    );

    int updateInventoryState(
            @Param("id") Long id,
            @Param("inventoryDate") String inventoryDate
    );

    int updateTransferState(
            @Param("id") Long id,
            @Param("labId") Long labId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("location") String location
    );

    void deleteDevices(@Param("id") Long id);

    List<DevicesEntity> getDevicesByLabId(@Param("labId") Integer labId);
}
