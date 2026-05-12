package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.backend.entity.softwareEntity;

import java.util.List;

@Mapper
public interface softwareMapper {
    List<softwareEntity> getLabSoftware();

    void InserterLabSoftware(softwareEntity software);

    void updateLabSoftware(softwareEntity software);

    void deleteLabSoftware(Long id);
}
