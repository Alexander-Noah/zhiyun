package org.example.backend.service;

import org.example.backend.entity.StudentClientEntity;

import java.util.List;

public interface StudentClientService {
    StudentClientEntity online(StudentClientEntity client);

    void offline(StudentClientEntity client);

    List<StudentClientEntity> listByLabId(Long labId);
}
