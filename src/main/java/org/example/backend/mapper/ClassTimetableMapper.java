package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.entity.ClassTimetableEntity;

import java.util.List;
import java.util.Map;

@Mapper
public interface ClassTimetableMapper {

    List<ClassTimetableEntity> listTimetables(
            @Param("semester") String semester,
            @Param("teacher") String teacher,
            @Param("className") String className,
            @Param("classroom") String classroom,
            @Param("courseName") String courseName,
            @Param("keyword") String keyword,
            @Param("week") Integer week,
            @Param("limit") Integer limit
    );

    Map<String, Object> getSummary();

    List<String> listSemesters();

    void createClassTimetableTableIfNotExists();

    int deleteBySemester(@Param("semester") String semester);

    int insertTimetable(ClassTimetableEntity entity);

    void createCrawlerConfigTableIfNotExists();

    Map<String, Object> getCrawlerConfig(@Param("configKey") String configKey);

    int upsertCrawlerConfig(
            @Param("configKey") String configKey,
            @Param("scheduleEnabled") Boolean scheduleEnabled,
            @Param("cronExpression") String cronExpression,
            @Param("semesterStartDate") String semesterStartDate
    );

    void createCrawlerTaskTableIfNotExists();

    List<Map<String, Object>> listCrawlerTasks(@Param("limit") Integer limit);

    int insertCrawlerTask(Map<String, Object> task);

    int updateCrawlerTask(Map<String, Object> task);

    int clearCrawlerTasks();

    void createCredentialTableIfNotExists();

    Map<String, Object> getCrawlerCredential(@Param("credentialKey") String credentialKey);

    int upsertCrawlerCredential(
            @Param("credentialKey") String credentialKey,
            @Param("usernameCipher") String usernameCipher,
            @Param("passwordCipher") String passwordCipher
    );

    int deleteCrawlerCredential(@Param("credentialKey") String credentialKey);
}
