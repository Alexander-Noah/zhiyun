package org.example.backend.service.impl;

import org.example.backend.entity.RepairEntity;
import org.example.backend.mapper.RepairMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.RepairService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RepairServiceImpl implements RepairService {
    private static final DateTimeFormatter TICKET_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String STATUS_WAIT_DISPATCH = "\u5f85\u6d3e\u5355";
    private static final String STATUS_UPDATED = "\u5df2\u66f4\u65b0";
    private static final String STATUS_DELETED = "\u5df2\u5220\u9664";
    private static final String ASSIGNEE_PENDING = "\u5f85\u5206\u914d";
    private static final String RESULT_PENDING = "\u5f85\u7ef4\u4fee\u4eba\u5458\u63a5\u5355\u3002";

    private final RepairMapper repairMapper;
    private final BusinessLoopService businessLoopService;

    public RepairServiceImpl(RepairMapper repairMapper, BusinessLoopService businessLoopService) {
        this.repairMapper = repairMapper;
        this.businessLoopService = businessLoopService;
    }

    @Override
    public List<RepairEntity> listRepairs() {
        List<RepairEntity> repairs = repairMapper.listRepairs();
        return repairs == null ? List.of() : repairs;
    }

    @Override
    @Transactional
    public RepairEntity createRepair(RepairEntity repair) {
        normalizeRepair(repair);
        repairMapper.insertRepair(repair);
        RepairEntity savedRepair = repairMapper.getRepair(repair.getTicket());
        recordRepairEvent("create", savedRepair);
        businessLoopService.syncDeviceAfterRepairChanged(savedRepair);
        return savedRepair;
    }

    @Override
    @Transactional
    public RepairEntity updateRepair(String id, RepairEntity repair) {
        normalizeRepair(repair);
        int updatedCount = repairMapper.updateRepair(id, repair);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("repair not found");
        }
        RepairEntity savedRepair = repairMapper.getRepair(id);
        recordRepairEvent("update", savedRepair);
        businessLoopService.syncDeviceAfterRepairChanged(savedRepair);
        return savedRepair;
    }

    @Override
    @Transactional
    public void deleteRepair(String id) {
        RepairEntity repair = repairMapper.getRepair(id);
        repairMapper.deleteRepair(id);
        businessLoopService.recordEvent("repair", "delete", repair == null ? id : firstNonBlank(repair.getTicket(), repair.getId()), STATUS_DELETED, Map.of("id", id));
    }

    @Override
    @Transactional
    public List<RepairEntity> replaceRepairs(List<RepairEntity> repairs) {
        if (repairs == null || repairs.isEmpty()) {
            return listRepairs();
        }

        repairMapper.deleteAllRepairs();
        for (RepairEntity repair : repairs) {
            normalizeRepair(repair);
            repairMapper.insertRepair(repair);
            RepairEntity savedRepair = repairMapper.getRepair(repair.getTicket());
            recordRepairEvent("batch-save", savedRepair);
            businessLoopService.syncDeviceAfterRepairChanged(savedRepair);
        }
        return listRepairs();
    }

    @Override
    public List<RepairEntity> resetRepairs() {
        businessLoopService.recordEvent("repair", "reset", "\u62a5\u4fee\u5de5\u5355", "\u5df2\u6062\u590d\u5f53\u524d\u53f0\u8d26", Map.of("count", listRepairs().size()));
        return listRepairs();
    }

    @Override
    @Transactional
    public RepairEntity patchRepair(String id, RepairEntity repair, String defaultStatus, Integer defaultProgress, String defaultResult) {
        RepairEntity patch = repair == null ? new RepairEntity() : repair;
        if (patch.getStatus() == null || patch.getStatus().isBlank()) {
            patch.setStatus(defaultStatus);
        }
        if (patch.getProgress() == null) {
            patch.setProgress(defaultProgress);
        }
        if (patch.getResult() == null || patch.getResult().isBlank()) {
            patch.setResult(defaultResult);
        }
        int updatedCount = repairMapper.patchRepair(id, patch);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("repair not found");
        }
        RepairEntity savedRepair = repairMapper.getRepair(id);
        recordRepairEvent("status", savedRepair);
        businessLoopService.syncDeviceAfterRepairChanged(savedRepair);
        return savedRepair;
    }

    private void normalizeRepair(RepairEntity repair) {
        if (repair.getTicket() == null || repair.getTicket().isBlank()) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
            repair.setTicket("R" + LocalDate.now().format(TICKET_DATE) + suffix);
        }
        if (repair.getStatus() == null || repair.getStatus().isBlank()) {
            repair.setStatus(STATUS_WAIT_DISPATCH);
        }
        if (repair.getAssignee() == null || repair.getAssignee().isBlank()) {
            repair.setAssignee(ASSIGNEE_PENDING);
        }
        if (repair.getProgress() == null) {
            repair.setProgress(8);
        }
        if (repair.getResult() == null || repair.getResult().isBlank()) {
            repair.setResult(RESULT_PENDING);
        }
    }

    private void recordRepairEvent(String action, RepairEntity repair) {
        if (repair == null) {
            return;
        }
        businessLoopService.recordEvent("repair", action, firstNonBlank(repair.getTicket(), repair.getId()), firstNonBlank(repair.getStatus(), STATUS_UPDATED), Map.of(
                "device", firstNonBlank(repair.getDevice(), "-"),
                "lab", firstNonBlank(repair.getLab(), "-"),
                "progress", repair.getProgress() == null ? 0 : repair.getProgress()
        ));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
