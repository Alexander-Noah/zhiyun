package org.example.backend.service;

import org.example.backend.entity.AcademicCredentialView;
import org.example.backend.entity.ClassTimetableEntity;

import java.util.List;
import java.util.Map;

public interface ClassTimetableService {
    List<ClassTimetableEntity> listTimetables(
            String semester,
            String teacher,
            String className,
            String classroom,
            String courseName,
            String keyword,
            Integer week,
            Integer limit
    );

    Map<String, Object> getSummary();

    List<String> listSemesters();

    Map<String, Object> triggerCrawler();

    AcademicCredentialView getCrawlerCredential();

    AcademicCredentialView saveCrawlerCredential(Map<String, String> payload);

    void deleteCrawlerCredential();
}
