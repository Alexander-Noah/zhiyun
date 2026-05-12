package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.UsageRecordEntity;

import java.util.List;

@Mapper
public interface UsageRecordMapper {
    List<UsageRecordEntity> listUsageRecords();

    UsageRecordEntity getUsageRecord(@Param("id") Long id);

    int insertUsageRecord(UsageRecordEntity usageRecord);

    int countUsageRecordBySignature(
            @Param("person") String person,
            @Param("resource") String resource,
            @Param("scene") String scene,
            @Param("useTime") java.time.LocalDateTime useTime
    );

    int updateUsageRecord(@Param("id") Long id, @Param("record") UsageRecordEntity usageRecord);

    int updateUsageRecordStatus(@Param("id") Long id, @Param("status") String status);

    void deleteAllUsageRecords();
}
