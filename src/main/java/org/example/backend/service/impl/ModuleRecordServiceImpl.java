package org.example.backend.service.impl;

import org.example.backend.mapper.ModuleRecordMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.ModuleRecordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ModuleRecordServiceImpl implements ModuleRecordService {
    private static final TypeReference<Map<String, Object>> RECORD_TYPE = new TypeReference<>() {
    };

    private final ModuleRecordMapper moduleRecordMapper;
    private final ObjectMapper objectMapper;
    private final BusinessLoopService businessLoopService;

    public ModuleRecordServiceImpl(
            ModuleRecordMapper moduleRecordMapper,
            ObjectMapper objectMapper,
            BusinessLoopService businessLoopService
    ) {
        this.moduleRecordMapper = moduleRecordMapper;
        this.objectMapper = objectMapper;
        this.businessLoopService = businessLoopService;
    }

    @Override
    public List<Map<String, Object>> listModuleRecords(String moduleName) {
        List<Map<String, Object>> records = new ArrayList<>();

        for (String recordJson : moduleRecordMapper.listRecordJson(moduleName)) {
            records.add(readRecord(recordJson));
        }

        return records;
    }

    @Override
    @Transactional
    public List<Map<String, Object>> replaceModuleRecords(String moduleName, List<Map<String, Object>> records) {
        List<Map<String, Object>> nextRecords = records == null ? Collections.emptyList() : records;

        moduleRecordMapper.deleteByModuleName(moduleName);

        for (Map<String, Object> record : nextRecords) {
            Map<String, Object> normalizedRecord = new LinkedHashMap<>(record);
            moduleRecordMapper.insertRecord(moduleName, getRecordKey(normalizedRecord), writeRecord(normalizedRecord));
        }

        if (!"operation-events".equals(moduleName)) {
            businessLoopService.recordEvent("module-record", "batch-save", moduleName, "已保存", Map.of("count", nextRecords.size()));
        }
        return nextRecords;
    }

    @Override
    @Transactional
    public List<Map<String, Object>> resetModuleRecords(String moduleName) {
        moduleRecordMapper.deleteByModuleName(moduleName);
        if (!"operation-events".equals(moduleName)) {
            businessLoopService.recordEvent("module-record", "reset", moduleName, "已清空", Map.of("moduleName", moduleName));
        }
        return Collections.emptyList();
    }

    private String getRecordKey(Map<String, Object> record) {
        Object key = record.get("id");
        if (key == null) key = record.get("code");
        if (key == null) key = record.get("name");
        if (key == null) key = record.get("course");
        return key == null ? null : String.valueOf(key);
    }

    private Map<String, Object> readRecord(String recordJson) {
        try {
            return objectMapper.readValue(recordJson, RECORD_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("模块数据解析失败", exception);
        }
    }

    private String writeRecord(Map<String, Object> record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (Exception exception) {
            throw new IllegalStateException("模块数据序列化失败", exception);
        }
    }
}
