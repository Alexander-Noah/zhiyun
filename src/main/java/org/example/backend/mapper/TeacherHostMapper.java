package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.TeacherHostEntity;

@Mapper
public interface TeacherHostMapper {
    int upsertHeartbeat(TeacherHostEntity host);

    TeacherHostEntity selectCurrentByLabId(@Param("labId") Long labId);

    int markOffline(@Param("teacherDeviceId") String teacherDeviceId, @Param("labId") Long labId);
}
