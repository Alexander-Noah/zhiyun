package org.example.backend.controller;

import org.example.backend.entity.TeacherHostEntity;
import org.example.backend.result.Result;
import org.example.backend.service.TeacherHostService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@CrossOrigin
@RestController
public class TeacherHostController {
    private final TeacherHostService teacherHostService;

    public TeacherHostController(TeacherHostService teacherHostService) {
        this.teacherHostService = teacherHostService;
    }

    @PostMapping({"/teacher-hosts/heartbeat", "/api/teacher-hosts/heartbeat"})
    public Result heartbeat(@RequestBody TeacherHostEntity request) {
        return Result.success("teacher host heartbeat registered", teacherHostService.heartbeat(request));
    }

    @GetMapping({"/teacher-hosts/current", "/api/teacher-hosts/current"})
    public Result current(@RequestParam Long labId) {
        TeacherHostEntity host = teacherHostService.currentHost(labId);
        return Result.success(
                host == null ? "current lab teacher host is offline" : "current teacher host found",
                host == null ? null : toPublicHost(host)
        );
    }

    private Map<String, Object> toPublicHost(TeacherHostEntity host) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", host.getId());
        payload.put("labId", host.getLabId());
        payload.put("teacherDeviceId", host.getTeacherDeviceId());
        payload.put("hostIp", host.getHostIp());
        payload.put("port", host.getPort());
        payload.put("status", host.getStatus());
        payload.put("lastHeartbeatTime", host.getLastHeartbeatTime());
        return payload;
    }
}
