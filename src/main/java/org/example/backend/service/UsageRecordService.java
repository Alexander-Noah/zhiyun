package org.example.backend.service;

import org.example.backend.entity.UsageRecordEntity;

import java.util.List;

public interface UsageRecordService {
    List<UsageRecordEntity> listUsageRecords();

    List<UsageRecordEntity> replaceUsageRecords(List<UsageRecordEntity> records);

    List<UsageRecordEntity> resetUsageRecords();

    UsageRecordEntity updateStatus(Long id, String status);
}
