package org.example.backend.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.entity.LabEntity;
import org.example.backend.mapper.DashboardMapper;
import org.example.backend.service.BusinessLoopReportService;
import org.example.backend.service.DashboardService;
import org.example.backend.service.LabService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DashboardServiceImpl implements DashboardService {
    private final DashboardMapper dashboardMapper;
    private final BusinessLoopReportService businessLoopReportService;
    private final LabService labService;

    public DashboardServiceImpl(
            DashboardMapper dashboardMapper,
            BusinessLoopReportService businessLoopReportService,
            LabService labService
    ) {
        this.dashboardMapper = dashboardMapper;
        this.businessLoopReportService = businessLoopReportService;
        this.labService = labService;
    }

    @Override
    public Map<String, Object> getOverview(Integer managerUserId) {
        Map<String, Object> overview = new LinkedHashMap<>();
        List<String> loadWarnings = new ArrayList<>();
        List<LabEntity> scopedLabs = safeLabList("实验室资源", () -> labService.getLabs(managerUserId), loadWarnings);
        Set<Long> activeLabIds = safeLongList("当前使用实验室", () -> dashboardMapper.listActiveLabIds(managerUserId), loadWarnings)
                .stream()
                .collect(Collectors.toSet());
        List<Map<String, Object>> rawTodaySchedules = safeList("今日安排", () -> dashboardMapper.listTodaySchedules(managerUserId), loadWarnings);
        List<Map<String, Object>> labTodaySchedules = buildTodaySchedules(scopedLabs, rawTodaySchedules, activeLabIds);

        overview.put("stats", buildStats(managerUserId, scopedLabs, activeLabIds, labTodaySchedules, loadWarnings));
        overview.put("shortcuts", buildShortcuts());
        putList(overview, "labUsage", "实验室使用概览", () -> buildLabUsage(scopedLabs, activeLabIds), loadWarnings);
        putList(overview, "deviceStatus", "设备状态", () -> dashboardMapper.listDeviceStatus(managerUserId), loadWarnings);
        putList(overview, "notices", "通知公告", dashboardMapper::listNotices, loadWarnings);
        putList(overview, "doorAlerts", "开门提醒", () -> dashboardMapper.listDoorAlerts(managerUserId), loadWarnings);
        overview.put("todaySchedules", labTodaySchedules);
        putList(overview, "todoItems", "待处理事项", () -> dashboardMapper.listTodoItems(managerUserId), loadWarnings);
        putList(overview, "recordReminders", "使用记录提醒", () -> dashboardMapper.listRecordReminders(managerUserId), loadWarnings);
        putList(overview, "recentActivities", "最近动态", () -> dashboardMapper.listRecentActivities(managerUserId), loadWarnings);
        overview.put("businessLoop", safeMap("业务闭环", businessLoopReportService::getOverview, loadWarnings));
        overview.put("loadWarnings", loadWarnings);
        return overview;
    }

    private List<Map<String, Object>> buildStats(
            Integer managerUserId,
            List<LabEntity> scopedLabs,
            Set<Long> activeLabIds,
            List<Map<String, Object>> labTodaySchedules,
            List<String> loadWarnings
    ) {
        long labCount = scopedLabs.size();
        long openLabs = scopedLabs.stream().filter(lab -> isOpenLab(lab, activeLabIds)).count();
        long usingLabs = scopedLabs.stream().filter(lab -> isUsingLab(lab, activeLabIds)).count();
        long deviceCount = safeLong("设备总数", () -> dashboardMapper.countDevices(managerUserId), loadWarnings);
        long onlineDevices = safeLong("在线设备总数", () -> dashboardMapper.countOnlineDevices(managerUserId), loadWarnings);
        long abnormalDevices = safeLong("异常设备总数", () -> dashboardMapper.countAbnormalDevices(managerUserId), loadWarnings);
        long pendingReservations = safeLong("待审批预约总数", () -> dashboardMapper.countPendingReservations(managerUserId), loadWarnings);
        long todayReservations = safeLong("今日预约总数", () -> dashboardMapper.countTodayReservations(managerUserId), loadWarnings);
        long approvedTodayReservations = safeLong("今日已通过预约总数", () -> dashboardMapper.countApprovedTodayReservations(managerUserId), loadWarnings);
        long activeRepairs = safeLong("处理中报修总数", () -> dashboardMapper.countActiveRepairs(managerUserId), loadWarnings);
        long highPriorityRepairs = safeLong("高优先级报修总数", () -> dashboardMapper.countHighPriorityRepairs(managerUserId), loadWarnings);
        long scheduledLabs = countScheduledLabs(labTodaySchedules);

        List<Map<String, Object>> stats = new ArrayList<>();
        stats.add(metric("实验室", labCount, "间", "lab", "#2563eb", "开放 " + openLabs + " 间"));
        stats.add(metric("当前使用实验室", usingLabs, "间", "reservation", "#0f766e", "今日预约 " + todayReservations + " 条"));
        stats.add(metric("今日有课实验室", scheduledLabs, "间", "record", "#7c3aed", "当前使用 " + usingLabs + " 间"));
        stats.add(metric("实验室预约", approvedTodayReservations, "条", "calendar", "#ea580c", "待审批 " + pendingReservations + " 条"));
        stats.add(metric("实验室待处理", pendingReservations + activeRepairs, "项", "todo", "#dc2626", "高优先级 " + highPriorityRepairs + " 项"));
        stats.add(metric("实验室设备", deviceCount, "台", "device", "#b45309", "异常 " + abnormalDevices + " 台，在线 " + onlineDevices + " 台"));
        return stats;
    }

    private List<Map<String, Object>> buildTodaySchedules(
            List<LabEntity> scopedLabs,
            List<Map<String, Object>> rawTodaySchedules,
            Set<Long> activeLabIds
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LabEntity lab : scopedLabs) {
            List<Map<String, Object>> matchedSchedules = rawTodaySchedules.stream()
                    .filter(schedule -> isScheduleForLab(schedule, lab))
                    .sorted(Comparator.comparing(schedule -> String.valueOf(schedule.getOrDefault("time", ""))))
                    .map(schedule -> buildTodayScheduleRow(lab, schedule))
                    .toList();
            if (matchedSchedules.isEmpty()) {
                rows.add(buildEmptyTodayScheduleRow(lab, activeLabIds));
            } else {
                rows.addAll(matchedSchedules);
            }
        }
        return rows;
    }

    private Map<String, Object> buildTodayScheduleRow(LabEntity lab, Map<String, Object> schedule) {
        Map<String, Object> row = new LinkedHashMap<>(schedule);
        row.put("labId", lab.getId());
        row.put("lab", resolveLabDisplay(lab));
        return row;
    }

    private Map<String, Object> buildEmptyTodayScheduleRow(LabEntity lab, Set<Long> activeLabIds) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("labId", lab.getId());
        row.put("time", "-");
        row.put("lab", resolveLabDisplay(lab));
        row.put("course", "今日暂无课程");
        row.put("className", "-");
        row.put("teacher", "-");
        row.put("status", isActiveLab(lab, activeLabIds) ? "使用中" : normalizeLabStatus(lab));
        row.put("statusType", isActiveLab(lab, activeLabIds) ? "success" : resolveStatusType(normalizeLabStatus(lab)));
        return row;
    }

    private boolean isScheduleForLab(Map<String, Object> schedule, LabEntity lab) {
        Long scheduleLabId = toLong(schedule.get("labId"));
        if (scheduleLabId != null && lab.getId() != null && scheduleLabId.equals(Long.valueOf(lab.getId()))) {
            return true;
        }
        String scheduleLab = normalizeText(schedule.get("lab"));
        String scheduleClassroom = normalizeText(schedule.get("classroom"));
        return isSameText(scheduleLab, lab.getLabCode())
                || isSameText(scheduleLab, lab.getLabName())
                || isSameText(scheduleLab, lab.getRoomNo())
                || containsText(scheduleClassroom, lab.getLabName())
                || containsText(scheduleClassroom, lab.getLabCode())
                || containsText(scheduleClassroom, lab.getRoomNo());
    }

    private boolean hasScheduleCourse(Map<String, Object> row) {
        return !"今日暂无课程".equals(String.valueOf(row.get("course")));
    }

    private long countScheduledLabs(List<Map<String, Object>> rows) {
        return rows.stream()
                .filter(this::hasScheduleCourse)
                .map(row -> firstNonBlank(row.get("labId"), row.get("lab")))
                .filter(value -> !value.isBlank())
                .distinct()
                .count();
    }

    private String resolveLabDisplay(LabEntity lab) {
        return firstNonBlank(lab.getLabCode(), lab.getLabName(), lab.getRoomNo(), "LAB-" + lab.getId());
    }

    private String resolveStatusType(String status) {
        if ("使用中".equals(status) || "占用中".equals(status) || "开放".equals(status)) return "success";
        if ("预约中".equals(status) || "待预约".equals(status) || "维护中".equals(status)) return "warning";
        if ("停用".equals(status)) return "info";
        return "primary";
    }

    private boolean isSameText(String left, Object right) {
        String rightText = normalizeText(right);
        return !left.isBlank() && !rightText.isBlank() && left.equals(rightText);
    }

    private boolean containsText(String left, Object right) {
        String rightText = normalizeText(right);
        return !left.isBlank() && !rightText.isBlank() && left.contains(rightText);
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toLowerCase().replaceAll("\\s+", "");
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<Map<String, Object>> buildLabUsage(List<LabEntity> scopedLabs, Set<Long> activeLabIds) {
        return scopedLabs.stream()
                .map(lab -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", firstNonBlank(lab.getLabCode(), lab.getLabName(), "LAB-" + lab.getId()));
                    item.put("value", resolveLabUsageValue(lab, activeLabIds));
                    return item;
                })
                .sorted((left, right) -> Long.compare(
                        Number.class.cast(right.get("value")).longValue(),
                        Number.class.cast(left.get("value")).longValue()
                ))
                .limit(8)
                .toList();
    }

    private boolean isOpenLab(LabEntity lab, Set<Long> activeLabIds) {
        String status = normalizeLabStatus(lab);
        return !isLockedLab(status) && !isActiveLab(lab, activeLabIds);
    }

    private boolean isUsingLab(LabEntity lab, Set<Long> activeLabIds) {
        String status = normalizeLabStatus(lab);
        return !isLockedLab(status) && isActiveLab(lab, activeLabIds);
    }

    private int resolveLabUsageValue(LabEntity lab, Set<Long> activeLabIds) {
        String status = normalizeLabStatus(lab);
        if (!isLockedLab(status) && isActiveLab(lab, activeLabIds)) {
            return 88;
        }
        if (isLockedLab(status)) {
            return 12;
        }
        return 35;
    }

    private boolean isLockedLab(String status) {
        return List.of("维护中", "停用").contains(status);
    }

    private boolean isActiveLab(LabEntity lab, Set<Long> activeLabIds) {
        if (lab == null || lab.getId() == null) {
            return false;
        }
        return activeLabIds.contains(Long.valueOf(lab.getId()));
    }

    private String normalizeLabStatus(LabEntity lab) {
        return firstNonBlank(lab == null ? null : lab.getOpenStatus(), "开放");
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            String text = value == null ? "" : String.valueOf(value).trim();
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private Map<String, Object> metric(String title, long value, String unit, String iconKey, String color, String trend) {
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("title", title);
        metric.put("value", value);
        metric.put("unit", unit);
        metric.put("iconKey", iconKey);
        metric.put("color", color);
        metric.put("trend", trend);
        return metric;
    }

    private List<Map<String, Object>> buildShortcuts() {
        List<Map<String, Object>> shortcuts = new ArrayList<>();
        shortcuts.add(shortcut("设备资产", "资产台账与设备状态", "/lab-admin/devices", "device"));
        shortcuts.add(shortcut("设备状态", "在线、故障与维护监测", "/lab-admin/device-status", "normal"));
        shortcuts.add(shortcut("预约审批", "课程与自主预约审核", "/lab-admin/reservations", "reservation"));
        shortcuts.add(shortcut("故障报修", "故障流转与维修跟踪", "/lab-admin/repairs", "repair"));
        shortcuts.add(shortcut("使用记录", "上机、借用、巡检记录", "/lab-admin/records", "record"));
        shortcuts.add(shortcut("统计分析", "运行指标与趋势分析", "/lab-admin/statistics", "statistics"));
        return shortcuts;
    }

    private Map<String, Object> shortcut(String title, String description, String path, String iconKey) {
        Map<String, Object> shortcut = new LinkedHashMap<>();
        shortcut.put("title", title);
        shortcut.put("description", description);
        shortcut.put("path", path);
        shortcut.put("iconKey", iconKey);
        return shortcut;
    }

    private void putList(
            Map<String, Object> overview,
            String key,
            String label,
            Supplier<List<Map<String, Object>>> supplier,
            List<String> loadWarnings
    ) {
        overview.put(key, safeList(label, supplier, loadWarnings));
    }

    private long safeLong(String label, LongSupplier supplier, List<String> loadWarnings) {
        try {
            return supplier.getAsLong();
        } catch (RuntimeException exception) {
            recordLoadWarning(label, loadWarnings, exception);
            return 0L;
        }
    }

    private List<Map<String, Object>> safeList(
            String label,
            Supplier<List<Map<String, Object>>> supplier,
            List<String> loadWarnings
    ) {
        try {
            return safeList(supplier.get());
        } catch (RuntimeException exception) {
            recordLoadWarning(label, loadWarnings, exception);
            return List.of();
        }
    }

    private List<LabEntity> safeLabList(
            String label,
            Supplier<List<LabEntity>> supplier,
            List<String> loadWarnings
    ) {
        try {
            List<LabEntity> source = supplier.get();
            return source == null ? List.of() : source;
        } catch (RuntimeException exception) {
            recordLoadWarning(label, loadWarnings, exception);
            return List.of();
        }
    }

    private List<Long> safeLongList(
            String label,
            Supplier<List<Long>> supplier,
            List<String> loadWarnings
    ) {
        try {
            List<Long> source = supplier.get();
            return source == null ? List.of() : source;
        } catch (RuntimeException exception) {
            recordLoadWarning(label, loadWarnings, exception);
            return List.of();
        }
    }

    private Map<String, Object> safeMap(
            String label,
            Supplier<Map<String, Object>> supplier,
            List<String> loadWarnings
    ) {
        try {
            Map<String, Object> source = supplier.get();
            return source == null ? Map.of() : source;
        } catch (RuntimeException exception) {
            recordLoadWarning(label, loadWarnings, exception);
            return Map.of();
        }
    }

    private void recordLoadWarning(String label, List<String> loadWarnings, RuntimeException exception) {
        loadWarnings.add(label);
        log.warn("dashboard overview section failed section={}", label, exception);
    }

    private List<Map<String, Object>> safeList(List<Map<String, Object>> source) {
        return source == null ? List.of() : source;
    }
}
