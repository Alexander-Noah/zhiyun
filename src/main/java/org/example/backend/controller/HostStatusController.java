package org.example.backend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.result.Result;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@CrossOrigin
@RestController
@Slf4j
public class HostStatusController {
    private static final String LATEST_STATUS_KEY = "smart-lab:host-status:latest";
    private static final Duration STATUS_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HostStatusController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/host-status/report")
    public Result report(@RequestBody Map<String, Object> payload) {
        Map<String, Object> status = new LinkedHashMap<>(payload == null ? Map.of() : payload);
        status.put("receivedAt", OffsetDateTime.now().toString());

        try {
            redisTemplate.opsForValue().set(LATEST_STATUS_KEY, objectMapper.writeValueAsString(status), STATUS_TTL);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "主机状态数据格式不正确", exception);
        }

        Object system = status.get("system");
        Object health = status.get("health");
        log.info("接收主机探针上报并写入 Redis，key={}, ttl={}s, system={}, health={}",
                LATEST_STATUS_KEY, STATUS_TTL.toSeconds(), system, health);
        return Result.success("主机状态上报成功", Map.of("receivedAt", status.get("receivedAt")));
    }

    @GetMapping("/host-status/latest")
    public Result latest() {
        String rawStatus = redisTemplate.opsForValue().get(LATEST_STATUS_KEY);
        if (rawStatus == null || rawStatus.isBlank()) {
            return Result.success("暂无主机状态上报", Map.of());
        }

        try {
            Map<String, Object> status = objectMapper.readValue(rawStatus, new TypeReference<>() {});
            return Result.success("查询最近主机状态成功", status);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Redis 中的主机状态数据无法解析", exception);
        }
    }
}
