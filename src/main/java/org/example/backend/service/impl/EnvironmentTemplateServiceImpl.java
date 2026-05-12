package org.example.backend.service.impl;

import org.example.backend.VO.TeacherOptionVO;
import org.example.backend.entity.EnvironmentTemplateEntity;
import org.example.backend.mapper.EnvironmentTemplateMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.EnvironmentTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class EnvironmentTemplateServiceImpl implements EnvironmentTemplateService {
    private final EnvironmentTemplateMapper environmentTemplateMapper;
    private final BusinessLoopService businessLoopService;

    public EnvironmentTemplateServiceImpl(EnvironmentTemplateMapper environmentTemplateMapper, BusinessLoopService businessLoopService) {
        this.environmentTemplateMapper = environmentTemplateMapper;
        this.businessLoopService = businessLoopService;
    }

    @Override
    public List<EnvironmentTemplateEntity> listTemplates() {
        return environmentTemplateMapper.listTemplates();
    }

    @Override
    @Transactional
    public EnvironmentTemplateEntity createTemplate(EnvironmentTemplateEntity template) {
        EnvironmentTemplateEntity nextTemplate = normalizeTemplate(template);
        environmentTemplateMapper.insertTemplate(nextTemplate);
        businessLoopService.recordEvent("environment-template", "create", nextTemplate.getName(), nextTemplate.getStatus(), Map.of("course", nextTemplate.getCourse()));
        return environmentTemplateMapper.getTemplate(nextTemplate.getId());
    }

    @Override
    @Transactional
    public EnvironmentTemplateEntity updateTemplate(Long id, EnvironmentTemplateEntity template) {
        environmentTemplateMapper.updateTemplate(id, normalizeTemplate(template));
        EnvironmentTemplateEntity savedTemplate = environmentTemplateMapper.getTemplate(id);
        if (savedTemplate != null) {
            businessLoopService.recordEvent("environment-template", "update", savedTemplate.getName(), savedTemplate.getStatus(), Map.of("course", savedTemplate.getCourse()));
        }
        return savedTemplate;
    }

    @Override
    @Transactional
    public List<EnvironmentTemplateEntity> deleteTemplate(Long id) {
        environmentTemplateMapper.deleteTemplate(id);
        businessLoopService.recordEvent("environment-template", "delete", String.valueOf(id), "已删除", Map.of("id", id));
        return environmentTemplateMapper.listTemplates();
    }

    @Override
    @Transactional
    public List<EnvironmentTemplateEntity> replaceTemplates(List<EnvironmentTemplateEntity> templates) {
        environmentTemplateMapper.deleteAllTemplates();

        List<EnvironmentTemplateEntity> nextTemplates = templates == null ? Collections.emptyList() : templates;
        for (EnvironmentTemplateEntity template : nextTemplates) {
            environmentTemplateMapper.insertTemplate(normalizeTemplate(template));
        }

        businessLoopService.recordEvent("environment-template", "batch-save", "环境模板", "已保存", Map.of("count", nextTemplates.size()));
        return environmentTemplateMapper.listTemplates();
    }

    @Override
    @Transactional
    public List<EnvironmentTemplateEntity> resetTemplates() {
        environmentTemplateMapper.deleteAllTemplates();
        List<EnvironmentTemplateEntity> templates = getDefaultTemplates();
        for (EnvironmentTemplateEntity template : templates) {
            environmentTemplateMapper.insertTemplate(template);
        }
        businessLoopService.recordEvent("environment-template", "reset", "环境模板", "已恢复初始模板", Map.of("count", templates.size()));
        return environmentTemplateMapper.listTemplates();
    }

    private EnvironmentTemplateEntity normalizeTemplate(EnvironmentTemplateEntity template) {
        EnvironmentTemplateEntity normalizedTemplate = template == null ? new EnvironmentTemplateEntity() : template;
        if (normalizedTemplate.getName() == null || normalizedTemplate.getName().isBlank()) {
            normalizedTemplate.setName("新环境模板");
        }
        if (normalizedTemplate.getCourse() == null || normalizedTemplate.getCourse().isBlank()) {
            normalizedTemplate.setCourse("通用课程");
        }
        if (normalizedTemplate.getStatus() == null || normalizedTemplate.getStatus().isBlank()) {
            normalizedTemplate.setStatus("试运行");
        }
        if (normalizedTemplate.getTagType() == null || normalizedTemplate.getTagType().isBlank()) {
            normalizedTemplate.setTagType(getTagType(normalizedTemplate.getStatus()));
        }
        return normalizedTemplate;
    }

    private String getTagType(String status) {
        if ("可复用".equals(status)) {
            return "success";
        }
        if ("待更新".equals(status)) {
            return "warning";
        }
        return "primary";
    }

    private List<EnvironmentTemplateEntity> getDefaultTemplates() {
        return List.of(
                buildTemplate("Java Web 标准环境", "Java Web 程序设计", "Windows 11", "JDK、IDEA、Tomcat、MySQL", "A101、A102", "可复用", "success"),
                buildTemplate("网络攻防隔离环境", "网络安全攻防实训", "Ubuntu/Kali", "Kali、靶场镜像、Wireshark", "B203", "待更新", "warning"),
                buildTemplate("AI GPU 训练环境", "人工智能基础", "Ubuntu 22.04", "Python、CUDA、PyTorch", "C305", "可复用", "success")
        );
    }

    private EnvironmentTemplateEntity buildTemplate(
            String name,
            String course,
            String os,
            String softwareList,
            String labs,
            String status,
            String tagType
    ) {
        EnvironmentTemplateEntity template = new EnvironmentTemplateEntity();
        template.setName(name);
        template.setCourse(course);
        template.setOs(os);
        template.setSoftwareList(softwareList);
        template.setLabs(labs);
        template.setStatus(status);
        template.setTagType(tagType);
        return template;
    }

    @Override
    public List<TeacherOptionVO> listTeachers() {
        return environmentTemplateMapper.listTeachers();
    }
}
