package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.RepairEntity;

import java.util.List;

@Mapper
public interface RepairMapper {
    List<RepairEntity> listRepairs();

    RepairEntity getRepair(@Param("id") String id);

    int insertRepair(RepairEntity repair);

    int countOpenRepairByDescriptionMarker(@Param("marker") String marker);

    int updateRepair(@Param("id") String id, @Param("repair") RepairEntity repair);

    int patchRepair(@Param("id") String id, @Param("repair") RepairEntity repair);

    int deleteRepair(@Param("id") String id);

    int deleteAllRepairs();
}
