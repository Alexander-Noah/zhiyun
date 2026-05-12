package org.example.backend.service.impl;

import org.example.backend.entity.LabEntity;
import org.example.backend.mapper.LabMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.LabService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class LabServiceImpl implements LabService {
    private final LabMapper labMapper;
    private final BusinessLoopService businessLoopService;

    public LabServiceImpl(LabMapper labMapper, BusinessLoopService businessLoopService) {
        this.labMapper = labMapper;
        this.businessLoopService = businessLoopService;
    }

    @Override
    public List<LabEntity> getLabs() {
        List<LabEntity> labs = labMapper.getLabs();
        return labs == null ? List.of() : labs;
    }

    @Override
    public LabEntity addLab(LabEntity lab) {
        if (lab.getOpenStatus() == null || lab.getOpenStatus().isBlank()) {
            lab.setOpenStatus("开放");
        }
        labMapper.addLab(lab);
        LabEntity savedLab = labMapper.getLabById(lab.getId());
        LabEntity result = savedLab == null ? lab : savedLab;
        businessLoopService.recordEvent("lab", "create", result.getLabName(), result.getOpenStatus(), Map.of(
                "labId", result.getId(),
                "capacity", result.getCapacity() == null ? 0 : result.getCapacity()
        ));
        return result;
    }

    @Override
    public Object getLabById(Integer id) {
        LabEntity lab = labMapper.getLabById(id);
        if (lab != null) {
            return lab;
        }
        return null;
    }

    @Override
    public void updateLab(Integer id, LabEntity lab) {
        labMapper.updateLab(id, lab);
        LabEntity updatedLab = labMapper.getLabById(id);
        businessLoopService.recordEvent("lab", "update", updatedLab == null ? String.valueOf(id) : updatedLab.getLabName(), updatedLab == null ? "已更新" : updatedLab.getOpenStatus(), Map.of(
                "labId", id
        ));
    }

    @Override
    public List<LabEntity> updateLabs(List<LabEntity> labs) {
        if (labs == null || labs.isEmpty()) {
            return getLabs();
        }

        for (LabEntity lab : labs) {
            if (lab.getId() != null) {
                updateLab(lab.getId(), lab);
            }
        }

        return getLabs();
    }

    @Override
    @Transactional
    public void deleteLab(Integer id) {
        LabEntity lab = labMapper.getLabById(id);
        if (lab == null) {
            throw new IllegalArgumentException("实验室不存在或已删除");
        }

        labMapper.deleteRepairsByLabId(id);
        labMapper.deleteReservationsByLabId(id);
        labMapper.deleteCourseEnvironmentsByLabId(id);
        labMapper.deleteLabSoftwareByLabId(id);
        labMapper.deleteDevicesByLabId(id);
        int deletedRows = labMapper.deleteLab(id);

        if (deletedRows == 0) {
            throw new IllegalArgumentException("实验室不存在或已删除");
        }

        businessLoopService.recordEvent("lab", "delete", lab.getLabName(), "已删除", Map.of(
                "labId", id
        ));
    }
}
