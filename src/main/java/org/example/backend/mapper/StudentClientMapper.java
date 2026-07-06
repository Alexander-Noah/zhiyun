package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.StudentClientEntity;

import java.util.List;

@Mapper
public interface StudentClientMapper {
    int upsertOnline(StudentClientEntity client);

    int markOffline(@Param("studentDeviceId") String studentDeviceId, @Param("labId") Long labId);

    List<StudentClientEntity> selectByLabId(@Param("labId") Long labId);
}
