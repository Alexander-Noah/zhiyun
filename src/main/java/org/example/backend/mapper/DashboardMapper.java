package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {
    @Select("select count(*) from lab")
    long countLabs();

    @Select("select count(*) from device")
    long countDevices();

    @Select("select count(*) from lab_reservation where status = '待审核'")
    long countPendingReservations();

    @Select("select count(*) from repair_order where status in ('待派单', '处理中', '待验收')")
    long countActiveRepairs();

    @Select("""
            select
                open_status as name,
                count(*) as value
            from lab
            group by open_status
            order by value desc
            """)
    List<Map<String, Object>> listLabUsage();

    @Select("""
            select
                status as name,
                count(*) as value
            from device
            group by status
            order by value desc
            """)
    List<Map<String, Object>> listDeviceStatus();

    @Select("""
            select
                title,
                notice_type as type,
                publish_status as status,
                date_format(coalesce(publish_time, created_at), '%Y-%m-%d %H:%i:%s') as time
            from notice
            order by coalesce(publish_time, created_at) desc
            limit 5
            """)
    List<Map<String, Object>> listNotices();
}
