package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.backend.VO.TeacherOptionVO;
import org.example.backend.entity.EnvironmentTemplateEntity;

import java.util.List;

@Mapper
public interface EnvironmentTemplateMapper {
     List<TeacherOptionVO> listTeachers();

    List<EnvironmentTemplateEntity> listTemplates();

    EnvironmentTemplateEntity getTemplate(@Param("id") Long id);

    EnvironmentTemplateEntity getTemplateByCourse(@Param("course") String course);

    int insertTemplate(EnvironmentTemplateEntity template);

    int updateTemplate(@Param("id") Long id, @Param("template") EnvironmentTemplateEntity template);

    int deleteTemplate(@Param("id") Long id);

    int deleteAllTemplates();


}
