package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.CourseEnvironmentEntity;

import java.util.List;

@Mapper
public interface CourseEnvironmentMapper {
    List<CourseEnvironmentEntity> getEnvironment();

    int InserterCourseEnvironment(CourseEnvironmentEntity courseEnvironment);

    CourseEnvironmentEntity getCourseEnvironment(@Param("id") Integer id);

    int updateCourseEnvironment(@Param("id") Integer id, @Param("courseEnvironment") CourseEnvironmentEntity courseEnvironment);

    int deleteCourseEnvironment(@Param("id") Integer id);

    int confirmCourseEnvironment(@Param("id") Integer id);

    int deleteAllCourseEnvironments();
}
