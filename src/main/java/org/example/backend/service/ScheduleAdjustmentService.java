package org.example.backend.service;

import org.example.backend.entity.ScheduleAdjustmentEntity;

import java.util.List;

public interface ScheduleAdjustmentService {
    List<ScheduleAdjustmentEntity> listAdjustments();

    ScheduleAdjustmentEntity createAdjustment(ScheduleAdjustmentEntity adjustment);

    ScheduleAdjustmentEntity updateAdjustment(Long id, ScheduleAdjustmentEntity adjustment);

    ScheduleAdjustmentEntity approveAdjustment(Long id);

    ScheduleAdjustmentEntity completeAdjustment(Long id);
}
