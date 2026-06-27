package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.backend.VO.LabOptionVO;
import org.example.backend.entity.LabEntity;

import java.util.List;

@Mapper
public interface LabMapper {
    List<LabEntity> getLabs();

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

    List<LabOptionVO> selectLabOptions();
}
