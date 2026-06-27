package org.example.backend.service.impl;

import org.example.backend.entity.UsageRecordEntity;
import org.example.backend.mapper.UsageRecordMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.UsageRecordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class UsageRecordServiceImpl implements UsageRecordService {
    private final UsageRecordMapper usageRecordMapper;
    private final BusinessLoopService businessLoopService;

    public UsageRecordServiceImpl(UsageRecordMapper usageRecordMapper, BusinessLoopService businessLoopService) {
        this.usageRecordMapper = usageRecordMapper;
        this.businessLoopService = businessLoopService;
    }

    @Override
    public List<UsageRecordEntity> listUsageRecords() {
        List<UsageRecordEntity> records = usageRecordMapper.listUsageRecords();
        return records == null ? List.of() : records;
    }

    @Override
    @Transactional
    public List<UsageRecordEntity> replaceUsageRecords(List<UsageRecordEntity> records) {
        if (records == null || records.isEmpty()) {
            return listUsageRecords();
        }
        usageRecordMapper.deleteAllUsageRecords();
        for (UsageRecordEntity record : records) {
            normalizeRecord(record);
            usageRecordMapper.insertUsageRecord(record);
            if (record.getStatus() != null && record.getStatus().contains("异常")) {
                businessLoopService.createRepairAfterUsageAbnormal(record);
            }
        }
        businessLoopService.recordEvent("usage-record", "batch-save", "使用记录台账", "已同步", Map.of("count", records.size()));
        return listUsageRecords();
    }

    @Override
    public List<UsageRecordEntity> resetUsageRecords() {
        List<UsageRecordEntity> records = listUsageRecords();
        businessLoopService.recordEvent("usage-record", "reset", "使用记录台账", "已恢复当前台账", Map.of("count", records.size()));
        return records;
    }

    @Override
    @Transactional
    public UsageRecordEntity updateStatus(Long id, String status) {
        int updatedCount = usageRecordMapper.updateUsageRecordStatus(id, status);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("未找到使用记录");
        }
        UsageRecordEntity usageRecord = usageRecordMapper.getUsageRecord(id);
        if (status != null && status.contains("异常")) {
            businessLoopService.createRepairAfterUsageAbnormal(usageRecord);
        } else {
            businessLoopService.recordEvent("usage-record", "status", usageRecord.getResource(), status, Map.of(
                    "usageRecordId", id,
                    "person", usageRecord.getPerson()
            ));
        }
        return usageRecord;
    }

    private void normalizeRecord(UsageRecordEntity record) {
        if (record.getUseTime() == null) {
            record.setUseTime(LocalDateTime.now());
        }
        if (record.getStatus() == null || record.getStatus().isBlank()) {
            record.setStatus("正常");
        }
    }
}
