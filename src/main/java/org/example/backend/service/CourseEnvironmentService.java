package org.example.backend.service;

import org.example.backend.entity.CourseEnvironmentEntity;

import java.util.List;

public interface CourseEnvironmentService {
    List<CourseEnvironmentEntity> getEnvironment();

    Object InserterCourseEnvironment(CourseEnvironmentEntity courseEnvironment);

    Object getCourseEnvironment(Integer id);

    Object updateCourseEnvironment(Integer id, CourseEnvironmentEntity courseEnvironment);

    Object deleteCourseEnvironment(Integer id);

    Object confirmCourseEnvironment(Integer id);
}
