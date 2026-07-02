package org.example.backend.service.impl;

import org.example.backend.service.DeviceStatusService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeviceStatusServiceImpl implements DeviceStatusService {
    private final JdbcTemplate jdbcTemplate;

    public DeviceStatusServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> listLabRuntimeStatus(Long labId, Map<String, String> query) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(runtimeSelectSql())
                .append(" where lab_id = ?");
        args.add(labId);
        appendRuntimeFilters(sql, args, query);
        sql.append(" order by coalesce(last_report_time, updated_at) desc, id desc");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return Map.of(
                "labId", labId,
                "total", rows.size(),
                "devices", rows
        );
    }

    @Override
    public Map<String, Object> getDeviceRuntimeStatus(Long deviceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                runtimeSelectSql() + " where device_id = ? order by coalesce(last_report_time, updated_at) desc, id desc",
                deviceId
        );
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    @Override
    public Map<String, Object> listEvents(Map<String, String> query) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select
                  id as id,
                  runtime_status_id as runtimeStatusId,
                  terminal_record_id as terminalRecordId,
                  terminal_id as terminalId,
                  device_id as deviceId,
                  lab_id as labId,
                  event_type as eventType,
                  event_level as eventLevel,
                  before_status as beforeStatus,
                  after_status as afterStatus,
                  title as title,
                  content as content,
                  source_type as sourceType,
                  occurred_at as occurredAt,
                  created_at as createdAt
                from device_status_event
                where 1 = 1
                """);
        appendEquals(sql, args, "lab_id", query.get("labId"));
        appendEquals(sql, args, "device_id", query.get("deviceId"));
        appendEquals(sql, args, "terminal_id", query.get("terminalId"));
        appendEquals(sql, args, "event_type", query.get("eventType"));
        appendEquals(sql, args, "event_level", query.get("eventLevel"));
        sql.append(" order by occurred_at desc, id desc limit ?");
        args.add(intValue(query.get("limit"), 100));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return Map.of("total", rows.size(), "records", rows);
    }

    @Override
    public Map<String, Object> reportRuntimeStatus(Map<String, Object> payload) {
        Map<String, Object> request = payload == null ? Map.of() : payload;
        TerminalBinding terminal = findTerminal(request);
        Long labId = terminal.labId() != null ? terminal.labId() : longValue(request.get("labId"));
        if (labId == null) {
            throw new IllegalArgumentException("缺少实验室ID或有效终端凭证");
        }

        String terminalId = firstNonBlank(terminal.terminalId(), stringValue(request.get("terminalId")));
        Long terminalRecordId = terminal.id();
        String onlineStatus = firstNonBlank(stringValue(request.get("onlineStatus")), "online");
        String runtimeStatus = firstNonBlank(stringValue(request.get("runtimeStatus")), "normal");
        String health = firstNonBlank(stringValue(request.get("health")), "unknown");
        LocalDateTime reportTime = LocalDateTime.now();

        ExistingRuntimeStatus existing = findExistingRuntimeStatus(terminalId, longValue(request.get("deviceId")));
        upsertRuntimeStatus(request, terminal, labId, terminalId, terminalRecordId, onlineStatus, runtimeStatus, health, reportTime);
        recordRuntimeEvent(existing, terminalId, terminalRecordId, longValue(request.get("deviceId")), labId, onlineStatus, runtimeStatus);
        updateTerminalSeenAt(terminalRecordId, reportTime);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("labId", labId);
        result.put("terminalId", terminalId);
        result.put("onlineStatus", onlineStatus);
        result.put("runtimeStatus", runtimeStatus);
        result.put("health", health);
        result.put("lastReportTime", reportTime);
        return result;
    }

    @Override
    public Map<String, Object> getSummary(Map<String, String> query) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select
                  count(1) as total,
                  sum(case when online_status = 'online' then 1 else 0 end) as online,
                  sum(case when online_status <> 'online' then 1 else 0 end) as offline,
                  sum(case when runtime_status in ('abnormal', 'warning')
                         or health in ('abnormal', 'warning') then 1 else 0 end) as abnormal,
                  max(last_report_time) as latestReportTime
                from device_runtime_status
                where 1 = 1
                """);
        appendEquals(sql, args, "lab_id", query.get("labId"));
        appendEquals(sql, args, "source_type", query.get("sourceType"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return rows.isEmpty() ? Map.of("total", 0, "online", 0, "offline", 0, "abnormal", 0) : rows.get(0);
    }

    private String runtimeSelectSql() {
        return """
                select
                  id as id,
                  lab_id as labId,
                  lab_code as labCode,
                  lab_name as labName,
                  device_id as deviceId,
                  terminal_record_id as terminalRecordId,
                  terminal_id as terminalId,
                  device_code as deviceCode,
                  host_name as hostName,
                  ip_address as ipAddress,
                  mac_address as macAddress,
                  online_status as onlineStatus,
                  runtime_status as runtimeStatus,
                  health as health,
                  cpu_usage as cpuUsage,
                  memory_usage as memoryUsage,
                  disk_usage as diskUsage,
                  login_user as loginUser,
                  client_version as clientVersion,
                  last_report_time as lastReportTime,
                  source_type as sourceType,
                  metric_snapshot as metricSnapshot,
                  created_at as createdAt,
                  updated_at as updatedAt
                from device_runtime_status
                """;
    }

    private void appendRuntimeFilters(StringBuilder sql, List<Object> args, Map<String, String> query) {
        appendEquals(sql, args, "online_status", query.get("onlineStatus"));
        appendEquals(sql, args, "runtime_status", query.get("runtimeStatus"));
        appendEquals(sql, args, "health", query.get("health"));
        appendEquals(sql, args, "source_type", query.get("sourceType"));
        String keyword = query.get("keyword");
        if (StringUtils.hasText(keyword)) {
            sql.append(" and (host_name like ? or ip_address like ? or mac_address like ? or terminal_id like ?)");
            String pattern = "%" + keyword.trim() + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
    }

    private void appendEquals(StringBuilder sql, List<Object> args, String column, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        sql.append(" and ").append(column).append(" = ?");
        args.add(value.trim());
    }

    private TerminalBinding findTerminal(Map<String, Object> request) {
        String terminalId = stringValue(request.get("terminalId"));
        if (!StringUtils.hasText(terminalId)) {
            return TerminalBinding.empty();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select
                  id,
                  terminal_id as terminalId,
                  lab_id as labId,
                  lab_code as labCode,
                  lab_name as labName
                from lab_terminal
                where terminal_id = ?
                  and status = 'active'
                  and coalesce(deleted, 0) = 0
                limit 1
                """, terminalId);
        if (rows.isEmpty()) {
            return TerminalBinding.empty();
        }
        Map<String, Object> row = rows.get(0);
        return new TerminalBinding(
                longValue(row.get("id")),
                stringValue(row.get("terminalId")),
                longValue(row.get("labId")),
                stringValue(row.get("labCode")),
                stringValue(row.get("labName"))
        );
    }

    private ExistingRuntimeStatus findExistingRuntimeStatus(String terminalId, Long deviceId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("select id, online_status as onlineStatus, runtime_status as runtimeStatus from device_runtime_status where 1 = 1");
        if (StringUtils.hasText(terminalId)) {
            sql.append(" and terminal_id = ?");
            args.add(terminalId);
        } else if (deviceId != null) {
            sql.append(" and device_id = ?");
            args.add(deviceId);
        } else {
            return ExistingRuntimeStatus.empty();
        }
        sql.append(" limit 1");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        if (rows.isEmpty()) {
            return ExistingRuntimeStatus.empty();
        }
        Map<String, Object> row = rows.get(0);
        return new ExistingRuntimeStatus(longValue(row.get("id")), stringValue(row.get("onlineStatus")), stringValue(row.get("runtimeStatus")));
    }

    private void upsertRuntimeStatus(
            Map<String, Object> request,
            TerminalBinding terminal,
            Long labId,
            String terminalId,
            Long terminalRecordId,
            String onlineStatus,
            String runtimeStatus,
            String health,
            LocalDateTime reportTime
    ) {
        jdbcTemplate.update("""
                insert into device_runtime_status (
                  lab_id, lab_code, lab_name, device_id, terminal_record_id, terminal_id, device_code,
                  host_name, ip_address, mac_address, online_status, runtime_status, health,
                  cpu_usage, memory_usage, disk_usage, login_user, client_version,
                  last_report_time, source_type, metric_snapshot
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on duplicate key update
                  lab_id = values(lab_id),
                  lab_code = values(lab_code),
                  lab_name = values(lab_name),
                  device_id = values(device_id),
                  terminal_record_id = values(terminal_record_id),
                  device_code = values(device_code),
                  host_name = values(host_name),
                  ip_address = values(ip_address),
                  mac_address = values(mac_address),
                  online_status = values(online_status),
                  runtime_status = values(runtime_status),
                  health = values(health),
                  cpu_usage = values(cpu_usage),
                  memory_usage = values(memory_usage),
                  disk_usage = values(disk_usage),
                  login_user = values(login_user),
                  client_version = values(client_version),
                  last_report_time = values(last_report_time),
                  source_type = values(source_type),
                  metric_snapshot = values(metric_snapshot),
                  updated_at = current_timestamp
                """,
                labId,
                firstNonBlank(stringValue(request.get("labCode")), terminal.labCode()),
                firstNonBlank(stringValue(request.get("labName")), terminal.labName()),
                longValue(request.get("deviceId")),
                terminalRecordId,
                terminalId,
                stringValue(request.get("deviceCode")),
                stringValue(request.get("hostName")),
                stringValue(request.get("ipAddress")),
                stringValue(request.get("macAddress")),
                onlineStatus,
                runtimeStatus,
                health,
                decimalValue(request.get("cpuUsage")),
                decimalValue(request.get("memoryUsage")),
                decimalValue(request.get("diskUsage")),
                stringValue(request.get("loginUser")),
                stringValue(request.get("clientVersion")),
                reportTime,
                firstNonBlank(stringValue(request.get("sourceType")), "terminal"),
                stringValue(request.get("metricSnapshot"))
        );
    }

    private void recordRuntimeEvent(ExistingRuntimeStatus existing, String terminalId, Long terminalRecordId, Long deviceId, Long labId, String onlineStatus, String runtimeStatus) {
        String before = firstNonBlank(existing.runtimeStatus(), existing.onlineStatus(), "new");
        String after = runtimeStatus + "/" + onlineStatus;
        String eventType = existing.id() == null ? "online" : (onlineStatus.equals(existing.onlineStatus()) && runtimeStatus.equals(existing.runtimeStatus()) ? "heartbeat" : "manual");
        jdbcTemplate.update("""
                insert into device_status_event (
                  runtime_status_id, terminal_record_id, terminal_id, device_id, lab_id,
                  event_type, event_level, before_status, after_status, title, content, source_type, occurred_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                existing.id(),
                terminalRecordId,
                terminalId,
                deviceId,
                labId,
                eventType,
                "abnormal".equals(runtimeStatus) ? "danger" : "info",
                before,
                after,
                "设备运行状态上报",
                "终端上报运行状态",
                "terminal",
                LocalDateTime.now()
        );
    }

    private void updateTerminalSeenAt(Long terminalRecordId, LocalDateTime reportTime) {
        if (terminalRecordId == null) {
            return;
        }
        jdbcTemplate.update("update lab_terminal set last_seen_at = ?, updated_at = current_timestamp where id = ?", reportTime, terminalRecordId);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            String text = stringValue(value);
            return text.isBlank() ? null : Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            String text = stringValue(value);
            return text.isBlank() ? fallback : Integer.parseInt(text);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            String text = stringValue(value);
            return text.isBlank() ? null : new BigDecimal(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record TerminalBinding(Long id, String terminalId, Long labId, String labCode, String labName) {
        static TerminalBinding empty() {
            return new TerminalBinding(null, "", null, "", "");
        }
    }

    private record ExistingRuntimeStatus(Long id, String onlineStatus, String runtimeStatus) {
        static ExistingRuntimeStatus empty() {
            return new ExistingRuntimeStatus(null, "", "");
        }
    }
}
