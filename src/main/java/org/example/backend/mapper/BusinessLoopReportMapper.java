package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface BusinessLoopReportMapper {
    long countReservations();

    long countApprovedReservations();

    long countPendingReservations();

    long countUsageRecords();

    long countUsageRecordsFromReservations();

    long countAbnormalUsageRecords();

    long countRepairOrders();

    long countRepairOrdersFromUsageRecords();

    long countActiveRepairOrders();

    long countClosedRepairOrders();

    long countDevices();

    long countNormalDevices();

    long countMaintenanceDevices();

    long countFaultDevices();

    long countCourseEnvironmentRequests();

    long countConfirmedCourseEnvironmentRequests();

    long countEnvironmentTemplates();

    long countConsumables();

    long countLowStockConsumables();

    long countNotices();

    long countPublishedNotices();

    long countOperationEvents();

    List<Map<String, Object>> listUsageTrend();

    List<Map<String, Object>> listLabUsageRanking();

    List<Map<String, Object>> listRepairFaultRanking();

    List<String> listLatestEventJson(@Param("limit") int limit);
}