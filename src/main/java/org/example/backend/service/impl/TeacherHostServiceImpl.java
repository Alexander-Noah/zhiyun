package org.example.backend.service.impl;

import org.example.backend.entity.TeacherHostEntity;
import org.example.backend.mapper.TeacherHostMapper;
import org.example.backend.service.TeacherHostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class TeacherHostServiceImpl implements TeacherHostService {
    private static final int DEFAULT_OFFLINE_SECONDS = 15;
    static final String LAB_ID_REQUIRED = "labId must be positive";

    private final TeacherHostMapper teacherHostMapper;
    private final int offlineSeconds;

    @Autowired
    public TeacherHostServiceImpl(TeacherHostMapper teacherHostMapper) {
        this(teacherHostMapper, DEFAULT_OFFLINE_SECONDS);
    }

    TeacherHostServiceImpl(TeacherHostMapper teacherHostMapper, int offlineSeconds) {
        this.teacherHostMapper = teacherHostMapper;
        this.offlineSeconds = offlineSeconds;
    }

    @Override
    public TeacherHostEntity heartbeat(TeacherHostEntity host) {
        requirePositiveLabId(host == null ? null : host.getLabId());
        if (isBlank(host.getTeacherDeviceId())) {
            throw new IllegalArgumentException("teacherDeviceId is required");
        }
        if (isBlank(host.getHostIp())) {
            throw new IllegalArgumentException("hostIp is required");
        }
        if (host.getPort() == null || host.getPort() <= 0) {
            throw new IllegalArgumentException("port must be positive");
        }

        host.setStatus("online");
        host.setLastHeartbeatTime(LocalDateTime.now());
        teacherHostMapper.upsertHeartbeat(host);
        return host;
    }

    @Override
    public TeacherHostEntity currentHost(Long labId) {
        requirePositiveLabId(labId);
        TeacherHostEntity host = teacherHostMapper.selectCurrentByLabId(labId);
        if (host == null) {
            return null;
        }

        if (!"online".equalsIgnoreCase(host.getStatus()) || isHeartbeatExpired(host.getLastHeartbeatTime())) {
            teacherHostMapper.markOffline(host.getTeacherDeviceId(), host.getLabId());
            host.setStatus("offline");
            return null;
        }

        return host;
    }

    private boolean isHeartbeatExpired(LocalDateTime heartbeatTime) {
        if (heartbeatTime == null) {
            return true;
        }
        return Duration.between(heartbeatTime, LocalDateTime.now()).getSeconds() > offlineSeconds;
    }

    private void requirePositiveLabId(Long labId) {
        if (labId == null || labId <= 0) {
            throw new IllegalArgumentException(LAB_ID_REQUIRED);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
