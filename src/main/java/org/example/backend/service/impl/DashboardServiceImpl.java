package org.example.backend.service.impl;

import org.example.backend.mapper.DashboardMapper;
import org.example.backend.service.BusinessLoopReportService;
import org.example.backend.service.DashboardService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DashboardServiceImpl implements DashboardService {
    private final DashboardMapper dashboardMapper;
    private final BusinessLoopReportService businessLoopReportService;

    public DashboardServiceImpl(DashboardMapper dashboardMapper, BusinessLoopReportService businessLoopReportService) {
        this.dashboardMapper = dashboardMapper;
        this.businessLoopReportService = businessLoopReportService;
    }

    @Override
    public Map<String, Object> getOverview() {
        List<Map<String, Object>> stats = List.of(
                Map.of("label", "实验室总数", "value", String.valueOf(dashboardMapper.countLabs()), "trend", "空间资源"),
                Map.of("label", "设备资产", "value", String.valueOf(dashboardMapper.countDevices()), "trend", "资产台账"),
                Map.of("label", "待审预约", "value", String.valueOf(dashboardMapper.countPendingReservations()), "trend", "预约审核"),
                Map.of("label", "处理中工单", "value", String.valueOf(dashboardMapper.countActiveRepairs()), "trend", "维修跟踪")
        );

        List<Map<String, Object>> shortcuts = List.of(
                Map.of("title", "实验室管理", "description", "空间、容量、开放状态", "path", "/labs", "iconKey", "lab"),
                Map.of("title", "设备管理", "description", "资产台账与设备状态", "path", "/devices", "iconKey", "device"),
                Map.of("title", "预约管理", "description", "课程与自主预约审核", "path", "/reservations", "iconKey", "reservation"),
                Map.of("title", "报修管理", "description", "故障流转与维修跟踪", "path", "/repairs", "iconKey", "repair")
        );

        return Map.of(
                "stats", stats,
                "shortcuts", shortcuts,
                "labUsage", dashboardMapper.listLabUsage(),
                "deviceStatus", dashboardMapper.listDeviceStatus(),
                "notices", dashboardMapper.listNotices(),
                "businessLoop", businessLoopReportService.getOverview()
        );
    }
}
