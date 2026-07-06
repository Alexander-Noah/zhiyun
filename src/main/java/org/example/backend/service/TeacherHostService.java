package org.example.backend.service;

import org.example.backend.entity.TeacherHostEntity;

public interface TeacherHostService {
    TeacherHostEntity heartbeat(TeacherHostEntity host);

    TeacherHostEntity currentHost(Long labId);
}
