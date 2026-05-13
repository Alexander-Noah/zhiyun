package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.DeviceInventoryRecordEntity;

import java.util.List;

@Mapper
public interface DeviceInventoryRecordMapper {
    void createTableIfNotExists();

    int insertRecord(DeviceInventoryRecordEntity record);

    List<DeviceInventoryRecordEntity> listRecords(@Param("deviceId") Long deviceId);

    List<DeviceInventoryRecordEntity> listRecordsByDeviceId(@Param("deviceId") Long deviceId);
}
