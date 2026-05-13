package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.DeviceTransferRecordEntity;

import java.util.List;

@Mapper
public interface DeviceTransferRecordMapper {
    void createTableIfNotExists();

    int insertRecord(DeviceTransferRecordEntity record);

    List<DeviceTransferRecordEntity> listRecords(@Param("deviceId") Long deviceId);

    List<DeviceTransferRecordEntity> listRecordsByDeviceId(@Param("deviceId") Long deviceId);
}
