package org.example.backend.service;

import org.example.backend.entity.AcademicScheduleCourse;
import org.example.backend.entity.AcademicScheduleImportRequest;
import org.example.backend.entity.CourseEnvironmentEntity;

import java.util.List;

public interface AcademicScheduleService {
    List<AcademicScheduleCourse> parseSchedule(AcademicScheduleImportRequest request);

    List<AcademicScheduleCourse> fetchSchedule(AcademicScheduleImportRequest request);

    List<CourseEnvironmentEntity> importSchedule(AcademicScheduleImportRequest request);
}
