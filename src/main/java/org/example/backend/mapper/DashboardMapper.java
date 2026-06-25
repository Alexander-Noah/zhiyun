package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {
    @Select("select count(*) from lab where (#{managerUserId} is null or manager_user_id = #{managerUserId})")
    long countLabs(@Param("managerUserId") Integer managerUserId);

    @Select("select count(*) from lab where (#{managerUserId} is null or manager_user_id = #{managerUserId}) and coalesce(open_status, '') not in ('维护中', '停用')")
    long countOpenLabs(@Param("managerUserId") Integer managerUserId);

    @Select("select count(*) from lab where (#{managerUserId} is null or manager_user_id = #{managerUserId}) and open_status in ('使用中', '占用中')")
    long countUsingLabs(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select count(*)
            from device d
            left join lab l on l.id = d.lab_id
            where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
            """)
    long countDevices(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select count(*)
            from device d
            left join lab l on l.id = d.lab_id
            where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
              and coalesce(d.online, 1) = 1
              and coalesce(d.status, '') not in ('故障', '已报修', '停用', '报废')
            """)
    long countOnlineDevices(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select count(*)
            from device d
            left join lab l on l.id = d.lab_id
            where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
              and coalesce(d.status, '') in ('故障', '已报修', '维修中', '维护中')
            """)
    long countAbnormalDevices(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select count(*)
            from lab_reservation r
            left join lab l on l.id = r.lab_id
            where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
              and r.status = '待审核'
            """)
    long countPendingReservations(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select count(*)
            from lab_reservation r
            left join lab l on l.id = r.lab_id
            where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
              and r.reservation_date = curdate()
            """)
    long countTodayReservations(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select count(*)
            from lab_reservation r
            left join lab l on l.id = r.lab_id
            where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
              and r.status in ('已通过', '通过')
              and r.reservation_date = curdate()
            """)
    long countApprovedTodayReservations(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select count(*)
            from repair_order r
            left join lab l on l.id = r.lab_id
            where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
              and r.status in ('待派单', '处理中', '待验收')
            """)
    long countActiveRepairs(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select count(*)
            from repair_order r
            left join lab l on l.id = r.lab_id
            where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
              and r.priority_level in ('高', '紧急')
              and r.status in ('待派单', '处理中', '待验收')
            """)
    long countHighPriorityRepairs(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select count(*)
            from class_timetable t
            join lab l on (
                (
                    nullif(l.lab_code, '') is not null
                    and (
                        t.classroom = l.lab_code
                        or t.classroom like concat(l.lab_code, '-%')
                        or t.classroom like concat(l.lab_code, ' %')
                        or t.classroom like concat('% ', l.lab_code, ' %')
                        or t.classroom like concat('% ', l.lab_code, '-%')
                    )
                )
                or (
                    nullif(l.lab_name, '') is not null
                    and replace(lower(t.classroom), ' ', '') like concat('%', replace(lower(l.lab_name), ' ', ''), '%')
                )
            )
            where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
              and find_in_set(cast(least(25, greatest(1, floor(datediff(curdate(), '2026-03-02') / 7) + 1)) as char), t.week_expanded) > 0
              and t.weekday = case dayofweek(curdate())
                when 1 then '星期日'
                when 2 then '星期一'
                when 3 then '星期二'
                when 4 then '星期三'
                when 5 then '星期四'
                when 6 then '星期五'
                when 7 then '星期六'
            end
            """)
    long countTodayCourses(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select count(*)
            from class_timetable t
            join lab l on (
                (
                    nullif(l.lab_code, '') is not null
                    and (
                        t.classroom = l.lab_code
                        or t.classroom like concat(l.lab_code, '-%')
                        or t.classroom like concat(l.lab_code, ' %')
                        or t.classroom like concat('% ', l.lab_code, ' %')
                        or t.classroom like concat('% ', l.lab_code, '-%')
                    )
                )
                or (
                    nullif(l.lab_name, '') is not null
                    and replace(lower(t.classroom), ' ', '') like concat('%', replace(lower(l.lab_name), ' ', ''), '%')
                )
            )
            where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
              and find_in_set(cast(least(25, greatest(1, floor(datediff(curdate(), '2026-03-02') / 7) + 1)) as char), t.week_expanded) > 0
              and t.weekday = case dayofweek(curdate())
                when 1 then '星期日'
                when 2 then '星期一'
                when 3 then '星期二'
                when 4 then '星期三'
                when 5 then '星期四'
                when 6 then '星期五'
                when 7 then '星期六'
            end
              and case ceil(coalesce(t.end_section, t.start_section, 0) / 2)
                    when 1 then '10:00:00'
                    when 2 then '11:50:00'
                    when 3 then '15:30:00'
                    when 4 then '17:20:00'
                    when 5 then '20:10:00'
                    when 6 then '21:50:00'
                    else '00:00:00'
                  end <= curtime()
            """)
    long countStartedTodayCourses(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select distinct labId
            from (
                select l.id as labId
                from class_timetable t
                join lab l on (
                    (
                        nullif(l.lab_code, '') is not null
                        and (
                            t.classroom = l.lab_code
                            or t.classroom like concat(l.lab_code, '-%')
                            or t.classroom like concat(l.lab_code, ' %')
                            or t.classroom like concat('% ', l.lab_code, ' %')
                            or t.classroom like concat('% ', l.lab_code, '-%')
                        )
                    )
                    or (
                        nullif(l.lab_name, '') is not null
                        and replace(lower(t.classroom), ' ', '') like concat('%', replace(lower(l.lab_name), ' ', ''), '%')
                    )
                )
                where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
                  and find_in_set(cast(least(25, greatest(1, floor(datediff(curdate(), '2026-03-02') / 7) + 1)) as char), t.week_expanded) > 0
                  and t.weekday = case dayofweek(curdate())
                    when 1 then '星期日'
                    when 2 then '星期一'
                    when 3 then '星期二'
                    when 4 then '星期三'
                    when 5 then '星期四'
                    when 6 then '星期五'
                    when 7 then '星期六'
                end
                  and case ceil(coalesce(t.start_section, t.end_section, 0) / 2)
                        when 1 then '08:30:00'
                        when 2 then '10:20:00'
                        when 3 then '14:00:00'
                        when 4 then '15:50:00'
                        when 5 then '18:40:00'
                        when 6 then '20:20:00'
                        else '23:59:59'
                      end <= curtime()
                  and case ceil(coalesce(t.end_section, t.start_section, 0) / 2)
                        when 1 then '10:00:00'
                        when 2 then '11:50:00'
                        when 3 then '15:30:00'
                        when 4 then '17:20:00'
                        when 5 then '20:10:00'
                        when 6 then '21:50:00'
                        else '00:00:00'
                      end >= curtime()
                union
                select l.id as labId
                from lab_reservation r
                join lab l on l.id = r.lab_id
                where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
                  and r.status in ('已通过', '通过')
                  and r.reservation_date = curdate()
                  and trim(substring_index(r.time_range, '-', 1)) regexp '^([01]?[0-9]|2[0-3]):[0-5][0-9]$'
                  and trim(substring_index(r.time_range, '-', -1)) regexp '^([01]?[0-9]|2[0-3]):[0-5][0-9]$'
                  and now() between
                    str_to_date(concat(r.reservation_date, ' ', trim(substring_index(r.time_range, '-', 1))), '%Y-%m-%d %H:%i')
                    and str_to_date(concat(r.reservation_date, ' ', trim(substring_index(r.time_range, '-', -1))), '%Y-%m-%d %H:%i')
            ) activeLabs
            """)
    List<Long> listActiveLabIds(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select
                coalesce(lab_code, lab_name, concat('LAB-', id)) as name,
                case
                    when open_status in ('使用中', '占用中') then 88
                    when open_status in ('预约中', '待预约') then 64
                    when open_status in ('维护中', '停用') then 12
                    else 35
                end as value
            from lab
            where (#{managerUserId} is null or manager_user_id = #{managerUserId})
            order by value desc, id
            limit 8
            """)
    List<Map<String, Object>> listLabUsage(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select
                case
                    when coalesce(online, 1) = 1 and coalesce(status, '') not in ('故障', '已报修', '停用', '报废', '维护中') then '正常在线'
                    when coalesce(status, '') in ('故障', '已报修') then '故障待修'
                    when coalesce(status, '') in ('维修中', '维护中') then '维护中'
                    else '闲置备用'
                end as name,
                count(*) as value,
                case
                    when coalesce(online, 1) = 1 and coalesce(status, '') not in ('故障', '已报修', '停用', '报废', '维护中') then '#16a34a'
                    when coalesce(status, '') in ('故障', '已报修') then '#ef4444'
                    when coalesce(status, '') in ('维修中', '维护中') then '#f59e0b'
                    else '#60a5fa'
                end as color,
                case
                    when coalesce(online, 1) = 1 and coalesce(status, '') not in ('故障', '已报修', '停用', '报废', '维护中') then 'success'
                    when coalesce(status, '') in ('故障', '已报修') then 'danger'
                    when coalesce(status, '') in ('维修中', '维护中') then 'warning'
                    else 'primary'
                end as type
            from device
            left join lab l on l.id = device.lab_id
            where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
            group by name, color, type
            order by value desc
            """)
    List<Map<String, Object>> listDeviceStatus(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select
                title,
                coalesce(notice_type, '通知') as type,
                coalesce(content, title) as summary,
                date_format(coalesce(publish_time, created_at), '%Y-%m-%d %H:%i') as publishTime
            from notice
            order by coalesce(publish_time, created_at) desc
            limit 5
            """)
    List<Map<String, Object>> listNotices();

    @Select("""
            select
                concat(coalesce(l.lab_code, '实验室'), ' 即将开始') as title,
                concat(coalesce(l.lab_code, ''), ' ', coalesce(l.lab_name, '实验室')) as lab,
                trim(substring_index(r.time_range, '-', 1)) as time,
                concat(coalesce(r.scene, '预约'), ' ', r.time_range, ' 开始，预约人：', coalesce(r.applicant_name, '未登记')) as `desc`,
                case
                    when timestampdiff(minute, now(), case
                        when trim(substring_index(r.time_range, '-', 1)) regexp '^([01]?[0-9]|2[0-3]):[0-5][0-9]$'
                        then str_to_date(concat(r.reservation_date, ' ', trim(substring_index(r.time_range, '-', 1))), '%Y-%m-%d %H:%i')
                    end) <= 20 then 'warning'
                    else 'primary'
                end as type,
                '查看预约' as action,
                '/reservations' as path
            from lab_reservation r
            left join lab l on l.id = r.lab_id
            where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
              and r.status in ('已通过', '通过')
              and r.reservation_date = curdate()
              and timestampdiff(minute, now(), case
                    when trim(substring_index(r.time_range, '-', 1)) regexp '^([01]?[0-9]|2[0-3]):[0-5][0-9]$'
                    then str_to_date(concat(r.reservation_date, ' ', trim(substring_index(r.time_range, '-', 1))), '%Y-%m-%d %H:%i')
                  end) between -10 and 60
            order by str_to_date(trim(substring_index(r.time_range, '-', 1)), '%H:%i')
            limit 4
            """)
    List<Map<String, Object>> listDoorAlerts(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select
                case ceil(coalesce(t.start_section, t.end_section, 0) / 2)
                    when 1 then '08:30-10:00'
                    when 2 then '10:20-11:50'
                    when 3 then '14:00-15:30'
                    when 4 then '15:50-17:20'
                    when 5 then '18:40-20:10'
                    when 6 then '20:20-21:50'
                    else coalesce(t.section_text, '待确认')
                end as time,
                coalesce(l.lab_code, substring_index(t.classroom, ' ', 1), '实验室') as lab,
                t.course_name as course,
                t.teacher,
                coalesce(t.class_name, t.row_class_name) as className,
                case
                    when case ceil(coalesce(t.start_section, t.end_section, 0) / 2)
                            when 1 then '08:30:00'
                            when 2 then '10:20:00'
                            when 3 then '14:00:00'
                            when 4 then '15:50:00'
                            when 5 then '18:40:00'
                            when 6 then '20:20:00'
                            else '23:59:59'
                         end <= curtime()
                     and case ceil(coalesce(t.end_section, t.start_section, 0) / 2)
                            when 1 then '10:00:00'
                            when 2 then '11:50:00'
                            when 3 then '15:30:00'
                            when 4 then '17:20:00'
                            when 5 then '20:10:00'
                            when 6 then '21:50:00'
                            else '00:00:00'
                         end >= curtime() then '进行中'
                    when case ceil(coalesce(t.start_section, t.end_section, 0) / 2)
                            when 1 then '08:30:00'
                            when 2 then '10:20:00'
                            when 3 then '14:00:00'
                            when 4 then '15:50:00'
                            when 5 then '18:40:00'
                            when 6 then '20:20:00'
                            else '00:00:00'
                         end > curtime() then '即将开始'
                    else '已结束'
                end as status,
                case
                    when case ceil(coalesce(t.start_section, t.end_section, 0) / 2)
                            when 1 then '08:30:00'
                            when 2 then '10:20:00'
                            when 3 then '14:00:00'
                            when 4 then '15:50:00'
                            when 5 then '18:40:00'
                            when 6 then '20:20:00'
                            else '23:59:59'
                         end <= curtime()
                     and case ceil(coalesce(t.end_section, t.start_section, 0) / 2)
                            when 1 then '10:00:00'
                            when 2 then '11:50:00'
                            when 3 then '15:30:00'
                            when 4 then '17:20:00'
                            when 5 then '20:10:00'
                            when 6 then '21:50:00'
                            else '00:00:00'
                         end >= curtime() then 'success'
                    when case ceil(coalesce(t.start_section, t.end_section, 0) / 2)
                            when 1 then '08:30:00'
                            when 2 then '10:20:00'
                            when 3 then '14:00:00'
                            when 4 then '15:50:00'
                            when 5 then '18:40:00'
                            when 6 then '20:20:00'
                            else '00:00:00'
                         end > curtime() then 'warning'
                    else 'info'
                end as statusType
            from class_timetable t
            join lab l on (
                (
                    nullif(l.lab_code, '') is not null
                    and (
                        t.classroom = l.lab_code
                        or t.classroom like concat(l.lab_code, '-%')
                        or t.classroom like concat(l.lab_code, ' %')
                        or t.classroom like concat('% ', l.lab_code, ' %')
                        or t.classroom like concat('% ', l.lab_code, '-%')
                    )
                )
                or (
                    nullif(l.lab_name, '') is not null
                    and replace(lower(t.classroom), ' ', '') like concat('%', replace(lower(l.lab_name), ' ', ''), '%')
                )
            )
            where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
              and find_in_set(cast(least(25, greatest(1, floor(datediff(curdate(), '2026-03-02') / 7) + 1)) as char), t.week_expanded) > 0
              and t.weekday = case dayofweek(curdate())
                when 1 then '星期日'
                when 2 then '星期一'
                when 3 then '星期二'
                when 4 then '星期三'
                when 5 then '星期四'
                when 6 then '星期五'
                when 7 then '星期六'
            end
            order by coalesce(t.start_section, 99), t.id
            """)
    List<Map<String, Object>> listTodaySchedules(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select *
            from (
                select
                    '审核实验室预约' as title,
                    concat(coalesce(l.lab_code, '实验室'), ' ', coalesce(r.time_range, ''), ' ', coalesce(r.reason, r.scene, '预约申请')) as `desc`,
                    '今日 18:00' as deadline,
                    if(coalesce(r.conflict_flag, 0) = 1, '高', '中') as level,
                    if(coalesce(r.conflict_flag, 0) = 1, 'danger', 'warning') as type,
                    '/reservations' as path,
                    coalesce(r.updated_at, r.created_at) as sortTime
                from lab_reservation r
                left join lab l on l.id = r.lab_id
                where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
                  and r.status = '待审核'
                union all
                select
                    concat('处理维修工单 ', coalesce(r.order_no, '')) as title,
                    concat(coalesce(l.lab_code, l.lab_name, '实验室'), ' ', coalesce(d.device_name, '设备'), '：', coalesce(r.description, r.fault_type, '故障待处理')) as `desc`,
                    coalesce(r.deadline, '今日 18:00') as deadline,
                    coalesce(r.priority_level, '中') as level,
                    case when r.priority_level in ('高', '紧急') then 'danger' else 'warning' end as type,
                    '/repairs' as path,
                    coalesce(r.updated_at, r.created_at) as sortTime
                from repair_order r
                left join lab l on l.id = r.lab_id
                left join device d on d.id = r.device_id
                where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
                  and r.status in ('待派单', '处理中', '待验收')
            ) t
            order by sortTime desc
            limit 6
            """)
    List<Map<String, Object>> listTodoItems(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select
                concat(coalesce(ur.resource, '使用记录'), '待复核') as title,
                concat(coalesce(ur.scene, '使用记录'), '：', coalesce(ur.remark, ur.status, '需要管理员确认')) as `desc`,
                case when ur.status = '异常' then 'warning' when ur.status in ('已归档', '正常') then 'success' else 'primary' end as type,
                '/records' as path
            from usage_record ur
            join lab l on (
                locate(nullif(l.lab_code, ''), ur.resource) > 0
                or locate(nullif(l.lab_name, ''), ur.resource) > 0
                or locate(nullif(cast(l.room_no as char), ''), ur.resource) > 0
            )
            where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
              and (
                    date(ur.use_time) = curdate()
                    or ur.status in ('待复核', '异常')
              )
            order by ur.use_time desc, ur.id desc
            limit 4
            """)
    List<Map<String, Object>> listRecordReminders(@Param("managerUserId") Integer managerUserId);

    @Select("""
            select *
            from (
                select
                    date_format(coalesce(r.updated_at, r.created_at), '%H:%i') as time,
                    concat('预约', r.status, '：', coalesce(l.lab_code, l.lab_name, '实验室')) as title,
                    concat(coalesce(r.applicant_name, '申请人'), ' ', coalesce(r.time_range, ''), ' ', coalesce(r.reason, r.scene, '')) as `desc`,
                    case when r.status = '已通过' then 'success' when r.status = '待审核' then 'warning' else 'info' end as type,
                    coalesce(r.updated_at, r.created_at) as sortTime
                from lab_reservation r
                left join lab l on l.id = r.lab_id
                where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
                  and date(coalesce(r.updated_at, r.created_at)) = curdate()
                union all
                select
                    date_format(coalesce(r.updated_at, r.created_at), '%H:%i') as time,
                    concat('维修工单：', coalesce(d.device_name, r.order_no, '设备')) as title,
                    concat(coalesce(l.lab_code, l.lab_name, '实验室'), ' ', coalesce(r.status, ''), '，', coalesce(r.description, r.fault_type, '')) as `desc`,
                    case when r.status in ('待派单', '处理中') then 'warning' when r.status = '已完成' then 'success' else 'primary' end as type,
                    coalesce(r.updated_at, r.created_at) as sortTime
                from repair_order r
                left join lab l on l.id = r.lab_id
                left join device d on d.id = r.device_id
                where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
                  and date(coalesce(r.updated_at, r.created_at)) = curdate()
                union all
                select
                    date_format(ur.use_time, '%H:%i') as time,
                    concat('使用记录：', coalesce(ur.resource, '实验室')) as title,
                    concat(coalesce(ur.person, '使用人'), ' ', coalesce(ur.scene, ''), ' ', coalesce(ur.status, '')) as `desc`,
                    case when ur.status = '异常' then 'warning' when ur.status in ('正常', '已归档') then 'success' else 'primary' end as type,
                    ur.use_time as sortTime
                from usage_record ur
                join lab l on (
                    locate(nullif(l.lab_code, ''), ur.resource) > 0
                    or locate(nullif(l.lab_name, ''), ur.resource) > 0
                    or locate(nullif(cast(l.room_no as char), ''), ur.resource) > 0
                )
                where (#{managerUserId} is null or l.manager_user_id = #{managerUserId})
                  and date(ur.use_time) = curdate()
            ) t
            order by sortTime desc
            limit 6
            """)
    List<Map<String, Object>> listRecentActivities(@Param("managerUserId") Integer managerUserId);
}
