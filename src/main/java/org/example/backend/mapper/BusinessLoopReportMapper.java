package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface BusinessLoopReportMapper {
    @Select("select count(1) from lab_reservation")
    long countReservations();

    @Select("select count(1) from lab_reservation where status = '\u5df2\u901a\u8fc7'")
    long countApprovedReservations();

    @Select("select count(1) from lab_reservation where status in ('\u5f85\u5ba1\u6838', '\u51b2\u7a81')")
    long countPendingReservations();

    @Select("select count(1) from usage_record")
    long countUsageRecords();

    @Select("select count(1) from usage_record where remark like concat('%', '\u9884\u7ea6\u7f16\u53f7', '%')")
    long countUsageRecordsFromReservations();

    @Select("select count(1) from usage_record where status = '\u5f02\u5e38'")
    long countAbnormalUsageRecords();

    @Select("select count(1) from repair_order")
    long countRepairOrders();

    @Select("select count(1) from repair_order where description like concat('%', '\u4f7f\u7528\u8bb0\u5f55#', '%')")
    long countRepairOrdersFromUsageRecords();

    @Select("select count(1) from repair_order where status in ('\u5f85\u6d3e\u5355', '\u5904\u7406\u4e2d', '\u5f85\u9a8c\u6536')")
    long countActiveRepairOrders();

    @Select("select count(1) from repair_order where status in ('\u5df2\u5b8c\u6210', '\u5df2\u5173\u95ed')")
    long countClosedRepairOrders();

    @Select("select count(1) from device")
    long countDevices();

    @Select("select count(1) from device where status = '\u6b63\u5e38'")
    long countNormalDevices();

    @Select("select count(1) from device where status = '\u7ef4\u62a4\u4e2d'")
    long countMaintenanceDevices();

    @Select("select count(1) from device where status = '\u6545\u969c'")
    long countFaultDevices();

    @Select("select count(1) from course_environment_request")
    long countCourseEnvironmentRequests();

    @Select("select count(1) from course_environment_request where process_status = '\u5df2\u786e\u8ba4' or confirm_status = '\u5df2\u786e\u8ba4'")
    long countConfirmedCourseEnvironmentRequests();

    @Select("select count(1) from environment_template")
    long countEnvironmentTemplates();

    @Select("select count(1) from consumable_inventory")
    long countConsumables();

    @Select("select count(1) from consumable_inventory where tag_type in ('danger', 'warning') or stock <= warn_threshold")
    long countLowStockConsumables();

    @Select("select count(1) from notice")
    long countNotices();

    @Select("select count(1) from notice where publish_status = '\u5df2\u53d1\u5e03'")
    long countPublishedNotices();

    @Select("select count(1) from module_record where module_name = 'operation-events'")
    long countOperationEvents();

    @Select("""
            select
                date_format(use_time, '%m-%d') as name,
                count(1) as value
            from usage_record
            where use_time >= date_sub(curdate(), interval 6 day)
            group by date_format(use_time, '%m-%d')
            order by min(use_time)
            """)
    List<Map<String, Object>> listUsageTrend();

    @Select("""
            select
                coalesce(l.lab_code, resource) as name,
                count(1) as value
            from usage_record ur
            left join lab l on ur.resource = l.lab_name or ur.resource = concat(l.lab_code, ' ', l.lab_name)
            group by coalesce(l.lab_code, resource)
            order by value desc
            limit 8
            """)
    List<Map<String, Object>> listLabUsageRanking();

    @Select("""
            select
                coalesce(nullif(fault_type, ''), '\u672a\u5206\u7c7b') as name,
                count(1) as value
            from repair_order
            group by coalesce(nullif(fault_type, ''), '\u672a\u5206\u7c7b')
            order by value desc
            limit 8
            """)
    List<Map<String, Object>> listRepairFaultRanking();

    @Select("""
            select record_json
            from module_record
            where module_name = 'operation-events'
            order by id desc
            limit #{limit}
            """)
    List<String> listLatestEventJson(@Param("limit") int limit);
}
