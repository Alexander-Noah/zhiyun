package org.example.backend.service.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IotGatewayCommandService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;
    private final GatewayAdapterRegistry gatewayAdapterRegistry;

    public IotGatewayCommandService(JdbcTemplate jdbcTemplate, GatewayAdapterRegistry gatewayAdapterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.gatewayAdapterRegistry = gatewayAdapterRegistry;
    }

    public Map<String, Object> execute(Map<String, Object> device, Map<String, Object> request) {
        Long labId = firstLong(longValue(request.get("labId")), longValue(device.get("labId")));
        if (labId == null) {
            throw new IllegalArgumentException("缺少实验室ID");
        }

        String action = firstNonBlank(stringValue(request.get("action")), stringValue(request.get("commandKey")));
        if (!StringUtils.hasText(action)) {
            throw new IllegalArgumentException("缺少控制命令");
        }

        Map<String, Object> point = findPoint(longValue(device.get("id")), action);
        boolean configured = booleanValue(device.get("configured")) && !point.isEmpty();
        String protocol = firstNonBlank(stringValue(point.get("protocolType")), stringValue(device.get("protocol")), "mock");
        GatewayCommandRequest gatewayRequest = new GatewayCommandRequest(
                longValue(point.get("gatewayId")),
                stringValue(point.get("gatewayCode")),
                protocol,
                stringValue(point.get("gatewayAddress")),
                stringValue(point.get("authType")),
                longValue(point.get("pointId")),
                stringValue(point.get("pointCode")),
                stringValue(point.get("requestPath")),
                stringValue(point.get("requestTemplate")),
                longValue(device.get("id")),
                stringValue(device.get("iotCode")),
                labId,
                stringValue(device.get("deviceType")),
                firstNonBlank(stringValue(request.get("commandKey")), action),
                action,
                payload(request)
        );

        GatewayAdapter adapter = gatewayAdapterRegistry.resolve(protocol, configured);
        GatewayCommandResult result = adapter.sendCommand(gatewayRequest);
        String commandNo = "IOT-CMD-" + UUID.randomUUID();
        jdbcTemplate.update("""
                insert into iot_command_log (
                  command_no, iot_device_id, iot_code, lab_id, gateway_id, point_id,
                  command_key, action, payload_json, request_params_json, response_result_json,
                  result_status, execution_status, error_message, response_summary,
                  operator_id, operator_name, source_type, gateway_mode, executed_at, finished_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                commandNo,
                gatewayRequest.iotDeviceId(),
                gatewayRequest.iotCode(),
                labId,
                gatewayRequest.gatewayId(),
                gatewayRequest.pointId(),
                gatewayRequest.commandType(),
                gatewayRequest.action(),
                toJson(gatewayRequest.payload()),
                toJson(result.requestParams()),
                toJson(result.responseResult()),
                result.resultStatus(),
                result.executionStatus(),
                result.errorMessage(),
                result.responseSummary(),
                longValue(request.get("operatorId")),
                stringValue(request.get("operatorName")),
                "mock_gateway".equals(result.gatewayMode()) ? "mock" : "gateway",
                result.gatewayMode(),
                result.executedAt(),
                result.finishedAt()
        );
        return findCommandLogByNo(commandNo);
    }

    private Map<String, Object> findPoint(Long iotDeviceId, String action) {
        if (iotDeviceId == null) {
            return Map.of();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select
                  p.id as pointId,
                  p.gateway_id as gatewayId,
                  p.command_type as commandType,
                  p.point_code as pointCode,
                  p.request_path as requestPath,
                  p.request_template as requestTemplate,
                  g.gateway_code as gatewayCode,
                  g.protocol_type as protocolType,
                  g.gateway_address as gatewayAddress,
                  g.auth_type as authType,
                  g.status as gatewayStatus
                from iot_gateway_point p
                left join iot_gateway_config g on g.id = p.gateway_id and coalesce(g.deleted, 0) = 0
                where p.iot_device_id = ?
                  and p.enabled = 1
                  and coalesce(p.deleted, 0) = 0
                  and (p.command_type = ? or p.command_type = ?)
                order by p.id desc
                limit 1
                """, iotDeviceId, action, action.replace("-", "_"));
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Map<String, Object> findCommandLogByNo(String commandNo) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select
                  id as id,
                  command_no as commandNo,
                  iot_device_id as iotDeviceId,
                  iot_code as iotCode,
                  lab_id as labId,
                  gateway_id as gatewayId,
                  point_id as pointId,
                  command_key as commandKey,
                  action as action,
                  payload_json as payloadJson,
                  request_params_json as requestParamsJson,
                  response_result_json as responseResultJson,
                  result_status as resultStatus,
                  execution_status as executionStatus,
                  error_message as errorMessage,
                  response_summary as responseSummary,
                  operator_id as operatorId,
                  operator_name as operatorName,
                  source_type as sourceType,
                  gateway_mode as gatewayMode,
                  executed_at as executedAt,
                  finished_at as finishedAt,
                  created_at as createdAt
                from iot_command_log
                where command_no = ?
                limit 1
                """, commandNo);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Map<String, Object> payload(Map<String, Object> request) {
        Object payload = request.get("payload");
        if (payload instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .filter(entry -> entry.getKey() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            entry -> String.valueOf(entry.getKey()),
                            Map.Entry::getValue,
                            (left, right) -> right
                    ));
        }
        return Map.of();
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
