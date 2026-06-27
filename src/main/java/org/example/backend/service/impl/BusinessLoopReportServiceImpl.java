package org.example.backend.service.impl;

import org.example.backend.mapper.BusinessLoopReportMapper;
import org.example.backend.service.BusinessLoopReportService;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BusinessLoopReportServiceImpl implements BusinessLoopReportService {

    private static final TypeReference<Map<String, Object>> EVENT_TYPE = new TypeReference<>() {
    };

    private final BusinessLoopReportMapper businessLoopReportMapper;
    private final ObjectMapper objectMapper;

    public BusinessLoopReportServiceImpl(
            BusinessLoopReportMapper businessLoopReportMapper,
            ObjectMapper objectMapper
    ) {
        this.businessLoopReportMapper = businessLoopReportMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> getOverview() {
        long reservationTotal = businessLoopReportMapper.countReservations();
        long approvedReservations = businessLoopReportMapper.countApprovedReservations();
        long pendingReservations = businessLoopReportMapper.countPendingReservations();
        long usageTotal = businessLoopReportMapper.countUsageRecords();
        long usageFromReservation = businessLoopReportMapper.countUsageRecordsFromReservations();
        long abnormalUsage = businessLoopReportMapper.countAbnormalUsageRecords();
        long repairTotal = businessLoopReportMapper.countRepairOrders();
        long repairFromUsage = businessLoopReportMapper.countRepairOrdersFromUsageRecords();
        long activeRepairs = businessLoopReportMapper.countActiveRepairOrders();
        long closedRepairs = businessLoopReportMapper.countClosedRepairOrders();
        long deviceTotal = businessLoopReportMapper.countDevices();
        long normalDevices = businessLoopReportMapper.countNormalDevices();
        long maintenanceDevices = businessLoopReportMapper.countMaintenanceDevices();
        long faultDevices = businessLoopReportMapper.countFaultDevices();
        long environmentRequests = businessLoopReportMapper.countCourseEnvironmentRequests();
        long confirmedEnvironmentRequests = businessLoopReportMapper.countConfirmedCourseEnvironmentRequests();
        long environmentTemplates = businessLoopReportMapper.countEnvironmentTemplates();
        long consumables = businessLoopReportMapper.countConsumables();
        long lowStockConsumables = businessLoopReportMapper.countLowStockConsumables();
        long notices = businessLoopReportMapper.countNotices();
        long publishedNotices = businessLoopReportMapper.countPublishedNotices();
        long eventTotal = businessLoopReportMapper.countOperationEvents();

        List<Map<String, Object>> latestEvents = readLatestEvents();
        Map<String, Long> eventCategories = countEventCategories(latestEvents);

        List<Map<String, Object>> chains = List.of(
                chain("reservation-usage", "预约审核到使用记录", approvedReservations, usageFromReservation, pendingReservations, "预约管理"),
                chain("usage-repair", "使用异常到报修工单", abnormalUsage, repairFromUsage, Math.max(0, abnormalUsage - repairFromUsage), "使用记录"),
                chain("repair-device", "报修处理到设备状态", repairTotal, closedRepairs, activeRepairs, "故障报修"),
                chain("environment-template", "课程环境到模板沉淀", environmentRequests, confirmedEnvironmentRequests, Math.max(0, environmentRequests - confirmedEnvironmentRequests), "环境管理"),
                chain("consumable-notice", "库存预警到通知公告", consumables, Math.max(0, consumables - lowStockConsumables), lowStockConsumables, "耗材库存"),
                chain("operation-event", "全模块操作到事件留痕", eventTotal, eventTotal, 0, "统一事件流水")
        );

        List<Map<String, Object>> cards = List.of(
                card("预约闭环率", rate(usageFromReservation, approvedReservations), approvedReservations, usageFromReservation, "审批后自动生成使用记录"),
                card("异常闭环率", rate(repairFromUsage, abnormalUsage), abnormalUsage, repairFromUsage, "异常使用自动生成工单"),
                card("报修闭环率", rate(closedRepairs, repairTotal), repairTotal, closedRepairs, "工单完成后同步设备状态"),
                card("设备健康率", rate(normalDevices, deviceTotal), deviceTotal, normalDevices, "故障和维护设备待处理"),
                card("环境沉淀率", rate(confirmedEnvironmentRequests, environmentRequests), environmentRequests, confirmedEnvironmentRequests, "课程需求确认后形成模板"),
                card("通知发布率", rate(publishedNotices, notices), notices, publishedNotices, "闭环动作可通过公告触达")
        );

        List<Map<String, Object>> actions = new ArrayList<>();
        addAction(actions, pendingReservations, "待审核预约", "处理预约审核后将自动写入使用记录", "/lab-admin/reservations");
        addAction(actions, Math.max(0, abnormalUsage - repairFromUsage), "异常使用未生成工单", "检查使用记录异常复核结果", "/lab-admin/records");
        addAction(actions, activeRepairs, "处理中报修工单", "完成工单后设备状态会回到健康台账", "/lab-admin/repairs");
        addAction(actions, faultDevices + maintenanceDevices, "非正常设备", "结合报修和物联网状态排查设备", "/lab-admin/devices");
        addAction(actions, lowStockConsumables, "低库存耗材", "补货后更新库存并完成预警闭环", "/lab-admin/consumables");

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("cards", cards);
        overview.put("chains", chains);
        overview.put("actions", actions);
        overview.put("latestEvents", latestEvents);
        overview.put("eventCategories", eventCategories);
        overview.put("usageTrend", businessLoopReportMapper.listUsageTrend());
        overview.put("labUsageRanking", businessLoopReportMapper.listLabUsageRanking());
        overview.put("repairFaultRanking", businessLoopReportMapper.listRepairFaultRanking());
        overview.put("totals", totals(
                reservationTotal,
                usageTotal,
                repairTotal,
                deviceTotal,
                environmentTemplates,
                consumables,
                notices,
                eventTotal
        ));

        return overview;
    }

    private Map<String, Object> totals(
            long reservationTotal,
            long usageTotal,
            long repairTotal,
            long deviceTotal,
            long environmentTemplates,
            long consumables,
            long notices,
            long eventTotal
    ) {
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("reservations", reservationTotal);
        totals.put("usageRecords", usageTotal);
        totals.put("repairs", repairTotal);
        totals.put("devices", deviceTotal);
        totals.put("environmentTemplates", environmentTemplates);
        totals.put("consumables", consumables);
        totals.put("notices", notices);
        totals.put("events", eventTotal);
        return totals;
    }

    private Map<String, Object> chain(
            String key,
            String title,
            long total,
            long completed,
            long pending,
            String owner
    ) {
        Map<String, Object> chain = new LinkedHashMap<>();
        chain.put("key", key);
        chain.put("title", title);
        chain.put("owner", owner);
        chain.put("total", total);
        chain.put("completed", Math.min(completed, total));
        chain.put("pending", pending);
        chain.put("rate", rate(completed, total));
        chain.put("tagType", pending > 0 ? "warning" : "success");
        chain.put("status", pending > 0 ? "待处理" : "已闭环");
        return chain;
    }

    private Map<String, Object> card(
            String label,
            String value,
            long total,
            long completed,
            String trend
    ) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("label", label);
        card.put("value", value);
        card.put("total", total);
        card.put("completed", Math.min(completed, total));
        card.put("trend", trend);
        return card;
    }

    private void addAction(
            List<Map<String, Object>> actions,
            long count,
            String title,
            String description,
            String path
    ) {
        if (count <= 0) {
            return;
        }

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("title", title);
        action.put("count", count);
        action.put("description", description);
        action.put("path", path);
        actions.add(action);
    }

    private String rate(long completed, long total) {
        if (total <= 0) {
            return "100%";
        }

        long safeCompleted = Math.max(0, Math.min(completed, total));
        return Math.round((safeCompleted * 100.0) / total) + "%";
    }

    private List<Map<String, Object>> readLatestEvents() {
        List<Map<String, Object>> events = new ArrayList<>();

        for (String eventJson : businessLoopReportMapper.listLatestEventJson(30)) {
            try {
                events.add(objectMapper.readValue(eventJson, EVENT_TYPE));
            } catch (Exception ignored) {
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("category", "unknown");
                fallback.put("subject", eventJson);
                events.add(fallback);
            }
        }

        return events;
    }

    private Map<String, Long> countEventCategories(List<Map<String, Object>> events) {
        Map<String, Long> counts = new LinkedHashMap<>();

        for (Map<String, Object> event : events) {
            String category = String.valueOf(event.getOrDefault("category", "unknown"));
            counts.put(category, counts.getOrDefault(category, 0L) + 1);
        }

        return counts;
    }
}