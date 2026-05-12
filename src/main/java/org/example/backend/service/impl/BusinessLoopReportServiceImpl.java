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

    public BusinessLoopReportServiceImpl(BusinessLoopReportMapper businessLoopReportMapper, ObjectMapper objectMapper) {
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
                chain("reservation-usage", "\u9884\u7ea6\u5ba1\u6838\u5230\u4f7f\u7528\u8bb0\u5f55", approvedReservations, usageFromReservation, pendingReservations, "\u9884\u7ea6\u7ba1\u7406"),
                chain("usage-repair", "\u4f7f\u7528\u5f02\u5e38\u5230\u62a5\u4fee\u5de5\u5355", abnormalUsage, repairFromUsage, Math.max(0, abnormalUsage - repairFromUsage), "\u4f7f\u7528\u8bb0\u5f55"),
                chain("repair-device", "\u62a5\u4fee\u5904\u7406\u5230\u8bbe\u5907\u72b6\u6001", repairTotal, closedRepairs, activeRepairs, "\u6545\u969c\u62a5\u4fee"),
                chain("environment-template", "\u8bfe\u7a0b\u73af\u5883\u5230\u6a21\u677f\u6c89\u6dc0", environmentRequests, confirmedEnvironmentRequests, Math.max(0, environmentRequests - confirmedEnvironmentRequests), "\u73af\u5883\u7ba1\u7406"),
                chain("consumable-notice", "\u5e93\u5b58\u9884\u8b66\u5230\u901a\u77e5\u516c\u544a", consumables, Math.max(0, consumables - lowStockConsumables), lowStockConsumables, "\u8017\u6750\u5e93\u5b58"),
                chain("operation-event", "\u5168\u6a21\u5757\u64cd\u4f5c\u5230\u4e8b\u4ef6\u7559\u75d5", eventTotal, eventTotal, 0, "\u7edf\u4e00\u4e8b\u4ef6\u6d41\u6c34")
        );

        List<Map<String, Object>> cards = List.of(
                card("\u9884\u7ea6\u95ed\u73af\u7387", rate(usageFromReservation, approvedReservations), approvedReservations, usageFromReservation, "\u5ba1\u6279\u540e\u81ea\u52a8\u751f\u6210\u4f7f\u7528\u8bb0\u5f55"),
                card("\u5f02\u5e38\u95ed\u73af\u7387", rate(repairFromUsage, abnormalUsage), abnormalUsage, repairFromUsage, "\u5f02\u5e38\u4f7f\u7528\u81ea\u52a8\u751f\u6210\u5de5\u5355"),
                card("\u62a5\u4fee\u95ed\u73af\u7387", rate(closedRepairs, repairTotal), repairTotal, closedRepairs, "\u5de5\u5355\u5b8c\u6210\u540e\u540c\u6b65\u8bbe\u5907\u72b6\u6001"),
                card("\u8bbe\u5907\u5065\u5eb7\u7387", rate(normalDevices, deviceTotal), deviceTotal, normalDevices, "\u6545\u969c\u548c\u7ef4\u62a4\u8bbe\u5907\u5f85\u5904\u7406"),
                card("\u73af\u5883\u6c89\u6dc0\u7387", rate(confirmedEnvironmentRequests, environmentRequests), environmentRequests, confirmedEnvironmentRequests, "\u8bfe\u7a0b\u9700\u6c42\u786e\u8ba4\u540e\u5f62\u6210\u6a21\u677f"),
                card("\u901a\u77e5\u53d1\u5e03\u7387", rate(publishedNotices, notices), notices, publishedNotices, "\u95ed\u73af\u52a8\u4f5c\u53ef\u901a\u8fc7\u516c\u544a\u89e6\u8fbe")
        );

        List<Map<String, Object>> actions = new ArrayList<>();
        addAction(actions, pendingReservations, "\u5f85\u5ba1\u6838\u9884\u7ea6", "\u5904\u7406\u9884\u7ea6\u5ba1\u6838\u540e\u5c06\u81ea\u52a8\u5199\u5165\u4f7f\u7528\u8bb0\u5f55", "/lab-admin/reservations");
        addAction(actions, Math.max(0, abnormalUsage - repairFromUsage), "\u5f02\u5e38\u4f7f\u7528\u672a\u751f\u6210\u5de5\u5355", "\u68c0\u67e5\u4f7f\u7528\u8bb0\u5f55\u5f02\u5e38\u590d\u6838\u7ed3\u679c", "/lab-admin/records");
        addAction(actions, activeRepairs, "\u5904\u7406\u4e2d\u62a5\u4fee\u5de5\u5355", "\u5b8c\u6210\u5de5\u5355\u540e\u8bbe\u5907\u72b6\u6001\u4f1a\u56de\u5230\u5065\u5eb7\u53f0\u8d26", "/lab-admin/repairs");
        addAction(actions, faultDevices + maintenanceDevices, "\u975e\u6b63\u5e38\u8bbe\u5907", "\u7ed3\u5408\u62a5\u4fee\u548c\u7269\u8054\u7f51\u72b6\u6001\u6392\u67e5\u8bbe\u5907", "/lab-admin/devices");
        addAction(actions, lowStockConsumables, "\u4f4e\u5e93\u5b58\u8017\u6750", "\u8865\u8d27\u540e\u66f4\u65b0\u5e93\u5b58\u5e76\u5b8c\u6210\u9884\u8b66\u95ed\u73af", "/lab-admin/consumables");

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

    private Map<String, Object> chain(String key, String title, long total, long completed, long pending, String owner) {
        Map<String, Object> chain = new LinkedHashMap<>();
        chain.put("key", key);
        chain.put("title", title);
        chain.put("owner", owner);
        chain.put("total", total);
        chain.put("completed", Math.min(completed, total));
        chain.put("pending", pending);
        chain.put("rate", rate(completed, total));
        chain.put("tagType", pending > 0 ? "warning" : "success");
        chain.put("status", pending > 0 ? "\u5f85\u5904\u7406" : "\u5df2\u95ed\u73af");
        return chain;
    }

    private Map<String, Object> card(String label, String value, long total, long completed, String trend) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("label", label);
        card.put("value", value);
        card.put("total", total);
        card.put("completed", Math.min(completed, total));
        card.put("trend", trend);
        return card;
    }

    private void addAction(List<Map<String, Object>> actions, long count, String title, String description, String path) {
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
