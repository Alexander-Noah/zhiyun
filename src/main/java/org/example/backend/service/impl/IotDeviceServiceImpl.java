package org.example.backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backend.service.IotDeviceService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class IotDeviceServiceImpl implements IotDeviceService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public IotDeviceServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> listDevices(Map<String, String> query) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(iotDeviceSelectSql()).append(" where coalesce(deleted, 0) = 0");
        appendDeviceFilters(sql, args, query);
        sql.append(" order by lab_id asc, device_type asc, iot_name asc, id desc");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return Map.of("total", rows.size(), "devices", rows);
    }

    @Override
    public Map<String, Object> listLabDevices(Long labId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                iotDeviceSelectSql() + " where coalesce(deleted, 0) = 0 and lab_id = ? order by device_type asc, sort_name asc, id desc",
                labId
        );
        long configuredCount = rows.stream().filter(row -> booleanValue(row.get("configured"))).count();
        return Map.of(
                "labId", labId,
                "total", rows.size(),
                "configuredCount", configuredCount,
                "devices", rows
        );
    }

    @Override
    public Map<String, Object> getLabOverview(Long labId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select
                  device_type as deviceType,
                  count(1) as total,
                  sum(case when configured = 1 then 1 else 0 end) as configured,
                  sum(case when status = 'online' then 1 else 0 end) as online,
                  sum(case when status = 'abnormal' then 1 else 0 end) as abnormal
                from iot_device
                where coalesce(deleted, 0) = 0
                  and lab_id = ?
                group by device_type
                order by device_type
                """, labId);
        return Map.of("labId", labId, "types", rows);
    }

    @Override
    public Map<String, Object> createDevice(Map<String, Object> payload) {
        Map<String, Object> request = payload == null ? Map.of() : payload;
        String code = firstNonBlank(stringValue(request.get("iotCode")), stringValue(request.get("code")), "IOT-" + UUID.randomUUID());
        String name = firstNonBlank(stringValue(request.get("iotName")), stringValue(request.get("name")), code);
        String type = normalizeDeviceType(stringValue(request.get("deviceType")));
        Long labId = longValue(request.get("labId"));
        if (labId == null) {
            throw new IllegalArgumentException("缺少实验室ID");
        }
        jdbcTemplate.update("""
                insert into iot_device (
                  iot_code, iot_name, device_type, lab_id, lab_code, lab_name, asset_device_id,
                  protocol, base_url, endpoint, auth_type, configured, enabled, status,
                  snapshot_url, stream_url, source_type, remark
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                code,
                name,
                type,
                labId,
                stringValue(request.get("labCode")),
                stringValue(request.get("labName")),
                longValue(request.get("assetDeviceId")),
                firstNonBlank(stringValue(request.get("protocol")), "mock"),
                stringValue(request.get("baseUrl")),
                stringValue(request.get("endpoint")),
                stringValue(request.get("authType")),
                booleanValue(request.get("configured")),
                !request.containsKey("enabled") || booleanValue(request.get("enabled")),
                firstNonBlank(stringValue(request.get("status")), "unconfigured"),
                stringValue(request.get("snapshotUrl")),
                stringValue(request.get("streamUrl")),
                firstNonBlank(stringValue(request.get("sourceType")), "manual"),
                stringValue(request.get("remark"))
        );
        return findDeviceByCode(code);
    }

    @Override
    public Map<String, Object> updateDevice(Long id, Map<String, Object> payload) {
        Map<String, Object> request = payload == null ? Map.of() : payload;
        jdbcTemplate.update("""
                update iot_device
                set
                  iot_name = ?,
                  device_type = ?,
                  lab_id = ?,
                  lab_code = ?,
                  lab_name = ?,
                  asset_device_id = ?,
                  protocol = ?,
                  base_url = ?,
                  endpoint = ?,
                  auth_type = ?,
                  configured = ?,
                  enabled = ?,
                  status = ?,
                  snapshot_url = ?,
                  stream_url = ?,
                  source_type = ?,
                  remark = ?,
                  updated_at = current_timestamp
                where id = ?
                  and coalesce(deleted, 0) = 0
                """,
                firstNonBlank(stringValue(request.get("iotName")), stringValue(request.get("name"))),
                normalizeDeviceType(stringValue(request.get("deviceType"))),
                longValue(request.get("labId")),
                stringValue(request.get("labCode")),
                stringValue(request.get("labName")),
                longValue(request.get("assetDeviceId")),
                firstNonBlank(stringValue(request.get("protocol")), "mock"),
                stringValue(request.get("baseUrl")),
                stringValue(request.get("endpoint")),
                stringValue(request.get("authType")),
                booleanValue(request.get("configured")),
                !request.containsKey("enabled") || booleanValue(request.get("enabled")),
                firstNonBlank(stringValue(request.get("status")), "unconfigured"),
                stringValue(request.get("snapshotUrl")),
                stringValue(request.get("streamUrl")),
                firstNonBlank(stringValue(request.get("sourceType")), "manual"),
                stringValue(request.get("remark")),
                id
        );
        return findDeviceById(id);
    }

    @Override
    public void deleteDevice(Long id) {
        jdbcTemplate.update("update iot_device set deleted = 1, updated_at = current_timestamp where id = ?", id);
    }

    @Override
    public Map<String, Object> control(Map<String, Object> payload) {
        Map<String, Object> request = payload == null ? Map.of() : payload;
        Map<String, Object> device = findControlTarget(request);
        Long labId = firstLong(longValue(request.get("labId")), longValue(device.get("labId")));
        if (labId == null) {
            throw new IllegalArgumentException("缺少实验室ID");
        }

        String protocol = stringValue(device.get("protocol"));
        boolean configured = booleanValue(device.get("configured"));
        String resultStatus = (!configured || "mock".equalsIgnoreCase(protocol)) ? "mock_success" : "pending";
        String commandNo = "IOT-CMD-" + UUID.randomUUID();
        jdbcTemplate.update("""
                insert into iot_command_log (
                  command_no, iot_device_id, iot_code, lab_id, command_key, action,
                  payload_json, result_status, response_summary, operator_id, operator_name,
                  source_type, executed_at, finished_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                commandNo,
                longValue(device.get("id")),
                stringValue(device.get("iotCode")),
                labId,
                firstNonBlank(stringValue(request.get("commandKey")), stringValue(request.get("action"))),
                firstNonBlank(stringValue(request.get("action")), stringValue(request.get("commandKey"))),
                toJson(request.get("payload")),
                resultStatus,
                "mock_success".equals(resultStatus) ? "当前为模拟控制，后续可接入真实硬件网关" : "控制命令已记录，等待硬件网关回执",
                longValue(request.get("operatorId")),
                stringValue(request.get("operatorName")),
                firstNonBlank(stringValue(request.get("sourceType")), configured ? "gateway" : "mock"),
                LocalDateTime.now(),
                "mock_success".equals(resultStatus) ? LocalDateTime.now() : null
        );
        return findCommandLogByNo(commandNo);
    }

    @Override
    public Map<String, Object> listCommandLogs(Map<String, String> query) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(commandLogSelectSql()).append(" where 1 = 1");
        appendEquals(sql, args, "lab_id", query.get("labId"));
        appendEquals(sql, args, "iot_device_id", query.get("iotDeviceId"));
        appendEquals(sql, args, "result_status", query.get("resultStatus"));
        sql.append(" order by executed_at desc, id desc limit ?");
        args.add(intValue(query.get("limit"), 100));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return Map.of("total", rows.size(), "records", rows);
    }

    @Override
    public Map<String, Object> getEnvironment(Long labId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select
                  t.id as id,
                  t.iot_device_id as iotDeviceId,
                  d.iot_code as iotCode,
                  d.iot_name as iotName,
                  t.lab_id as labId,
                  t.metric_key as metricKey,
                  t.metric_value as metricValue,
                  t.metric_text as metricText,
                  t.unit as unit,
                  t.status as status,
                  t.source_type as sourceType,
                  t.reported_at as reportedAt,
                  t.updated_at as updatedAt
                from iot_telemetry_latest t
                left join iot_device d on d.id = t.iot_device_id
                where t.lab_id = ?
                order by t.metric_key asc, t.reported_at desc
                """, labId);
        return Map.of("labId", labId, "total", rows.size(), "metrics", rows);
    }

    private String iotDeviceSelectSql() {
        return """
                select
                  id as id,
                  iot_code as iotCode,
                  iot_name as iotName,
                  device_type as deviceType,
                  lab_id as labId,
                  lab_code as labCode,
                  lab_name as labName,
                  asset_device_id as assetDeviceId,
                  protocol as protocol,
                  base_url as baseUrl,
                  endpoint as endpoint,
                  auth_type as authType,
                  configured as configured,
                  enabled as enabled,
                  status as status,
                  snapshot_url as snapshotUrl,
                  stream_url as streamUrl,
                  source_type as sourceType,
                  remark as remark,
                  created_at as createdAt,
                  updated_at as updatedAt
                from iot_device
                """;
    }

    private String commandLogSelectSql() {
        return """
                select
                  id as id,
                  command_no as commandNo,
                  iot_device_id as iotDeviceId,
                  iot_code as iotCode,
                  lab_id as labId,
                  command_key as commandKey,
                  action as action,
                  payload_json as payloadJson,
                  result_status as resultStatus,
                  response_summary as responseSummary,
                  operator_id as operatorId,
                  operator_name as operatorName,
                  source_type as sourceType,
                  executed_at as executedAt,
                  finished_at as finishedAt,
                  created_at as createdAt
                from iot_command_log
                """;
    }

    private void appendDeviceFilters(StringBuilder sql, List<Object> args, Map<String, String> query) {
        appendEquals(sql, args, "lab_id", query.get("labId"));
        appendEquals(sql, args, "device_type", query.get("deviceType"));
        appendEquals(sql, args, "status", query.get("status"));
        String keyword = query.get("keyword");
        if (StringUtils.hasText(keyword)) {
            sql.append(" and (iot_code like ? or iot_name like ? or lab_name like ?)");
            String pattern = "%" + keyword.trim() + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
    }

    private Map<String, Object> findControlTarget(Map<String, Object> request) {
        Long id = firstLong(longValue(request.get("iotDeviceId")), longValue(request.get("id")));
        if (id != null) {
            return findDeviceById(id);
        }
        String code = firstNonBlank(stringValue(request.get("iotCode")), stringValue(request.get("code")));
        if (StringUtils.hasText(code)) {
            return findDeviceByCode(code);
        }
        throw new IllegalArgumentException("缺少物联设备ID或编码");
    }

    private Map<String, Object> findDeviceById(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                iotDeviceSelectSql() + " where id = ? and coalesce(deleted, 0) = 0 limit 1",
                id
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("物联设备不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> findDeviceByCode(String code) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                iotDeviceSelectSql() + " where iot_code = ? and coalesce(deleted, 0) = 0 limit 1",
                code
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("物联设备不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> findCommandLogByNo(String commandNo) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(commandLogSelectSql() + " where command_no = ? limit 1", commandNo);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private void appendEquals(StringBuilder sql, List<Object> args, String column, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        sql.append(" and ").append(column).append(" = ?");
        args.add(value.trim());
    }

    private String normalizeDeviceType(String value) {
        String type = stringValue(value).replace("-", "_").toLowerCase(Locale.ROOT);
        return StringUtils.hasText(type) ? type : "access_control";
    }

    private Long firstLong(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = stringValue(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
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

    private String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ignored) {
            return "{}";
        }
    }
}
