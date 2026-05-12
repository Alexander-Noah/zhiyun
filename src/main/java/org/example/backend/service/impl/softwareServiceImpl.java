package org.example.backend.service.impl;

import org.example.backend.result.Result;
import org.example.backend.entity.softwareEntity;
import org.example.backend.mapper.softwareMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.softwareService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class softwareServiceImpl implements softwareService {
    private final softwareMapper softwareMapper;
    private final BusinessLoopService businessLoopService;

    public softwareServiceImpl(softwareMapper softwareMapper, BusinessLoopService businessLoopService) {
        this.softwareMapper = softwareMapper;
        this.businessLoopService = businessLoopService;
    }

    @Override
    public Result getLabSoftware() {
        return Result.success("获取软件环境列表成功", softwareMapper.getLabSoftware());
    }

    @Override
    public Result InserterLabSoftware(softwareEntity software) {
        normalizeSoftware(software);
        softwareMapper.InserterLabSoftware(software);
        recordSoftwareEvent(software, "create");
        return Result.success("添加软件环境成功", software);
    }

    @Override
    public Result updateLabSoftware(Long id, softwareEntity software) {
        software.setId(id);
        normalizeSoftware(software);
        softwareMapper.updateLabSoftware(software);
        recordSoftwareEvent(software, "update");
        return Result.success("修改软件环境成功", software);
    }

    @Override
    public Result deleteLabSoftware(Long id) {
        softwareMapper.deleteLabSoftware(id);
        businessLoopService.recordEvent("software", "delete", String.valueOf(id), "已删除", Map.of("id", id));
        return Result.success("删除软件环境成功");
    }

    private void normalizeSoftware(softwareEntity software) {
        if (software.getSoftwareName() == null || software.getSoftwareName().isBlank()) {
            software.setSoftwareName(software.getName());
        }
        if (software.getName() == null || software.getName().isBlank()) {
            software.setName(software.getSoftwareName());
        }
        if (software.getSoftwareVersion() == null || software.getSoftwareVersion().isBlank()) {
            software.setSoftwareVersion(software.getVersion());
        }
        if (software.getVersion() == null || software.getVersion().isBlank()) {
            software.setVersion(software.getSoftwareVersion());
        }
        if (software.getLicenseInfo() == null || software.getLicenseInfo().isBlank()) {
            software.setLicenseInfo(software.getLicense());
        }
        if (software.getLicense() == null || software.getLicense().isBlank()) {
            software.setLicense(software.getLicenseInfo());
        }
        if (software.getRemark() == null || software.getRemark().isBlank()) {
            software.setRemark(software.getType());
        }
        if (software.getStatus() == null || software.getStatus().isBlank()) {
            software.setStatus(software.getInstallTime() == null ? "待安装" : "已安装");
        }
        if (software.getInstallTime() == null && "已安装".equals(software.getStatus())) {
            software.setInstallTime(LocalDateTime.now());
        }
    }

    private void recordSoftwareEvent(softwareEntity software, String action) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", software.getId());
        details.put("lab", software.getLab());
        details.put("version", software.getSoftwareVersion());
        details.put("license", software.getLicenseInfo());
        businessLoopService.recordEvent("software", action, software.getSoftwareName(), firstNonBlank(software.getStatus(), "已安装"), details);
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
