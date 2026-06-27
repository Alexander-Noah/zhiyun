package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {
    long countLabs(@Param("managerUserId") Integer managerUserId);

    long countOpenLabs(@Param("managerUserId") Integer managerUserId);

    long countUsingLabs(@Param("managerUserId") Integer managerUserId);

    long countDevices(@Param("managerUserId") Integer managerUserId);

    long countOnlineDevices(@Param("managerUserId") Integer managerUserId);

    long countAbnormalDevices(@Param("managerUserId") Integer managerUserId);

    long countPendingReservations(@Param("managerUserId") Integer managerUserId);

    long countTodayReservations(@Param("managerUserId") Integer managerUserId);

    long countApprovedTodayReservations(@Param("managerUserId") Integer managerUserId);

    long countActiveRepairs(@Param("managerUserId") Integer managerUserId);

    long countHighPriorityRepairs(@Param("managerUserId") Integer managerUserId);

    long countTodayCourses(@Param("managerUserId") Integer managerUserId);

    long countStartedTodayCourses(@Param("managerUserId") Integer managerUserId);

    List<Long> listActiveLabIds(@Param("managerUserId") Integer managerUserId);

    List<Map<String, Object>> listLabUsage(@Param("managerUserId") Integer managerUserId);

    List<Map<String, Object>> listDeviceStatus(@Param("managerUserId") Integer managerUserId);

    List<Map<String, Object>> listNotices();

    List<Map<String, Object>> listDoorAlerts(@Param("managerUserId") Integer managerUserId);

    List<Map<String, Object>> listTodaySchedules(@Param("managerUserId") Integer managerUserId);

    List<Map<String, Object>> listTodoItems(@Param("managerUserId") Integer managerUserId);

    List<Map<String, Object>> listRecordReminders(@Param("managerUserId") Integer managerUserId);

    List<Map<String, Object>> listRecentActivities(@Param("managerUserId") Integer managerUserId);
}
