package org.example.backend.service;

import org.example.backend.result.Result;
import org.example.backend.entity.softwareEntity;

public interface softwareService {
    Result getLabSoftware();

    Result InserterLabSoftware(softwareEntity software);

    Result updateLabSoftware(Long id, softwareEntity software);

    Result deleteLabSoftware(Long id);
}
