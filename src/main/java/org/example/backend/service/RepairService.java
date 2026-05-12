package org.example.backend.service;

import org.example.backend.entity.RepairEntity;

import java.util.List;

public interface RepairService {
    List<RepairEntity> listRepairs();

    RepairEntity createRepair(RepairEntity repair);

    RepairEntity updateRepair(String id, RepairEntity repair);

    void deleteRepair(String id);

    List<RepairEntity> replaceRepairs(List<RepairEntity> repairs);

    List<RepairEntity> resetRepairs();

    RepairEntity patchRepair(String id, RepairEntity repair, String defaultStatus, Integer defaultProgress, String defaultResult);
}
