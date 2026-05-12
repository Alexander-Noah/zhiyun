package org.example.backend.service;

import org.example.backend.VO.TeacherOptionVO;
import org.example.backend.entity.EnvironmentTemplateEntity;

import java.util.List;

public interface EnvironmentTemplateService {
    List<EnvironmentTemplateEntity> listTemplates();

    EnvironmentTemplateEntity createTemplate(EnvironmentTemplateEntity template);

    EnvironmentTemplateEntity updateTemplate(Long id, EnvironmentTemplateEntity template);

    List<EnvironmentTemplateEntity> deleteTemplate(Long id);

    List<EnvironmentTemplateEntity> replaceTemplates(List<EnvironmentTemplateEntity> templates);

    List<EnvironmentTemplateEntity> resetTemplates();

    List<TeacherOptionVO> listTeachers();

}
