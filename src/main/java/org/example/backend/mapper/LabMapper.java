package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.backend.VO.LabOptionVO;
import org.example.backend.entity.LabEntity;

import java.util.List;

@Mapper
public interface LabMapper {
    @Select("""
            select
                l.id,
                l.lab_code as labCode,
                l.lab_name as labName,
                l.building,
                l.floor,
                l.room_no as roomNo,
                l.lab_type as labType,
                l.capacity,
                l.manager_user_id as managerUserId,
                u.real_name as managerName,
                l.open_status as openStatus,
                count(d.id) as deviceCount,
                l.remark,
                l.created_at as createdAt,
                l.updated_at as updatedAt
            from lab l
            left join sys_user u on u.id = l.manager_user_id
            left join device d on d.lab_id = l.id
            group by
                l.id,
                l.lab_code,
                l.lab_name,
                l.building,
                l.floor,
                l.room_no,
                l.lab_type,
                l.capacity,
                l.manager_user_id,
                u.real_name,
                l.open_status,
                l.remark,
                l.created_at,
                l.updated_at
            order by l.id
            """)
    List<LabEntity> getLabs();

    @Select("""
            select
                l.id,
                l.lab_code as labCode,
                l.lab_name as labName,
                l.building,
                l.floor,
                l.room_no as roomNo,
                l.lab_type as labType,
                l.capacity,
                l.manager_user_id as managerUserId,
                u.real_name as managerName,
                l.open_status as openStatus,
                count(d.id) as deviceCount,
                l.remark,
                l.created_at as createdAt,
                l.updated_at as updatedAt
            from lab l
            left join sys_user u on u.id = l.manager_user_id
            left join device d on d.lab_id = l.id
            where l.manager_user_id = #{managerUserId}
            group by
                l.id,
                l.lab_code,
                l.lab_name,
                l.building,
                l.floor,
                l.room_no,
                l.lab_type,
                l.capacity,
                l.manager_user_id,
                u.real_name,
                l.open_status,
                l.remark,
                l.created_at,
                l.updated_at
            order by l.id
            """)
    List<LabEntity> getLabsByManagerUserId(@Param("managerUserId") Integer managerUserId);

    int addLab(LabEntity lab);

    LabEntity getLabById(Integer id);

    void updateLab(@Param("id") Integer id, @Param("lab") LabEntity lab);

    int updateLabOpenStatus(@Param("id") Integer id, @Param("openStatus") String openStatus);

    int deleteRepairsByLabId(Integer id);

    int deleteReservationsByLabId(Integer id);

    int deleteCourseEnvironmentsByLabId(Integer id);

    int deleteLabSoftwareByLabId(Integer id);

    int deleteDevicesByLabId(Integer id);

    int deleteLab(Integer id);

    @Select("""
        SELECT
            id,
            lab_name AS labName
        FROM lab
        ORDER BY id DESC
    """)
    List<LabOptionVO> selectLabOptions();
}
