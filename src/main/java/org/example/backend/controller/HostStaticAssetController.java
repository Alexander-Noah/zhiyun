package org.example.backend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.result.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@CrossOrigin
@RestController
@Slf4j
public class HostStaticAssetController {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HostStaticAssetController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/host-assets/report")
    public Result report(@RequestBody Map<String, Object> payload) throws JsonProcessingException {
        Map<String, Object> system = asMap(payload.get("system"));
        String hostname = stringValue(system.get("hostname"));
        if (hostname.isBlank()) {
            hostname = stringValue(payload.get("hostname"));
        }
        if (hostname.isBlank()) {
            return Result.error(400, "主机名不能为空");
        }

        Object environment = payload.getOrDefault("environment", Map.of());
        Object runningApps = payload.getOrDefault("running_apps", List.of());
        Object installedSoftware = payload.getOrDefault("installed_software", List.of());

        int runningAppCount = collectionSize(runningApps);
        int installedSoftwareCount = collectionSize(installedSoftware);

        jdbcTemplate.update("""
                insert into host_static_asset (
                  hostname, platform, system_name, release_version, architecture, python_version,
                  environment_json, running_apps_json, installed_software_json,
                  running_app_count, installed_software_count, reported_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on duplicate key update
                  platform = values(platform),
                  system_name = values(system_name),
                  release_version = values(release_version),
                  architecture = values(architecture),
                  python_version = values(python_version),
                  environment_json = values(environment_json),
                  running_apps_json = values(running_apps_json),
                  installed_software_json = values(installed_software_json),
                  running_app_count = values(running_app_count),
                  installed_software_count = values(installed_software_count),
                  reported_at = values(reported_at),
                  received_at = current_timestamp,
                  updated_at = current_timestamp
                """,
                hostname,
                stringValue(system.get("platform")),
                stringValue(system.get("system")),
                stringValue(system.get("release")),
                stringValue(system.get("architecture")),
                stringValue(system.get("python")),
                objectMapper.writeValueAsString(environment),
                objectMapper.writeValueAsString(runningApps),
                objectMapper.writeValueAsString(installedSoftware),
                runningAppCount,
                installedSoftwareCount,
                stringValue(payload.get("timestamp"))
        );

        log.info("接收主机静态资产上报，hostname={}, runningApps={}, installedSoftware={}",
                hostname, runningAppCount, installedSoftwareCount);
        return Result.success("主机静态资产上报成功", Map.of(
                "hostname", hostname,
                "runningAppCount", runningAppCount,
                "installedSoftwareCount", installedSoftwareCount
        ));
    }

    @GetMapping("/host-assets")
    public Result list() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select id, hostname, platform, system_name, release_version, architecture, python_version,
                       running_app_count, installed_software_count, reported_at, received_at, updated_at
                from host_static_asset
                order by updated_at desc
                """);
        return Result.success("查询主机静态资产列表成功", rows);
    }

    @GetMapping("/host-assets/{hostname}")
    public Result detail(@PathVariable String hostname) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select *
                from host_static_asset
                where hostname = ?
                limit 1
                """, hostname);
        return Result.success("查询主机静态资产详情成功", rows.isEmpty() ? Map.of() : rows.get(0));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private int collectionSize(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        return 0;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
