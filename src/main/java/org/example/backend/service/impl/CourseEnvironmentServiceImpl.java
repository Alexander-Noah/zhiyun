package org.example.backend.service.impl;

import org.example.backend.entity.CourseEnvironmentEntity;
import org.example.backend.entity.EnvironmentTemplateEntity;
import org.example.backend.entity.NoticeEntity;
import org.example.backend.mapper.CourseEnvironmentMapper;
import org.example.backend.mapper.EnvironmentTemplateMapper;
import org.example.backend.mapper.NoticeMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.CourseEnvironmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CourseEnvironmentServiceImpl implements CourseEnvironmentService {
    private final CourseEnvironmentMapper courseEnvironmentMapper;
    private final EnvironmentTemplateMapper environmentTemplateMapper;
    private final NoticeMapper noticeMapper;
    private final BusinessLoopService businessLoopService;

    public CourseEnvironmentServiceImpl(
            CourseEnvironmentMapper courseEnvironmentMapper,
            EnvironmentTemplateMapper environmentTemplateMapper,
            NoticeMapper noticeMapper,
            BusinessLoopService businessLoopService
    ) {
        this.courseEnvironmentMapper = courseEnvironmentMapper;
        this.environmentTemplateMapper = environmentTemplateMapper;
        this.noticeMapper = noticeMapper;
        this.businessLoopService = businessLoopService;
    }

    @Override
    public List<CourseEnvironmentEntity> getEnvironment() {
        return courseEnvironmentMapper.getEnvironment();
    }

    @Override
    @Transactional
    public Object InserterCourseEnvironment(CourseEnvironmentEntity courseEnvironment) {
        CourseEnvironmentEntity normalizedCourseEnvironment = normalizeCourseEnvironment(courseEnvironment);
        courseEnvironmentMapper.InserterCourseEnvironment(normalizedCourseEnvironment);
        CourseEnvironmentEntity savedCourseEnvironment = courseEnvironmentMapper.getCourseEnvironment(normalizedCourseEnvironment.getId());
        recordCourseEnvironmentEvent(savedCourseEnvironment, "submit", savedCourseEnvironment.getProcessStatus());
        return savedCourseEnvironment;
    }

    @Override
    public Object getCourseEnvironment(Integer id) {
        return courseEnvironmentMapper.getCourseEnvironment(id);
    }

    @Override
    @Transactional
    public Object updateCourseEnvironment(Integer id, CourseEnvironmentEntity courseEnvironment) {
        courseEnvironmentMapper.updateCourseEnvironment(id, normalizeCourseEnvironment(courseEnvironment));
        CourseEnvironmentEntity savedCourseEnvironment = courseEnvironmentMapper.getCourseEnvironment(id);
        recordCourseEnvironmentEvent(savedCourseEnvironment, "configure", savedCourseEnvironment.getProcessStatus());
        if (isConfirmed(savedCourseEnvironment)) {
            closeLoopAfterConfirmed(savedCourseEnvironment);
        }
        return savedCourseEnvironment;
    }

    @Override
    @Transactional
    public Object deleteCourseEnvironment(Integer id) {
        CourseEnvironmentEntity existingCourseEnvironment = courseEnvironmentMapper.getCourseEnvironment(id);
        courseEnvironmentMapper.deleteCourseEnvironment(id);
        recordCourseEnvironmentEvent(existingCourseEnvironment, "delete", "已删除");
        return courseEnvironmentMapper.getEnvironment();
    }

    @Override
    @Transactional
    public Object confirmCourseEnvironment(Integer id) {
        courseEnvironmentMapper.confirmCourseEnvironment(id);
        CourseEnvironmentEntity savedCourseEnvironment = courseEnvironmentMapper.getCourseEnvironment(id);
        closeLoopAfterConfirmed(savedCourseEnvironment);
        return savedCourseEnvironment;
    }

    private CourseEnvironmentEntity normalizeCourseEnvironment(CourseEnvironmentEntity courseEnvironment) {
        CourseEnvironmentEntity normalizedCourseEnvironment = courseEnvironment == null ? new CourseEnvironmentEntity() : courseEnvironment;
        if (isBlank(normalizedCourseEnvironment.getCourseName())) {
            normalizedCourseEnvironment.setCourseName(normalizedCourseEnvironment.getCourse());
        }
        if (isBlank(normalizedCourseEnvironment.getCourse())) {
            normalizedCourseEnvironment.setCourse(normalizedCourseEnvironment.getCourseName());
        }
        if (isBlank(normalizedCourseEnvironment.getCourseName())) {
            normalizedCourseEnvironment.setCourseName("新课程环境");
            normalizedCourseEnvironment.setCourse("新课程环境");
        }
        if (isBlank(normalizedCourseEnvironment.getSoftwareRequirements())) {
            normalizedCourseEnvironment.setSoftwareRequirements(normalizedCourseEnvironment.getSoftware());
        }
        if (isBlank(normalizedCourseEnvironment.getSoftware())) {
            normalizedCourseEnvironment.setSoftware(normalizedCourseEnvironment.getSoftwareRequirements());
        }
        if (isBlank(normalizedCourseEnvironment.getProcessStatus())) {
            normalizedCourseEnvironment.setProcessStatus(normalizedCourseEnvironment.getStatus());
        }
        if (isBlank(normalizedCourseEnvironment.getProcessStatus())) {
            normalizedCourseEnvironment.setProcessStatus(normalizedCourseEnvironment.getAssignedLabId() == null ? "待配置" : "配置中");
        }
        if (isBlank(normalizedCourseEnvironment.getStatus())) {
            normalizedCourseEnvironment.setStatus(normalizedCourseEnvironment.getProcessStatus());
        }
        if (isBlank(normalizedCourseEnvironment.getConfirmStatus())) {
            normalizedCourseEnvironment.setConfirmStatus(isConfirmed(normalizedCourseEnvironment) ? "已确认" : "待确认");
        }
        if (normalizedCourseEnvironment.getTeacherUserId() == null) {
            normalizedCourseEnvironment.setTeacherUserId(3);
        }
        if (isBlank(normalizedCourseEnvironment.getClassName())) {
            normalizedCourseEnvironment.setClassName("待填写班级");
        }
        if (isBlank(normalizedCourseEnvironment.getUseTime())) {
            normalizedCourseEnvironment.setUseTime("待确认");
        }
        if (isBlank(normalizedCourseEnvironment.getLabType())) {
            normalizedCourseEnvironment.setLabType("计算机实验室");
        }
        return normalizedCourseEnvironment;
    }

    private void closeLoopAfterConfirmed(CourseEnvironmentEntity courseEnvironment) {
        if (courseEnvironment == null) {
            return;
        }

        EnvironmentTemplateEntity template = environmentTemplateMapper.getTemplateByCourse(courseEnvironment.getCourseName());
        EnvironmentTemplateEntity nextTemplate = buildTemplate(courseEnvironment);
        if (template == null) {
            environmentTemplateMapper.insertTemplate(nextTemplate);
        } else {
            environmentTemplateMapper.updateTemplate(template.getId(), nextTemplate);
        }

        NoticeEntity notice = new NoticeEntity();
        notice.setTitle(courseEnvironment.getCourseName() + "课程环境已确认");
        notice.setType("环境确认");
        notice.setNoticeType("环境确认");
        notice.setTarget("teacher");
        notice.setTargetRole("teacher");
        notice.setContent("课程环境已完成配置并沉淀为可复用模板，任课教师可按计划使用。");
        notice.setStatus("已发布");
        notice.setPublishStatus("已发布");
        notice.setPublishTime(LocalDateTime.now());
        noticeMapper.insertNotice(notice);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("courseEnvironmentId", courseEnvironment.getId());
        details.put("templateCourse", nextTemplate.getCourse());
        details.put("assignedLab", firstNonBlank(courseEnvironment.getAssignedLabName(), courseEnvironment.getAssignedLabCode()));
        businessLoopService.recordEvent("course-environment", "confirm", courseEnvironment.getCourseName(), "已沉淀模板并发布通知", details);
    }

    private EnvironmentTemplateEntity buildTemplate(CourseEnvironmentEntity courseEnvironment) {
        EnvironmentTemplateEntity template = new EnvironmentTemplateEntity();
        template.setName(courseEnvironment.getCourseName() + "环境模板");
        template.setCourse(courseEnvironment.getCourseName());
        template.setOs(firstNonBlank(courseEnvironment.getSystemRequirements(), "按课程要求配置"));
        template.setSoftwareList(firstNonBlank(courseEnvironment.getSoftwareRequirements(), courseEnvironment.getSoftware(), "按课程要求配置"));
        template.setLabs(firstNonBlank(courseEnvironment.getAssignedLabName(), courseEnvironment.getAssignedLabCode(), courseEnvironment.getLabType()));
        template.setStatus("可复用");
        template.setTagType("success");
        return template;
    }

    private void recordCourseEnvironmentEvent(CourseEnvironmentEntity courseEnvironment, String action, String status) {
        if (courseEnvironment == null) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", courseEnvironment.getId());
        details.put("course", courseEnvironment.getCourseName());
        details.put("className", courseEnvironment.getClassName());
        details.put("assignedLab", firstNonBlank(courseEnvironment.getAssignedLabName(), courseEnvironment.getAssignedLabCode()));
        businessLoopService.recordEvent("course-environment", action, courseEnvironment.getCourseName(), status, details);
    }

    private boolean isConfirmed(CourseEnvironmentEntity courseEnvironment) {
        String status = firstNonBlank(courseEnvironment.getConfirmStatus(), courseEnvironment.getProcessStatus(), courseEnvironment.getStatus());
        return status.contains("确认") && !status.contains("待");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
