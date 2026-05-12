package org.example.backend.service;

import org.example.backend.entity.RepairEntity;
import org.example.backend.entity.ReservationsEntity;
import org.example.backend.entity.UsageRecordEntity;

import java.util.Map;

public interface BusinessLoopService {

    /**
     * 预约审批通过后，自动生成使用记录
     */
    void syncUsageRecordAfterReservationApproved(ReservationsEntity reservation);

    /**
     * 使用记录异常后，自动生成维修工单
     */
    RepairEntity createRepairAfterUsageAbnormal(UsageRecordEntity usageRecord);

    /**
     * 报修状态变化后，同步设备状态
     */
    void syncDeviceAfterRepairChanged(RepairEntity repair);

    /**
     * 记录业务事件日志
     */
    void recordEvent(String category, String action, String subject, String status, Map<String, ?> details);
}