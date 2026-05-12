package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.ScheduleAdjustmentEntity;

import java.util.List;

@Mapper
public interface ScheduleAdjustmentMapper {
    List<ScheduleAdjustmentEntity> listAdjustments();

    ScheduleAdjustmentEntity getAdjustment(@Param("id") Long id);

    int insertAdjustment(ScheduleAdjustmentEntity adjustment);

    int updateAdjustment(@Param("id") Long id, @Param("adjustment") ScheduleAdjustmentEntity adjustment);

    int patchAdjustment(@Param("id") Long id, @Param("status") String status, @Param("tagType") String tagType, @Param("flowStep") Integer flowStep);
}
