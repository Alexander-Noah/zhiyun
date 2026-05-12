package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.DevicesEntity;

import java.util.List;

@Mapper
public interface DevicesMapper {
    List<DevicesEntity> getDevices();

    void InserterDevices(DevicesEntity devicesEntity);

    DevicesEntity getDevicesById(@Param("id") Long id);

    int updateDevices(@Param("id") Long id, @Param("devices") DevicesEntity devices);

    int updateDeviceRuntimeStateByNameOrCode(
            @Param("device") String device,
            @Param("status") String status,
            @Param("health") String health,
            @Param("online") Boolean online,
            @Param("maintenance") String maintenance
    );

    void deleteDevices(@Param("id") Long id);

    List<DevicesEntity> getDevicesByLabId(Integer labId);
}
